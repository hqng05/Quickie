package tech.qhuyy.quickie.config

import org.bukkit.configuration.file.FileConfiguration
import tech.qhuyy.quickie.Quickie

class ConfigManager(
    private val plugin: Quickie
) {
    private var config: FileConfiguration = plugin.config

    fun init() {
        plugin.reloadConfig()
        init()
    }

    private fun reload() {
        plugin.reloadConfig()
        config = plugin.config
    }

    val getMetricsEnabled: Boolean
        get() = config.getBoolean("metrics.enabled", true)
}