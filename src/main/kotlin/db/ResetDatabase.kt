package se.onemanstudio.db

import org.slf4j.LoggerFactory
import se.onemanstudio.config.ConfigLoader

private val logger = LoggerFactory.getLogger("ResetDatabase")

/**
 * Standalone entry point for database reset
 * Invoked by Gradle task: ./gradlew reset
 */
fun main() {
    logger.info("Loading configuration...")

    val config = try {
        ConfigLoader.load()
    } catch (e: Exception) {
        logger.error("Failed to load configuration: ${e.message}")
        logger.error("Please ensure .env file exists with required configuration.")
        logger.error("See .env.example for template.")
        return
    }

    logger.info("Initializing database connection...")

    try {
        DatabaseFactory.init(config.database)
    } catch (e: Exception) {
        logger.error("Failed to initialize database: ${e.message}")
        return
    }

    logger.info("Resetting database...")

    try {
        DatabaseFactory.reset(config.database)
        logger.info("Database reset successfully!")
    } catch (e: Exception) {
        logger.error("Failed to reset database: ${e.message}")
        return
    }
}
