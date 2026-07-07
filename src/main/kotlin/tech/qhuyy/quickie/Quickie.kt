package tech.qhuyy.quickie

import com.tcoded.folialib.FoliaLib
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.database.DatabaseManager
import tech.qhuyy.quickie.manager.MessageManager
import tech.qhuyy.quickie.economy.EconomyListener
import tech.qhuyy.quickie.economy.EconomyManager
import tech.qhuyy.quickie.economy.VaultEconomyProvider
import tech.qhuyy.quickie.manager.MetricsManager
import tech.qhuyy.quickie.scheduler.PluginCoroutineScope
import tech.qhuyy.quickie.utils.PluginBuild
import tech.qhuyy.quickie.utils.Software
import kotlinx.coroutines.runBlocking

private const val PLUGIN_ID = 32424

class Quickie : JavaPlugin() {
    lateinit var foliaLib: FoliaLib
        private set
    lateinit var serverSoftware: Software
        private set
    lateinit var pluginBuild: PluginBuild
        private set
    lateinit var bukkitAudiences: BukkitAudiences
        private set
    lateinit var scope: PluginCoroutineScope
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var messageManager: MessageManager
        private set
    lateinit var metricsManager: MetricsManager
        private set
    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var economyManager: EconomyManager
        private set

    override fun onEnable() {
        foliaLib = FoliaLib(this)
        serverPlatformInitialization()
        pluginBuild = PluginBuild(this)
        checkingIfSpigot()
        logScheduleStatus()

        bukkitAudiences = BukkitAudiences.create(this)
        scope = PluginCoroutineScope(this)
        configManager = ConfigManager(this).also { it.init() }
        messageManager = MessageManager(this, bukkitAudiences).also { it.load() }
        metricsManager = MetricsManager(this, PLUGIN_ID).also { it.start() }

        databaseManager = DatabaseManager(this, configManager, scope)
        try {
            databaseManager.connect()
        } catch (e: Exception) {
            logger.severe("Economy/database disabled: ${e.message}")
        }

        if (databaseManager.isConnected) {
            economyManager = EconomyManager(this, databaseManager, configManager)
            runBlocking { economyManager.init() }

            val provider = VaultEconomyProvider(this, economyManager, configManager, databaseManager)
            server.servicesManager.register(
                Economy::class.java,
                provider,
                this,
                ServicePriority.Normal
            )
            server.pluginManager.registerEvents(EconomyListener(this, economyManager), this)
        }
    }

    private fun logScheduleStatus() {
        if (serverSoftware == Software.FOLIA) {
            logger.info("Running on Folia - region-safe scheduling enabled")
        } else {
            logger.info("Running on Paper - standard scheduling enabled")
        }
    }


    private fun serverPlatformInitialization() {
        serverSoftware = Software.detectServerSoftware(foliaLib)
        logger.info("This server is running $serverSoftware")
    }

    private fun checkingIfSpigot() {
        if (serverSoftware == Software.SPIGOT || serverSoftware == Software.UNKNOWN) {
            logger.severe("═══════════════════════════════════════════════════════════════")
            logger.severe("${pluginBuild.getPluginName(false)} requires Paper or Folia to run (including forks).")
            logger.severe("Spigot, non-bukkit and other server software are not supported.")
            logger.severe("Please upgrade to Paper: https://papermc.io/downloads/paper")
            logger.severe("═══════════════════════════════════════════════════════════════")
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        messageManager.unload()
        server.servicesManager.unregisterAll(this)
        databaseManager.shutdown()
        scope.cancel()
    }
}
