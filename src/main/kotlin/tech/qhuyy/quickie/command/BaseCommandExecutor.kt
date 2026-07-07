package tech.qhuyy.quickie.command

import dev.jorel.commandapi.executors.CommandArguments
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import tech.qhuyy.quickie.core.QuickieServices
import java.util.logging.Level

object BaseCommandExecutor {

    fun handle(
        command: QuickieCommand,
        sender: CommandSender,
        args: CommandArguments,
        services: QuickieServices
    ) {
        // Player-only guard.
        if (command.requiresPlayer && sender !is Player) {
            services.messageManager.sendMessage(sender, "error.player-only")
            return
        }

        // Permission guard.
        val permission = command.permission
        if (permission != null && !sender.hasPermission(permission)) {
            services.messageManager.sendMessage(sender, "error.no-permission")
            return
        }

        // Execute, isolating unexpected exceptions so one bad command never
        // propagates into the CommandAPI dispatch chain. Both a deliberate FAILURE
        // returned by the command and an uncaught exception are funnelled through
        // the same handler: log SEVERE (with command name; stacktrace only when a
        // real exception exists) and send the generic error message.
        try {
            when (command.execute(sender, args, services)) {
                CommandResult.SUCCESS -> Unit
                CommandResult.HANDLED -> Unit
                CommandResult.FAILURE -> logAndSendGeneric(command, sender, services, null)
            }
        } catch (e: Exception) {
            logAndSendGeneric(command, sender, services, e)
        }
    }

    private fun logAndSendGeneric(
        command: QuickieCommand,
        sender: CommandSender,
        services: QuickieServices,
        error: Exception?
    ) {
        services.plugin.logger.log(
            Level.SEVERE,
            "Command '/${command.name}' failed" + (error?.let { ": ${it.message}" } ?: ""),
            error
        )
        services.messageManager.sendMessage(sender, "error.generic")
    }
}
