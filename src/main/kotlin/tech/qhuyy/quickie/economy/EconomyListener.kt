package tech.qhuyy.quickie.economy

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import tech.qhuyy.quickie.Quickie

class EconomyListener(
    private val plugin: Quickie,
    private val economyManager: EconomyManager
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        // Load (or create with the starting balance) into the in-memory cache.
        // Fire-and-forget: if it hasn't finished by the first balance read,
        // EconomyManager.getBalance falls back to a direct DB query.
        plugin.scope.launchAsync {
            economyManager.loadProfile(player.uniqueId.toString(), player.name)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        economyManager.unloadProfile(event.player.uniqueId.toString())
    }
}
