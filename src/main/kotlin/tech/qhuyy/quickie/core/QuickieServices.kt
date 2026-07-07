package tech.qhuyy.quickie.core

import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.config.ConfigManager
import tech.qhuyy.quickie.database.DatabaseManager
import tech.qhuyy.quickie.economy.EconomyManager
import tech.qhuyy.quickie.manager.MessageManager
import tech.qhuyy.quickie.scheduler.PluginCoroutineScope

/**
 * Single immutable bundle of every manager instance, created exactly once in
 * [Quickie.onEnable]. Reloads mutate each manager's internal state (e.g. the
 * @Volatile cache in [MessageManager]) on the SAME instance, so commands keep
 * referencing this one [QuickieServices] and always see fresh data — it must
 * never be recreated on reload.
 */
data class QuickieServices(
    val plugin: Quickie,
    val configManager: ConfigManager,
    val databaseManager: DatabaseManager,
    val economyManager: EconomyManager,
    val messageManager: MessageManager,
    val scope: PluginCoroutineScope
)
