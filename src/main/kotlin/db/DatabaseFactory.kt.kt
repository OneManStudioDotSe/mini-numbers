package se.onemanstudio.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.config.DatabaseConfig
import se.onemanstudio.config.DatabaseType
import java.time.LocalDateTime
import java.util.UUID

/**
 * Database factory for initializing and managing database connections
 * Supports both SQLite and PostgreSQL based on configuration
 */
object DatabaseFactory {

    /**
     * Initialize database connection and create schema
     * Automatically seeds demo data if database is empty
     *
     * @param config Database configuration from environment
     */
    fun init(config: DatabaseConfig) {
        val database = when (config.type) {
            DatabaseType.SQLITE -> {
                // SQLite connection (simple, file-based)
                Database.connect(
                    url = "jdbc:sqlite:${config.path}",
                    driver = "org.sqlite.JDBC"
                )
            }
            DatabaseType.POSTGRESQL -> {
                // PostgreSQL connection (production-ready with connection pooling)
                Database.connect(createHikariDataSource(config))
            }
        }

        transaction(database) {
            // Create tables if they don't exist
            SchemaUtils.create(Projects, Events)

            // Seed demo data only if database is empty
            if (Projects.selectAll().empty()) {
                seedDemoData()
            }
        }
    }

    /**
     * Reset database: delete all data and re-seed demo
     * WARNING: This is destructive and cannot be undone!
     *
     * @param config Database configuration (not used but kept for consistency)
     */
    fun reset(config: DatabaseConfig) {
        transaction {
            // Delete in correct order (events first due to foreign key constraint)
            Events.deleteAll()
            Projects.deleteAll()

            // Re-seed demo data
            seedDemoData()
        }
    }

    /**
     * Seed database with demo data
     * Creates one demo project with realistic sample events
     *
     * @param count Number of events to generate (default: 1000)
     */
    fun seedDemoData(count: Int = 1000) {
        val demoId = UUID.randomUUID()

        // Create demo project
        Projects.insert {
            it[id] = demoId
            it[name] = "Professional Demo"
            it[domain] = "localhost"
            it[apiKey] = "demo-key-123"
        }

        // Sample data for realistic events
        val paths = listOf("/home", "/pricing", "/blog/post-1", "/docs", "/contact")
        val referrers = listOf("google.com", "twitter.com", "github.com", null, "linkedin.com")
        val browsers = listOf("Chrome", "Firefox", "Safari", "Edge")
        val oss = listOf("MacOS", "Windows", "iOS", "Android", "Linux")
        val locations = listOf(
            "USA" to "New York",
            "Greece" to "Athens",
            "Sweden" to "Stockholm",
            "UK" to "London",
            "Germany" to "Berlin"
        )

        // Generate sample events spread across the past year
        for (i in 1..count) {
            val randomTimestamp = LocalDateTime.now()
                .minusDays((0..365).random().toLong())      // Random day in past year
                .minusMinutes((0..1440).random().toLong())  // Random time of day

            Events.insert {
                it[projectId] = demoId
                it[visitorHash] = "v-${(1..200).random()}"  // Simulate 200 unique visitors
                it[sessionId] = "sess-${UUID.randomUUID()}"
                it[eventType] = "pageview"
                it[path] = paths.random()
                it[referrer] = referrers.random()
                it[duration] = (5..300).random()
                it[timestamp] = randomTimestamp

                // Random location
                val loc = locations.random()
                it[country] = loc.first
                it[city] = loc.second

                // Random browser and OS
                it[browser] = browsers.random()
                it[os] = oss.random()
            }
        }
    }

    /**
     * Create HikariCP connection pool for PostgreSQL
     * Provides connection pooling, health checks, and optimal performance
     *
     * @param config Database configuration with PostgreSQL settings
     * @return Configured HikariDataSource
     */
    private fun createHikariDataSource(config: DatabaseConfig): HikariDataSource {
        return HikariDataSource(HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })
    }
}