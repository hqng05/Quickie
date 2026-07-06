package tech.qhuyy.quickie.scheduler

import com.tcoded.folialib.FoliaLib
import com.tcoded.folialib.wrapper.task.WrappedTask
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.qhuyy.quickie.Quickie
import java.util.logging.Level

class PluginCoroutineScope(
    private val plugin: Quickie
) {
    private val foliaLib: FoliaLib = plugin.foliaLib

    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    plugin.logger.log(Level.SEVERE, "Unhandled exception in coroutine", e)
                }
    )

    fun runTimer(delay: Long, period: Long, f: () -> Unit): WrappedTask = foliaLib.scheduler.runTimer(f, delay, period)

    fun launchAsync(f: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            plugin.logger.log(Level.SEVERE, "Unhandled async exception", e)
        }) {
            f()
        }
    }

    fun cancel() {
        scope.cancel("Plugin disabling")
    }
}