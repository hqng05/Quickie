package tech.qhuyy.quickie.command

import dev.jorel.commandapi.CommandAPI
import tech.qhuyy.quickie.Quickie
import tech.qhuyy.quickie.core.QuickieServices

class CommandManager(
    private val plugin: Quickie,
    private val services: QuickieServices,
    private val commands: List<QuickieCommand>
) {

    fun registerAll() {
        for (command in commands) {
            command.register(services).register(services.plugin)
            plugin.logger.info("Registered command: /${command.name}")
        }
    }

    fun unregisterAll() {
        for (command in commands) {
            CommandAPI.unregister(command.name)
        }
    }
}
