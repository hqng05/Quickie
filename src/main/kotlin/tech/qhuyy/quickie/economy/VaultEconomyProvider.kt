package tech.qhuyy.quickie.economy

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.database.DatabaseManager
import java.math.BigDecimal
import java.text.DecimalFormat
import kotlinx.coroutines.runBlocking

@Suppress("OVERRIDE_DEPRECATION")
class VaultEconomyProvider(
    private val plugin: Quickie,
    private val economyManager: EconomyManager,
    private val configManager: ConfigManager,
    private val databaseManager: DatabaseManager
) : Economy {

    // Vault's Economy interface is synchronous, but our storage layer is suspend-based
    // (async JDBC via Hikari + PluginCoroutineScope). We bridge with runBlocking.
    // Trade-off: when the player is online the profile is already in the in-memory cache,
    // so getBalance/deposit/withdraw resolve almost instantly and runBlocking barely blocks
    // the server thread. For offline players (e.g. an admin command editing someone who is
    // not online) runBlocking waits on a real DB round-trip — acceptable because Vault
    // mandates a synchronous return value and these calls are infrequent.
    private fun <T> blocking(action: suspend () -> T): T = runBlocking { action() }

    private fun uuidOf(player: OfflinePlayer): String = player.uniqueId.toString()

    // ───────────────────────────── Metadata ─────────────────────────────

    override fun isEnabled(): Boolean = databaseManager.isConnected

    override fun getName(): String = "Quickie"

    override fun hasBankSupport(): Boolean = false

    override fun fractionalDigits(): Int = configManager.getEconomyFractionalDigits()

    override fun format(amount: Double): String {
        val digits = configManager.getEconomyFractionalDigits()
        val pattern = buildString {
            append("#,##0")
            if (digits > 0) {
                append(".")
                repeat(digits) { append("0") }
            }
        }
        val formatted = DecimalFormat(pattern).format(amount)
        val name = if (amount == 1.0) {
            configManager.getEconomyCurrencySingular()
        } else {
            configManager.getEconomyCurrencyPlural()
        }
        return "$formatted $name"
    }

    override fun currencyNamePlural(): String = configManager.getEconomyCurrencyPlural()

    override fun currencyNameSingular(): String = configManager.getEconomyCurrencySingular()

    // ───────────────────────────── Accounts ─────────────────────────────

    override fun hasAccount(playerName: String): Boolean =
        hasAccount(Bukkit.getOfflinePlayer(playerName))

    override fun hasAccount(player: OfflinePlayer): Boolean =
        blocking { economyManager.hasAccount(uuidOf(player)) }

    override fun hasAccount(playerName: String, worldName: String): Boolean =
        hasAccount(Bukkit.getOfflinePlayer(playerName))

    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean =
        hasAccount(player)

    override fun createPlayerAccount(playerName: String): Boolean =
        createPlayerAccount(Bukkit.getOfflinePlayer(playerName))

    override fun createPlayerAccount(player: OfflinePlayer): Boolean =
        blocking { economyManager.createAccount(uuidOf(player), player.name) }

    override fun createPlayerAccount(playerName: String, worldName: String): Boolean =
        createPlayerAccount(Bukkit.getOfflinePlayer(playerName))

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean =
        createPlayerAccount(player)

    // ───────────────────────────── Balances ─────────────────────────────

    override fun getBalance(playerName: String): Double =
        getBalance(Bukkit.getOfflinePlayer(playerName))

    override fun getBalance(player: OfflinePlayer): Double =
        blocking { economyManager.getBalance(uuidOf(player)) }.toDouble()

    override fun getBalance(playerName: String, worldName: String): Double =
        getBalance(Bukkit.getOfflinePlayer(playerName))

    override fun getBalance(player: OfflinePlayer, worldName: String): Double =
        getBalance(player)

    override fun has(playerName: String, amount: Double): Boolean =
        has(Bukkit.getOfflinePlayer(playerName), amount)

    override fun has(player: OfflinePlayer, amount: Double): Boolean =
        blocking { economyManager.has(uuidOf(player), amount.toBigDecimal()) }

    override fun has(playerName: String, worldName: String, amount: Double): Boolean =
        has(Bukkit.getOfflinePlayer(playerName), amount)

    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean =
        has(player, amount)

    // ───────────────────────────── Deposit ─────────────────────────────

    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse =
        depositPlayer(Bukkit.getOfflinePlayer(playerName), amount)

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        val uuid = uuidOf(player)
        return blocking {
            economyManager.deposit(uuid, amount.toBigDecimal())
            val balance = economyManager.getBalance(uuid)
            EconomyResponse(amount, balance.toDouble(), EconomyResponse.ResponseType.SUCCESS, "")
        }
    }

    override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse =
        depositPlayer(Bukkit.getOfflinePlayer(playerName), amount)

    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse =
        depositPlayer(player, amount)

    // ───────────────────────────── Withdraw ─────────────────────────────

    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse =
        withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount)

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        val uuid = uuidOf(player)
        return blocking {
            val ok = economyManager.withdraw(uuid, amount.toBigDecimal())
            val balance = economyManager.getBalance(uuid)
            if (ok) {
                EconomyResponse(amount, balance.toDouble(), EconomyResponse.ResponseType.SUCCESS, "")
            } else {
                EconomyResponse(
                    amount,
                    balance.toDouble(),
                    EconomyResponse.ResponseType.FAILURE,
                    "Insufficient funds"
                )
            }
        }
    }

    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse =
        withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount)

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse =
        withdrawPlayer(player, amount)

    // ───────────────────────────── Banks (not supported) ─────────────────────────────

    private fun notImplemented(): EconomyResponse =
        EconomyResponse(
            0.0,
            0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "Bank support is not implemented"
        )

    override fun createBank(name: String, playerName: String): EconomyResponse = notImplemented()
    override fun createBank(name: String, player: OfflinePlayer): EconomyResponse = notImplemented()
    override fun deleteBank(name: String): EconomyResponse = notImplemented()
    override fun bankBalance(name: String): EconomyResponse = notImplemented()
    override fun bankHas(name: String, amount: Double): EconomyResponse = notImplemented()
    override fun bankWithdraw(name: String, amount: Double): EconomyResponse = notImplemented()
    override fun bankDeposit(name: String, amount: Double): EconomyResponse = notImplemented()
    override fun isBankOwner(name: String, playerName: String): EconomyResponse = notImplemented()
    override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse = notImplemented()
    override fun isBankMember(name: String, playerName: String): EconomyResponse = notImplemented()
    override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse = notImplemented()
    override fun getBanks(): List<String> = emptyList()
}
