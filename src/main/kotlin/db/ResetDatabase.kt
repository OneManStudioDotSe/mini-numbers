package se.onemanstudio.db

import se.onemanstudio.config.ConfigLoader

/**
 * Standalone entry point for database reset
 * Invoked by Gradle task: ./gradlew reset
 */
fun main() {
    println("Loading configuration...")

    val config = try {
        ConfigLoader.load()
    } catch (e: Exception) {
        println("ERROR: Failed to load configuration")
        println(e.message)
        println()
        println("Please ensure .env file exists with required configuration.")
        println("See .env.example for template.")
        return
    }

    println("Initializing database connection...")

    try {
        DatabaseFactory.init(config.database)
    } catch (e: Exception) {
        println("ERROR: Failed to initialize database")
        println(e.message)
        return
    }

    println("Resetting database...")

    try {
        DatabaseFactory.reset(config.database)
        println("Database reset successfully!")
    } catch (e: Exception) {
        println("ERROR: Failed to reset database")
        println(e.message)
        return
    }
}
