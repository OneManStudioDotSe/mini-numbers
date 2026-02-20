package se.onemanstudio.core

import org.slf4j.Logger
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.services.GeoLocationService

/**
 * Centralized service lifecycle management with state tracking and reload capability
 *
 * This manager handles initialization and reloading of all application services
 * (database, security, GeoIP) in a thread-safe manner.
 */
object ServiceManager {

    enum class State {
        UNINITIALIZED,  // Before setup complete
        INITIALIZING,   // Services being initialized
        READY,          // All services ready
        ERROR           // Initialization failed
    }

    @Volatile
    private var currentState: State = State.UNINITIALIZED

    @Volatile
    private var lastError: Throwable? = null

    fun isReady(): Boolean = currentState == State.READY
    fun getState(): State = currentState
    fun getLastError(): Throwable? = lastError

    /**
     * Initialize all application services with the given configuration
     *
     * This method is idempotent - safe to call multiple times.
     * Thread-safe via @Synchronized annotation.
     *
     * @param config Application configuration
     * @param logger Logger for tracking initialization progress
     * @return true if initialization successful, false otherwise
     */
    @Synchronized
    fun initialize(config: AppConfig, logger: Logger): Boolean {
        if (currentState == State.READY) {
            logger.info("Services already initialized")
            return true
        }

        currentState = State.INITIALIZING
        lastError = null

        try {
            // 1. Initialize security module
            logger.info("Initializing security module...")
            AnalyticsSecurity.init(config.security.serverSalt)
            logger.info("Security module initialized successfully")

            // 2. Initialize database
            logger.info("Initializing database...")
            DatabaseFactory.init(config.database)
            logger.info("Database initialized successfully")

            // 3. Initialize GeoIP service (optional - failure is non-fatal)
            logger.info("Initializing GeoIP service...")
            try {
                GeoLocationService.init(config.geoip.databasePath)
                logger.info("GeoIP service initialized successfully")
            } catch (e: Exception) {
                logger.warn("GeoIP service initialization failed: ${e.message}")
                logger.warn("Location tracking will be disabled")
            }

            currentState = State.READY
            logger.info("All services initialized successfully")
            return true

        } catch (e: Exception) {
            currentState = State.ERROR
            lastError = e
            logger.error("Failed to initialize services: ${e.message}", e)
            return false
        }
    }

    /**
     * Reload all services with new configuration
     *
     * This resets the state and reinitializes all services.
     *
     * @param config New application configuration
     * @param logger Logger for tracking reload progress
     * @return true if reload successful, false otherwise
     */
    @Synchronized
    fun reload(config: AppConfig, logger: Logger): Boolean {
        logger.info("Reloading services with new configuration...")
        currentState = State.UNINITIALIZED
        return initialize(config, logger)
    }

    /**
     * Shutdown all services and release resources
     *
     * This method should be called on application shutdown to properly
     * close database connections, GeoIP readers, and other resources.
     *
     * Thread-safe via @Synchronized annotation.
     *
     * @param logger Logger for tracking shutdown progress
     */
    @Synchronized
    fun shutdown(logger: Logger) {
        logger.info("Shutting down services...")

        try {
            // Close GeoIP service
            logger.info("Closing GeoIP service...")
            GeoLocationService.close()

            // Close database connections
            logger.info("Closing database connections...")
            DatabaseFactory.close()

            currentState = State.UNINITIALIZED
            logger.info("All services shut down successfully")

        } catch (e: Exception) {
            logger.error("Error during shutdown: ${e.message}", e)
        }
    }
}
