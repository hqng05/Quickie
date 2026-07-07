package tech.qhuyy.quickie.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.enums.DatabaseType
import tech.qhuyy.quickie.scheduler.PluginCoroutineScope
import java.sql.Connection
import java.util.logging.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseManager(
    private val plugin: Quickie,
    private val configManager: ConfigManager,
    private val coroutineScope: PluginCoroutineScope
) {
    private var pool: HikariDataSource? = null
    lateinit var dialect: Dialect
        private set

    val isConnected: Boolean
        get() = pool != null && !pool!!.isClosed

    fun connect() {
        if (isConnected) return

        val type = configManager.getDatabaseType()
        val config = HikariConfig().apply {
            when (type) {
                DatabaseType.SQLITE -> {
                    dialect = Dialect.SQLITE
                    driverClassName = "org.sqlite.JDBC"
                    jdbcUrl = "jdbc:sqlite:${configManager.getSQLitePath()}"
                    // SQLite writes to a single file; high concurrency is useless and
                    // can cause "database is locked" errors, so keep the pool tiny.
                    maximumPoolSize = 1
                    minimumIdle = 1
                    connectionTimeout = configManager.getPoolConnectionTimeout()
                    idleTimeout = configManager.getPoolIdleTimeout()
                    maxLifetime = configManager.getPoolMaxLifetime()
                }

                DatabaseType.MYSQL -> {
                    dialect = Dialect.MYSQL
                    driverClassName = "com.mysql.cj.jdbc.Driver"
                    jdbcUrl = "jdbc:mysql://${configManager.getMySQLHost()}:${configManager.getMySQLPort()}/" +
                            configManager.getMySQLDatabase() +
                            "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8mb4"
                    username = configManager.getMySQLUsername()
                    password = configManager.getMySQLPassword()
                    maximumPoolSize = configManager.getMySQLMaxPoolSize()
                    minimumIdle = configManager.getMySQLMinIdle()
                    connectionTimeout = configManager.getPoolConnectionTimeout()
                    idleTimeout = configManager.getPoolIdleTimeout()
                    maxLifetime = configManager.getPoolMaxLifetime()
                }
            }
            // Auto-commit on for the whole pool: each statement commits immediately,
            // which is correct for the current simple single-statement DAO use case.
            // Use withTransaction() when a multi-statement atomic operation is needed.
            isAutoCommit = true
            poolName = "Quickie-${dialect.displayName}"
            validationTimeout = 5000
            connectionTestQuery = "SELECT 1"
        }

        try {
            pool = HikariDataSource(config)
            pool!!.connection.use { conn ->
                if (!conn.isValid(5)) {
                    throw IllegalStateException(
                        "Database connection established but validation (isValid) returned false."
                    )
                }
            }
            plugin.logger.info("Connected to ${dialect.displayName} database.")
        } catch (e: Exception) {
            pool?.close()
            pool = null
            plugin.logger.log(
                Level.SEVERE,
                "Failed to connect to the ${dialect.displayName} database. " +
                        "Check your config and that the driver is available.",
                e
            )
            throw IllegalStateException("Database connection failed (${dialect.displayName})", e)
        }
    }

    fun getDataSource(): HikariDataSource =
        pool ?: throw IllegalStateException("Database is not connected. Call connect() first.")

    fun shutdown() {
        try {
            pool?.close()
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error while closing the database pool.", e)
        } finally {
            pool = null
        }
    }

    suspend fun tableExists(tableName: String): Boolean = withConnection { conn ->
        conn.metaData.getTables(null, null, tableName, arrayOf("TABLE")).use { rs ->
            rs.next()
        }
    }

    suspend fun <R> withConnection(block: suspend (Connection) -> R): R =
        withContext(coroutineScope.scope.coroutineContext + Dispatchers.IO) {
            val ds = getDataSource()
            ds.connection.use { conn -> block(conn) }
        }

    suspend fun <R> withTransaction(block: suspend (Connection) -> R): R =
        withContext(coroutineScope.scope.coroutineContext + Dispatchers.IO) {
            val ds = getDataSource()
            ds.connection.use { conn ->
                val prevAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    val result = block(conn)
                    conn.commit()
                    result
                } catch (e: Exception) {
                    try {
                        conn.rollback()
                    } catch (rollbackErr: Exception) {
                        plugin.logger.log(Level.WARNING, "Transaction rollback failed.", rollbackErr)
                    }
                    throw e
                } finally {
                    conn.autoCommit = prevAutoCommit
                }
            }
        }

    enum class Dialect(
        val displayName: String,
        val autoIncrement: String,
        val booleanType: String
    ) {
        MYSQL("MySQL", "INT AUTO_INCREMENT PRIMARY KEY", "BOOLEAN"),
        SQLITE("SQLite", "INTEGER PRIMARY KEY AUTOINCREMENT", "INTEGER");
    }
}
