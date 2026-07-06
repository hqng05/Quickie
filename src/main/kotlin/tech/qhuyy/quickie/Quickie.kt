package tech.qhuyy.quickie

import org.bukkit.plugin.java.JavaPlugin
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.manager.MetricsManager

private const val PLUGIN_ID = 32424

class Quickie : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set
    lateinit var metricsManager: MetricsManager
        private set

    override fun onEnable() {
        configManager = ConfigManager(this).also { it.init() }
        metricsManager = MetricsManager(this, PLUGIN_ID).also { it.start() }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
