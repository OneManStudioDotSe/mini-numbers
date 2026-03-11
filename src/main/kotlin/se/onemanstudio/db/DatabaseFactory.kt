package se.onemanstudio.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import se.onemanstudio.config.models.DatabaseConfig
import se.onemanstudio.config.models.DatabaseType

/**
 * Database lifecycle manager — connects, migrates, and tears down the database.
 *
 * Supports two backends selected by the `DB_TYPE` environment variable:
 * - **SQLite** — single-file, zero-config, ideal for small/medium deployments.
 *   Connected directly via JDBC (no connection pool needed).
 * - **PostgreSQL** — production-grade with HikariCP connection pooling,
 *   repeatable-read isolation, and configurable pool size.
 *
 * On [init], Exposed's `SchemaUtils.createMissingTablesAndColumns` is called
 * which handles both initial table creation and forward-compatible schema
 * evolution (new columns are added automatically without data loss).
 *
 * [close] cleanly shuts down the HikariCP pool (PostgreSQL) or is a no-op
 * (SQLite). It is safe to call multiple times.
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    // Store data source for cleanup on shutdown
    private var dataSource: HikariDataSource? = null

    /**
     * Initialize database connection and create schema
     * Automatically seeds demo data if database is empty
     *
     * @param config Database configuration from environment
     */
    fun init(config: DatabaseConfig) {
        // Close existing connection if reinitializing
        close()

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
                val hikariDataSource = createHikariDataSource(config)
                dataSource = hikariDataSource
                Database.connect(hikariDataSource)
            }
        }

        transaction(database) {
            // Create tables and add any missing columns (supports schema evolution)
            SchemaUtils.createMissingTablesAndColumns(Projects, Events, ConversionGoals, Funnels, FunnelSteps, Segments, RefreshTokens, Users, Webhooks, WebhookDeliveries, EmailReports)
        }
    }

    /**
     * Reset database: delete all data
     * WARNING: This is destructive and cannot be undone!
     *
     * @param config Database configuration (not used but kept for consistency)
     */
    fun reset() {
        transaction {
            // Delete in correct order (events first due to foreign key constraint)
            Events.deleteAll()
            Projects.deleteAll()
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

    /**
     * Close database connection pool and release resources
     * Safe to call multiple times
     */
    fun close() {
        try {
            dataSource?.close()
            dataSource = null
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Log error but don't throw - shutdown should continue
            logger.error("Error closing database connection: ${e.message}", e)
        }
    }
}
