package se.onemanstudio.services

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.config.models.*
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.EmailReports
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Email reports sent for real to a small SMTP server on localhost: what the message contains,
 * how it logs in, what happens when the server is silent, and which reports the scheduler sends.
 */
class EmailReportSendTest {

    private data class Mail(val from: String, val to: List<String>, val login: String?, val data: String) {
        val message: MimeMessage by lazy { MimeMessage(Session.getInstance(Properties()), data.byteInputStream()) }
    }

    /** Just enough SMTP to receive a message. Refuses recipients starting with "bounce"; [silent] never greets. */
    private class FakeSmtp(private val silent: Boolean = false) {
        val mails = CopyOnWriteArrayList<Mail>()

        /** The last AUTH PLAIN credentials any client sent, delivered or not. */
        @Volatile
        var lastLogin: String? = null
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val open = CopyOnWriteArrayList<Socket>()
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    open += socket
                    if (!silent) thread(isDaemon = true) { runCatching { talk(socket) } }
                }
            }
        }

        private fun talk(socket: Socket) = socket.use {
            val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val output = it.getOutputStream()
            fun say(line: String) { output.write("$line\r\n".toByteArray()); output.flush() }
            val conversation = Conversation(input)
            say("220 localhost ESMTP")
            generateSequence { input.readLine() }.forEach { line -> conversation.answer(line).forEach(::say) }
        }

        /** One client connection: remembers the envelope and answers each command. */
        private inner class Conversation(private val input: java.io.BufferedReader) {
            private var from = ""
            private var login: String? = null
            private val to = mutableListOf<String>()

            fun answer(line: String): List<String> {
                val command = line.uppercase()
                val address = line.substringAfter('<').substringBefore('>')
                return when {
                    command.startsWith("EHLO") -> listOf("250-localhost", "250 AUTH PLAIN")
                    command.startsWith("AUTH PLAIN") -> {
                        login = String(Base64.getDecoder().decode(line.substringAfterLast(' '))).replace('\u0000', ':')
                        lastLogin = login
                        listOf("235 ok")
                    }
                    command.startsWith("MAIL FROM:") -> listOf("250 ok").also { from = address }
                    command.startsWith("RCPT TO:") && address.startsWith("bounce") -> listOf("550 no such user")
                    command.startsWith("RCPT TO:") -> listOf("250 ok").also { to += address }
                    command == "DATA" -> listOf("354 go ahead")
                    command == "QUIT" -> listOf("221 bye")
                    to.isNotEmpty() && from.isNotEmpty() && !command.startsWith("RSET") -> {
                        // First line of the message: read the rest up to the lone dot
                        val rest = generateSequence { input.readLine() }.takeWhile { l -> l != "." }
                        val data = (sequenceOf(line) + rest).joinToString("\r\n") { l -> l.removePrefix(".") }
                        mails += Mail(from, to.toList(), login, data)
                        from = ""
                        listOf("250 queued")
                    }
                    else -> listOf("250 ok")
                }
            }
        }

        fun close() {
            server.close()
            open.forEach { runCatching { it.close() } }
        }
    }

    private val logger = LoggerFactory.getLogger("EmailReportSendTest")
    private val smtp = FakeSmtp()
    private lateinit var project: UUID

    private fun useSmtp(port: Int, username: String? = null, password: String? = null, startTls: Boolean = false) =
        EmailService.init(
            EmailConfig(
                smtpHost = "127.0.0.1", smtpPort = port, smtpFrom = "reports@mini.example",
                smtpUsername = username, smtpPassword = password, smtpStartTls = startTls
            )
        )

    @Before
    fun start() {
        ServiceManager.reload(
            AppConfig(
                security = SecurityConfig(
                    adminUsername = "test",
                    adminPassword = "testpassword123",
                    serverSalt = "test-salt-" + "x".repeat(50),
                    allowedOrigins = emptyList()
                ),
                database = DatabaseConfig(
                    type = DatabaseType.SQLITE,
                    path = "./test-dbs/email-test-${System.nanoTime()}.db"
                ),
                server = ServerConfig(port = 8080, isDevelopment = true),
                geoip = GeoIPConfig(databasePath = "src/main/resources/geo/geolite2-city.mmdb"),
                rateLimit = RateLimitConfig(perIpRequestsPerMinute = 1000, perApiKeyRequestsPerMinute = 10000)
            ),
            logger
        )
        project = UUID.randomUUID()
        transaction {
            Projects.insert {
                it[id] = project
                it[name] = "Café & Co"
                it[domain] = "cafe.example"
                it[apiKey] = UUID.randomUUID().toString().replace("-", "")
            }
            for ((session, page) in listOf("a" to "/", "a" to "/menu", "b" to "/")) {
                Events.insert {
                    it[projectId] = project
                    it[visitorHash] = "visitor-$session"
                    it[sessionId] = session
                    it[path] = page
                    it[eventType] = "pageview"
                    it[timestamp] = LocalDateTime.now().minusHours(1)
                }
            }
        }
        useSmtp(smtp.port)
    }

    @After
    fun stop() {
        EmailService.smtpTimeoutMillis = 15_000
        EmailService.init(null)
        smtp.close()
        ServiceManager.shutdown(logger)
    }

    @Suppress("LongParameterList")
    private fun report(
        recipient: String,
        schedule: String = "DAILY",
        hour: Int = 8,
        day: Int = 1,
        active: Boolean = true,
        sections: String = "overview,pages,referrers,geo,events",
        subject: String = "{project_name} Analytics — {period}",
        header: String? = null
    ): UUID {
        val reportId = UUID.randomUUID()
        transaction {
            EmailReports.insert {
                it[id] = reportId
                it[projectId] = project
                it[recipientEmail] = recipient
                it[EmailReports.schedule] = schedule
                it[sendHour] = hour
                it[sendDay] = day
                it[isActive] = active
                it[includeSections] = sections
                it[subjectTemplate] = subject
                it[headerText] = header
            }
        }
        return reportId
    }

    private fun lastSentAt(reportId: UUID) = transaction {
        EmailReports.selectAll().where { EmailReports.id eq reportId }.single()[EmailReports.lastSentAt]
    }

    @Test
    fun `a report arrives as one HTML message with the project's numbers and the chosen sections`() {
        val id = report("ann@example.com", sections = "overview,pages", subject = "{project_name} — {period} ({date})",
            header = "Hello <team>")

        EmailService.sendReport(project, "ann@example.com", "7d", id)

        val mail = smtp.mails.single()
        assertEquals("reports@mini.example", mail.from)
        assertEquals(listOf("ann@example.com"), mail.to)
        assertNull(mail.login, "no login without a username")
        assertEquals("Café & Co — last 7 days (${java.time.LocalDate.now()})", mail.message.subject)
        assertEquals("ann@example.com", mail.message.allRecipients.single().toString())
        assertTrue(mail.message.isMimeType("text/html"), mail.message.contentType)
        val html = mail.message.content as String
        assertTrue(html.contains("Café &amp; Co") && html.contains("Hello &lt;team&gt;"), "escaped names and header")
        assertNotNull(Regex(""">3</div>\s*<div[^>]*>Page Views""").find(html), "three page views")
        assertNotNull(Regex(""">2</div>\s*<div[^>]*>Unique Visitors""").find(html), "two visitors")
        assertTrue(html.contains("Top Pages") && html.contains("/menu"))
        assertFalse(html.contains("Top Referrers") || html.contains("Top Countries"), "sections that were not chosen")
        assertNotNull(lastSentAt(id), "the send is recorded")
    }

    @Test
    fun `a manual test send does not count as the scheduled send`() {
        val id = report("ann@example.com")

        EmailService.sendReport(project, "ann@example.com", "24h", id, recordSend = false)

        assertEquals(1, smtp.mails.size)
        assertNull(lastSentAt(id))
    }

    @Test
    fun `it logs in when a username is configured`() {
        useSmtp(smtp.port, username = "mailer", password = "s3cret")

        EmailService.sendReport(project, "ann@example.com", "7d")

        assertEquals("mailer:mailer:s3cret", smtp.mails.single().login)
    }

    @Test
    fun `with STARTTLS on, a server that does not offer it gets no login and no mail`() {
        useSmtp(smtp.port, username = "mailer", password = "s3cret", startTls = true)

        assertFailsWith<jakarta.mail.MessagingException> { EmailService.sendReport(project, "ann@example.com", "7d") }

        assertTrue(smtp.mails.isEmpty(), "nothing was delivered")
        assertNull(smtp.lastLogin, "the password was not sent in clear text")
    }

    @Test
    fun `a mail server that never answers fails the send instead of hanging`() {
        val silent = FakeSmtp(silent = true)
        try {
            useSmtp(silent.port)
            EmailService.smtpTimeoutMillis = 300
            val started = System.currentTimeMillis()

            assertFailsWith<jakarta.mail.MessagingException> { EmailService.sendReport(project, "ann@example.com", "7d") }

            assertTrue(System.currentTimeMillis() - started < 5_000, "gave up quickly")
        } finally {
            silent.close()
        }
    }

    @Test
    fun `sending without a mail server configured is refused`() {
        EmailService.init(null)

        assertFailsWith<IllegalStateException> { EmailService.sendReport(project, "ann@example.com", "7d") }
    }

    @Test
    fun `a report whose hour passed while the server was down is sent at the next check, once`() {
        val now = Instant.now()
        val daily = report("daily@example.com", hour = now.atZone(ZoneOffset.UTC).hour)

        EmailService.checkAndSendDueReports(now.plusSeconds(3 * 3600)) // three hours later: the hour is gone
        assertEquals(listOf("daily@example.com"), smtp.mails.flatMap { it.to })
        assertNotNull(lastSentAt(daily))

        EmailService.checkAndSendDueReports(now.plusSeconds(4 * 3600))
        assertEquals(1, smtp.mails.size, "the catch-up is not repeated")
    }

    @Test
    fun `a refused report is tried again at the next check`() {
        val now = Instant.now()
        val bounced = report("bounce@example.com", hour = now.atZone(ZoneOffset.UTC).hour)

        EmailService.checkAndSendDueReports(now)
        assertNull(lastSentAt(bounced))
        transaction { EmailReports.update({ EmailReports.id eq bounced }) { it[recipientEmail] = "fixed@example.com" } }

        EmailService.checkAndSendDueReports(now.plusSeconds(3600))
        assertEquals(listOf("fixed@example.com"), smtp.mails.flatMap { it.to })
        assertNotNull(lastSentAt(bounced))
    }

    @Test
    fun `the scheduler sends each due report once, and a bounced one does not stop the rest`() {
        val now = Instant.now()
        val utc = now.atZone(ZoneOffset.UTC)
        val bounced = report("bounce@example.com", hour = utc.hour)
        val daily = report("daily@example.com", hour = utc.hour)
        val weekly = report("weekly@example.com", "WEEKLY", hour = utc.hour, day = utc.dayOfWeek.value)
        val otherHour = report("later@example.com", hour = (utc.hour + 1) % 24)
        val otherDay = report("otherday@example.com", "WEEKLY", hour = utc.hour, day = utc.dayOfWeek.value % 7 + 1)
        val paused = report("paused@example.com", hour = utc.hour, active = false)

        EmailService.checkAndSendDueReports(now)

        assertEquals(setOf("daily@example.com", "weekly@example.com"), smtp.mails.flatMap { it.to }.toSet())
        assertEquals(2, smtp.mails.size)
        assertEquals(
            setOf("Café & Co Analytics — last 24 hours", "Café & Co Analytics — last 7 days"),
            smtp.mails.map { it.message.subject }.toSet()
        )
        assertNotNull(lastSentAt(daily))
        assertNotNull(lastSentAt(weekly))
        for (unsent in listOf(bounced, otherHour, otherDay, paused)) assertNull(lastSentAt(unsent))

        EmailService.checkAndSendDueReports(now) // the same hour again, as after a restart
        assertEquals(setOf("daily@example.com", "weekly@example.com"), smtp.mails.flatMap { it.to }.toSet())
        assertEquals(2, smtp.mails.size, "nothing is sent twice")
    }
}
