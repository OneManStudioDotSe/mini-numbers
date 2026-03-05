package se.onemanstudio.core

import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.db.Events
import se.onemanstudio.db.Users
import se.onemanstudio.services.GeoLocationService
import java.time.LocalDateTime
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/**
 * Centralised service lifecycle manager — the "boot sequence" of the application.
 *
 * Tracks a simple state machine: `UNINITIALIZED → INITIALIZING → READY | ERROR`.
 * All external services (database, security, GeoIP, JWT, email, data retention)
 * are started in a fixed order inside [initialize]. If any critical step fails
 * the state moves to `ERROR` and the last exception is stored in [lastError].
 *
 * ## Zero-restart capability
 * After the setup wizard saves a new configuration, it calls [reload] which
 * tears down all services and re-runs [initialize] with the new config.
 * This means the server process never needs to be restarted after initial setup.
 *
 * ## Initialisation order
 * 1. **AnalyticsSecurity** — server salt + hash rotation.
 * 2. **DatabaseFactory** — connect to SQLite or PostgreSQL, run migrations.
 * 3. **Admin user seed** — if the Users table is empty, seed from `.env` config.
 * 4. **GeoLocationService** — load MaxMind `.mmdb` file (optional, non-fatal).
 * 5. **JwtService** — HMAC-SHA256 signer for API token authentication.
 * 6. **Data retention timer** — background task that purges old events every 6 h.
 * 7. **EmailService** — SMTP transport + scheduled report delivery.
 *
 * ## Thread safety
 * All public methods are `@Synchronized` so concurrent calls (e.g. two
 * setup-wizard submits) are serialised. State fields are `@Volatile` for
 * safe reads from non-synchronised code (health-check endpoint, etc.).
 */
object ServiceManager {

    /** Lifecycle states. Transitions: UNINITIALIZED → INITIALIZING → READY | ERROR. */
    enum class State {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        ERROR
    }

    @Volatile
    private var currentState: State = State.UNINITIALIZED

    @Volatile
    private var lastError: Throwable? = null

    @Volatile
    private var startTime: Long = 0

    private var retentionTimer: Timer? = null

    fun isReady(): Boolean = currentState == State.READY
    fun getState(): State = currentState
    fun getLastError(): Throwable? = lastError
    fun getUptimeSeconds(): Long = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000 else 0

    @Synchronized
    fun initialize(config: AppConfig, logger: Logger): Boolean {
        if (currentState == State.READY) {
            logger.info("Services already initialized")
            return true
        }

        currentState = State.INITIALIZING
        lastError = null

        try {
            // 1. Initialize security module with configurable rotation
            logger.info("Initializing security module...")
            AnalyticsSecurity.init(config.security.serverSalt, config.privacy.hashRotationHours)
            logger.info("Security module initialized (hash rotation: ${config.privacy.hashRotationHours}h, privacy mode: ${config.privacy.privacyMode})")

            // 2. Initialize database
            logger.info("Initializing database...")
            DatabaseFactory.init(config.database)
            logger.info("Database initialized successfully")

            // 2.5 Seed admin user if Users table is empty (backward compat migration)
            transaction {
                if (Users.selectAll().count() == 0L) {
                    // If the config password is already BCrypt-hashed, store it directly;
                    // otherwise hash it first (handles plain-text passwords in test/dev environments)
                    val hashedPassword = if (config.security.adminPassword.startsWith("\$2a\$") ||
                        config.security.adminPassword.startsWith("\$2b\$")) {
                        config.security.adminPassword
                    } else {
                        org.mindrot.jbcrypt.BCrypt.hashpw(
                            config.security.adminPassword,
                            org.mindrot.jbcrypt.BCrypt.gensalt(12)
                        )
                    }
                    Users.insert {
                        it[id] = UUID.randomUUID()
                        it[username] = config.security.adminUsername
                        it[passwordHash] = hashedPassword
                        it[role] = "admin"
                    }
                    logger.info("Admin user seeded from configuration")
                }
            }

            // 3. Initialize GeoIP service (optional)
            logger.info("Initializing GeoIP service...")
            try {
                GeoLocationService.init(config.geoip.databasePath)
                logger.info("GeoIP service initialized successfully")
            } catch (e: Exception) {
                logger.warn("GeoIP service initialization failed: ${e.message}")
                logger.warn("Location tracking will be disabled")
            }

            // 4. Initialize JWT service
            logger.info("Initializing JWT service...")
            JwtService.init(config.security.serverSalt)
            logger.info("JWT service initialized")

            // 5. Start data retention cleanup if configured
            if (config.privacy.dataRetentionDays > 0) {
                startRetentionCleanup(config.privacy.dataRetentionDays, logger)
                logger.info("Data retention policy active: ${config.privacy.dataRetentionDays} days")
            }

            // 6. Initialize email service and scheduler
            se.onemanstudio.services.EmailService.init(config.email)
            se.onemanstudio.services.EmailService.startScheduler()

            currentState = State.READY
            startTime = System.currentTimeMillis()
            logger.info("All services initialized successfully")
            return true

        } catch (e: Exception) {
            currentState = State.ERROR
            lastError = e
            logger.error("Failed to initialize services: ${e.message}", e)
            return false
        }
    }

    @Synchronized
    fun reload(config: AppConfig, logger: Logger): Boolean {
        logger.info("Reloading services with new configuration...")
        stopRetentionCleanup()
        currentState = State.UNINITIALIZED
        return initialize(config, logger)
    }

    @Synchronized
    fun shutdown(logger: Logger) {
        logger.info("Shutting down services...")

        try {
            stopRetentionCleanup()
            se.onemanstudio.services.EmailService.stopScheduler()
            GeoLocationService.close()
            DatabaseFactory.close()
            currentState = State.UNINITIALIZED
            startTime = 0
            logger.info("All services shut down successfully")
        } catch (e: Exception) {
            logger.error("Error during shutdown: ${e.message}", e)
        }
    }

    /**
     * Start periodic data retention cleanup
     */
    private fun startRetentionCleanup(retentionDays: Int, logger: Logger) {
        stopRetentionCleanup()

        retentionTimer = Timer("data-retention", true).apply {
            // Run cleanup every 6 hours
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        val cutoff = LocalDateTime.now().minusDays(retentionDays.toLong())
                        val deleted = transaction {
                            Events.deleteWhere { Events.timestamp less cutoff }
                        }
                        if (deleted > 0) {
                            logger.info("Data retention cleanup: deleted $deleted events older than $retentionDays days")
                        }
                    } catch (e: Exception) {
                        logger.error("Data retention cleanup failed: ${e.message}")
                    }
                }
            }, 60_000L, 6 * 60 * 60 * 1000L) // Start after 1 min, run every 6h
        }
    }

    private fun stopRetentionCleanup() {
        retentionTimer?.cancel()
        retentionTimer = null
    }
}
