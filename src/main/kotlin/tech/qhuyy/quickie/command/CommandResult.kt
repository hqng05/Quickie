package tech.qhuyy.quickie.command

enum class CommandResult {
    /**
     * Command ran correctly; the base executor does not need to send or log anything.
     */
    SUCCESS,

    /**
     * The command already sent its own error/info message to the sender via
     * [tech.qhuyy.quickie.manager.MessageManager]; the base executor does nothing
     * further (and must not log a fake error).
     */
    HANDLED,

    /**
     * Unexpected failure with no specific message sent by the command itself
     * (e.g. an uncaught exception). The base executor logs SEVERE with the
     * exception and sends the generic error message to the sender.
     */
    FAILURE
}
