package tech.qhuyy.quickie.database

import com.zaxxer.hikari.HikariDataSource
import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.config.ConfigManager

class DatabaseManager(
    private val plugin: Quickie,
    private val configManager: ConfigManager
) {
    private lateinit var pool: HikariDataSource
    private lateinit var dialect: SqlDialect

    private enum class SqlDialect(
        val displayName: String,
        val sqlCreateTable: String
    ) {
        MYSQL(
            "MySQL",
            """""".trimIndent()
        ),
        SQLITE(
            "SQLite",
            """""".trimIndent()
        );
    }
}