package tech.qhuyy.quickie.economy

import tech.qhuyy.quickie.database.AbstractRepository
import tech.qhuyy.quickie.database.DatabaseManager
import tech.qhuyy.quickie.config.ConfigManager
import java.math.BigDecimal
import java.math.RoundingMode

class EconomyDAO(
    db: DatabaseManager,
    private val configManager: ConfigManager
) : AbstractRepository<PlayerEconomyProfile, String>(db, "economy_profiles") {

    // Balances are stored as integer minor units (e.g. cents) in a BIGINT column.
    // SQLite has no real fixed-point DECIMAL type (NUMERIC affinity stores as REAL,
    // causing float drift), so using integer minor units keeps server-side arithmetic
    // exact and identical on both SQLite and MySQL. Vault/fractional-digits only
    // affects display, not storage.
    // IMPORTANT: this storage scale is a fixed compile-time constant. It must NEVER be
    // read from ConfigManager (fractional-digits), because changing that config value
    // after data already exists in the DB would silently corrupt every stored balance.
    private companion object {
        const val CURRENCY_STORAGE_SCALE = 2
    }

    private fun toMinor(b: BigDecimal): Long =
        b.setScale(CURRENCY_STORAGE_SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact()

    private fun fromMinor(l: Long): BigDecimal =
        BigDecimal.valueOf(l).movePointLeft(CURRENCY_STORAGE_SCALE)

    override suspend fun createTable() {
        if (tableExists()) return
        withConnection { conn ->
            conn.prepareStatement(
                """
                CREATE TABLE $tableName (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    username VARCHAR(64) NULL,
                    balance BIGINT NOT NULL
                )
                """.trimIndent()
            ).execute()
        }
    }

    override suspend fun findById(id: String): PlayerEconomyProfile? = withConnection { conn ->
        conn.prepareStatement("SELECT uuid, username, balance FROM $tableName WHERE uuid = ?")
            .apply { setString(1, id) }
            .executeQuery().use { rs ->
                if (rs.next()) {
                    PlayerEconomyProfile(
                        rs.getString("uuid"),
                        rs.getString("username"),
                        fromMinor(rs.getLong("balance"))
                    )
                } else null
            }
    }

    override suspend fun findAll(): List<PlayerEconomyProfile> = withConnection { conn ->
        conn.prepareStatement("SELECT uuid, username, balance FROM $tableName")
            .executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            PlayerEconomyProfile(
                                rs.getString("uuid"),
                                rs.getString("username"),
                                fromMinor(rs.getLong("balance"))
                            )
                        )
                    }
                }
            }
    }

    override suspend fun save(entity: PlayerEconomyProfile) {
        withConnection { conn ->
            val sql = when (db.dialect) {
                DatabaseManager.Dialect.MYSQL ->
                    "INSERT INTO $tableName (uuid, username, balance) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE username = VALUES(username), balance = VALUES(balance)"
                DatabaseManager.Dialect.SQLITE ->
                    "INSERT OR REPLACE INTO $tableName (uuid, username, balance) VALUES (?, ?, ?)"
            }
            conn.prepareStatement(sql).apply {
                setString(1, entity.uuid)
                setString(2, entity.username)
                setLong(3, toMinor(entity.balance))
            }.executeUpdate()
        }
    }

    override suspend fun delete(id: String) {
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE uuid = ?")
                .apply { setString(1, id) }
                .executeUpdate()
        }
    }

    suspend fun addBalance(uuid: String, amount: BigDecimal): BigDecimal = withConnection { conn ->
        val delta = toMinor(amount)
        val upsert = when (db.dialect) {
            DatabaseManager.Dialect.MYSQL ->
                "INSERT INTO $tableName (uuid, balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = balance + ?"
            DatabaseManager.Dialect.SQLITE ->
                "INSERT INTO $tableName (uuid, balance) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET balance = balance + ?"
        }
        conn.prepareStatement(upsert).apply {
            setString(1, uuid)
            setLong(2, delta)
            setLong(3, delta)
        }.executeUpdate()

        conn.prepareStatement("SELECT balance FROM $tableName WHERE uuid = ?")
            .apply { setString(1, uuid) }
            .executeQuery().use { rs ->
                if (rs.next()) fromMinor(rs.getLong("balance")) else BigDecimal.ZERO
            }
    }

    suspend fun subtractBalance(uuid: String, amount: BigDecimal): BigDecimal = withConnection { conn ->
        val delta = toMinor(amount)
        // WHERE balance >= ? guarantees the row is only updated when there are enough
        // funds, so two near-simultaneous withdrawals cannot both succeed and drive the
        // balance negative. rowsAffected == 0 means no account or insufficient funds.
        val affected = conn.prepareStatement(
            "UPDATE $tableName SET balance = balance - ? WHERE uuid = ? AND balance >= ?"
        ).apply {
            setLong(1, delta)
            setString(2, uuid)
            setLong(3, delta)
        }.executeUpdate()

        if (affected == 0) {
            throw IllegalStateException("Insufficient funds or account does not exist: $uuid")
        }

        conn.prepareStatement("SELECT balance FROM $tableName WHERE uuid = ?")
            .apply { setString(1, uuid) }
            .executeQuery().use { rs ->
                if (rs.next()) fromMinor(rs.getLong("balance")) else BigDecimal.ZERO
            }
    }
}
