package se.onemanstudio.core

import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.db.Events
import se.onemanstudio.services.GeoLocationService
import java.time.LocalDateTime
import java.util.Timer
import java.util.TimerTask

/**
 * Centralized service lifecycle management with state tracking and reload capability
 */
object ServiceManager {

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

            // 3. Initialize GeoIP service (optional)
            logger.info("Initializing GeoIP service...")
            try {
                GeoLocationService.init(config.geoip.databasePath)
                logger.info("GeoIP service initialized successfully")
            } catch (e: Exception) {
                logger.warn("GeoIP service initialization failed: ${e.message}")
                logger.warn("Location tracking will be disabled")
            }

            // 4. Start data retention cleanup if configured
            if (config.privacy.dataRetentionDays > 0) {
                startRetentionCleanup(config.privacy.dataRetentionDays, logger)
                logger.info("Data retention policy active: ${config.privacy.dataRetentionDays} days")
            }

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
