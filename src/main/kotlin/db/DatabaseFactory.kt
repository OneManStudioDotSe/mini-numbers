package se.onemanstudio.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

object DatabaseFactory {
    fun init() {
        // Use SQLite for local demo mode
        val driverClassName = "org.sqlite.JDBC"
        val jdbcUrl = "jdbc:sqlite:./stats.db"

        val database = Database.connect(jdbcUrl, driverClassName)

        transaction(database) {
            SchemaUtils.create(Projects, Events)

            // Only seed if the database is empty
            if (Projects.selectAll().empty()) {
                seedDemoData()
            }
        }
    }

    fun seedDemoData(count: Int = 1000) {
        val demoId = UUID.randomUUID()
        Projects.insert {
            it[id] = demoId
            it[name] = "Professional Demo"
            it[domain] = "localhost"
            it[apiKey] = "demo-key-123"
        }

        val paths = listOf("/home", "/pricing", "/blog/post-1", "/docs", "/contact")
        val referrers = listOf("google.com", "twitter.com", "github.com", null, "linkedin.com")
        val browsers = listOf("Chrome", "Firefox", "Safari", "Edge")
        val oss = listOf("MacOS", "Windows", "iOS", "Android", "Linux")
        val locations = listOf("USA" to "New York", "Greece" to "Athens", "Sweden" to "Stockholm", "UK" to "London", "Germany" to "Berlin")

        for (i in 1..count) {
            val randomTimestamp = LocalDateTime.now()
                .minusDays((0..365).random().toLong()) // Spans a whole year
                .minusMinutes((0..1440).random().toLong())

            Events.insert {
                it[projectId] = demoId
                it[visitorHash] = "v-${(1..200).random()}"
                it[path] = paths.random()
                it[timestamp] = randomTimestamp

                it[sessionId] = "sess-${UUID.randomUUID()}"
                it[eventType] = "pageview"
                it[path] = paths.random()
                it[referrer] = referrers.random()
                it[duration] = (5..300).random()

                val loc = locations.random()
                it[country] = loc.first
                it[city] = loc.second

                it[browser] = browsers.random()
                it[os] = oss.random()
            }
        }
    }

    /*
    fun init() {
        val driverClassName = "org.postgresql.Driver" // Use "org.sqlite.JDBC" for SQLite
        val jdbcUrl = "jdbc:postgresql://localhost:5432/your_db_name" // Change to your DB string

        val database = Database.connect(createHikariDataSource(driverClassName, jdbcUrl))

        transaction {
            // Check if a test project exists, if not, create one
            if (Projects.selectAll().empty()) {
                Projects.insert {
                    it[id] = java.util.UUID.randomUUID()
                    it[name] = "Test Website"
                    it[domain] = "localhost"
                    it[apiKey] = "test-key-123"
                }
            }
        }

        transaction(database) {
            // This creates the tables in the DB automatically
            SchemaUtils.create(Projects, Events)
        }
    }
    */

    /*
    private fun createHikariDataSource(driver: String, url: String) = HikariDataSource(
        HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = "your_username"
            password = "your_password"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
    )
     */
}