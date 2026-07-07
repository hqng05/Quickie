package tech.qhuyy.quickie.config

import org.bukkit.configuration.file.FileConfiguration
import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.enums.DatabaseType
import java.math.BigDecimal

@Suppress("UNUSED")
class ConfigManager(
    private val plugin: Quickie
) {
    private var config: FileConfiguration = plugin.config

    fun init() {
        plugin.reloadConfig()
        reload()
    }

    private fun reload() {
        plugin.reloadConfig()
        config = plugin.config
    }

    // ────────────────── Prefix Section ──────────────────
    fun isPrefixEnabled(): Boolean = config.getBoolean("enable-prefix", true)
    fun getPrefix(): String = config.getString(
        "prefix",
        "<gradient:#E43A96:#FFFFFF>ꞯᴜɪᴄᴋɪᴇ</gradient>"
    ) ?: "<gradient:#E43A96:#FFFFFF>ꞯᴜɪᴄᴋɪᴇ</gradient>"
    // ──────────────────────────────────────────────────────

    // ────────────────── Database Section ──────────────────
    // Database type: "sqlite" or "mysql"
    fun getDatabaseType(): DatabaseType
            = DatabaseType.fromString(
        config.getString(
            "database.type",
            "sqlite"
        ))

    // MySQL database connection details
    fun getMySQLHost(): String = config.getString(
        "database.mysql.host",
        "localhost"
    ) ?: "localhost"

    fun getMySQLPort(): Int = config.getInt(
        "database.mysql.port",
        3306
    )

    fun getMySQLDatabase(): String = config.getString(
        "database.mysql.database",
        "quickie_db"
    ) ?: "quickie_db"

    fun getMySQLUsername(): String = config.getString(
        "database.mysql.username",
        "quickie"
    ) ?: "quickie"

    fun getMySQLPassword(): String = config.getString(
        "database.mysql.password",
        "password"
    ) ?: "password"

    // SQLite database file path
    fun getSQLitePath(): String {
        val configPath = config.getString("database.sqlite.file", "quickie.db") ?: "quickie.db"
        return if(configPath.startsWith("/") || configPath.contains(":")) {
            configPath
        } else {
            "${plugin.dataFolder.path}/$configPath"
        }
    }

    // Hikari Connection Pool Section
    fun getMySQLMaxPoolSize(): Int = config.getInt(
        "database.hikari.maximum-pool-size",
        10
    )

    fun getMySQLMinIdle(): Int = config.getInt(
        "database.hikari.minimum-idle",
        2
    )

    fun getPoolConnectionTimeout(): Long = config.getLong(
        "database.hikari.connection-timeout",
        30000
    )

    fun getPoolIdleTimeout(): Long = config.getLong(
        "database.hikari.idle-timeout",
        600000
    )

    fun getPoolMaxLifetime(): Long = config.getLong(
        "database.hikari.max-lifetime",
        1800000
    )
    // ──────────────────────────────────────────────────────

    // ────────────────── Economy Section ──────────────────
    fun getEconomyStartingBalance(): BigDecimal =
        config.getString("economy.starting-balance", "100.00")
            ?.toBigDecimalOrNull() ?: BigDecimal("100.00")

    fun getEconomyCurrencySingular(): String =
        config.getString("economy.currency.singular", "coin") ?: "coin"

    fun getEconomyCurrencyPlural(): String =
        config.getString("economy.currency.plural", "coins") ?: "coins"

    fun getEconomyFractionalDigits(): Int =
        config.getInt("economy.fractional-digits", 2).coerceAtLeast(0)
    // ──────────────────────────────────────────────────────

    // ────────────────── Metrics Section ──────────────────
    fun getMetricsEnabled(): Boolean = config.getBoolean(
        "metrics.enabled",
        true
    )
    // ──────────────────────────────────────────────────────
}