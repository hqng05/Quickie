package tech.qhuyy.quickie.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandArguments
import org.bukkit.command.CommandSender
import tech.qhuyy.quickie.core.QuickieServices

interface QuickieCommand {
    /**
     * The root command name, e.g. "home", "warp", "economy".
     */
    val name: String

    /**
     * Permission required for the whole command, or null if every CommandSender
     * may use it. Checked by [BaseCommandExecutor] before [execute] is called.
     */
    val permission: String?

    /**
     * If true, [BaseCommandExecutor] blocks non-player senders (console, command
     * blocks) before calling [execute].
     */
    val requiresPlayer: Boolean

    /**
     * Runs the actual command logic. Implementations should return [CommandResult]
     * to tell the base executor how to react. Any per-sub-argument permission that
     * differs from [permission] must be checked here manually.
     */
    fun execute(sender: CommandSender, args: CommandArguments, services: QuickieServices): CommandResult

    /**
     * Builds this command's CommandAPI DSL (arguments, sub-commands, tab complete…)
     * and returns the [CommandAPICommand]. Inside every `.executes { }` lambda, call
     * [BaseCommandExecutor.handle] rather than re-implementing permission/player-only
     * checks.
     */
    fun register(services: QuickieServices): CommandAPICommand
}
