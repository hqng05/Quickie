package tech.qhuyy.quickie.economy

import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.database.DatabaseManager
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class EconomyManager(
    private val plugin: Quickie,
    databaseManager: DatabaseManager,
    private val configManager: ConfigManager
) {
    private val dao = EconomyDAO(databaseManager, configManager)

    // In-memory cache of profiles for players currently online.
    // Offline profiles are never cached (queried straight from the DB) to avoid leaks.
    private val cache = ConcurrentHashMap<String, PlayerEconomyProfile>()

    suspend fun init() {
        dao.createTable()
    }

    suspend fun loadProfile(uuid: String, username: String? = null): PlayerEconomyProfile {
        cache[uuid]?.let { cached ->
            if (username != null && cached.username != username) {
                val updated = cached.copy(username = username)
                dao.save(updated)
                cache[uuid] = updated
            }
            return cache[uuid]!!
        }

        val profile = dao.findById(uuid)?.let { existing ->
            if (username != null && existing.username != username) {
                existing.copy(username = username).also { dao.save(it) }
            } else existing
        } ?: PlayerEconomyProfile(
            uuid, username, configManager.getEconomyStartingBalance()
        ).also { dao.save(it) }

        cache[uuid] = profile
        return profile
    }

    fun unloadProfile(uuid: String) {
        cache.remove(uuid)
    }

    suspend fun getBalance(uuid: String): BigDecimal {
        // Online players are always in the cache and return instantly.
        cache[uuid]?.let { return it.balance }
        // Fallback for offline players: query DB without polluting the online cache.
        return dao.findById(uuid)?.balance ?: BigDecimal.ZERO
    }

    suspend fun has(uuid: String, amount: BigDecimal): Boolean =
        getBalance(uuid) >= amount

    suspend fun deposit(uuid: String, amount: BigDecimal): BigDecimal {
        // newBalance is the result of the atomic UPDATE under the DB (addBalance),
        // NOT recomputed here. We only mirror it into the cache atomically.
        val newBalance = dao.addBalance(uuid, amount)
        cache.compute(uuid) { _, old -> old?.copy(balance = newBalance) }
        return newBalance
    }

    suspend fun withdraw(uuid: String, amount: BigDecimal): Boolean {
        return try {
            val newBalance = dao.subtractBalance(uuid, amount)
            cache.compute(uuid) { _, old -> old?.copy(balance = newBalance) }
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    suspend fun setBalance(uuid: String, amount: BigDecimal) {
        // Preserve username from the cached/DB profile; only the balance changes.
        val profile = (cache[uuid] ?: dao.findById(uuid))?.copy(balance = amount)
            ?: PlayerEconomyProfile(uuid, null, amount)
        dao.save(profile)
        cache.compute(uuid) { _, old -> old?.copy(balance = amount) }
    }

    suspend fun createAccount(uuid: String, username: String?): Boolean {
        if (dao.findById(uuid) != null) return false
        dao.save(PlayerEconomyProfile(uuid, username, configManager.getEconomyStartingBalance()))
        return true
    }

    suspend fun hasAccount(uuid: String): Boolean =
        cache[uuid] != null || dao.findById(uuid) != null
}
