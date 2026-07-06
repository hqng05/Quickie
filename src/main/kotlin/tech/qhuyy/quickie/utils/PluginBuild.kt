package tech.qhuyy.quickie.utils

import tech.qhuyy.quickie.Quickie
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

@Suppress("UNUSED")
class PluginBuild(
    private val plugin: Quickie
) {
    data class GitInfo(
        val commitIdAbbrev: String,
        val commitMessage: String,
        val commitTime: String,
        val branch: String,
        val buildTime: String,
        val buildVersion: String,
        val dirty: Boolean
    )

    private val gitInfo: GitInfo by lazy { load() }

    private fun load(): GitInfo {
        val props = Properties()
        val stream = javaClass.getResourceAsStream("/git.properties")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream("git.properties")
            ?: return default()

        return stream.use {
            props.load(it)
            GitInfo(
                commitIdAbbrev = props.getProperty("git.commit.id.abbrev", "Unknown"),
                commitMessage = props.getProperty("git.commit.message.short", "Unknown"),
                commitTime = props.getProperty("git.commit.time")?.let { raw -> format(raw) } ?: "Unknown",
                branch = props.getProperty("git.branch", "Unknown"),
                buildTime = props.getProperty("git.build.time")?.let { raw -> format(raw) } ?: "Unknown",
                buildVersion = plugin.pluginMeta.version,
                dirty = props.getProperty("git.dirty", "false")
                    .toBooleanStrictOrNull() ?: false
            )
        }
    }

    private fun format(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"

        val instant = runCatching { Instant.parse(raw) }
            .recoverCatching { Instant.ofEpochSecond(raw.toLong()) }
            .getOrNull() ?: return "Invalid timestamp"

        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    private fun default() = GitInfo(
        commitIdAbbrev = "Unknown",
        commitMessage = "Unknown",
        commitTime = "Unknown",
        branch = "Unknown",
        buildTime = "Unknown",
        buildVersion = plugin.pluginMeta.version,
        dirty = false
    )

    fun info(): GitInfo = gitInfo

    fun getPluginName(fancy: Boolean = true) = if(fancy) "quickie" else "ꞯᴜɪᴄᴋɪᴇ"

    val commitIdAbbrev get() = gitInfo.commitIdAbbrev
    val commitMessage get() = gitInfo.commitMessage
    val commitTime get() = gitInfo.commitTime
    val branch get() = gitInfo.branch
    val buildTime get() = gitInfo.buildTime
    val buildVersion get() = gitInfo.buildVersion
    val isDirty get() = gitInfo.dirty

    fun toMap(): Map<String, Any> = mapOf(
        "version" to buildVersion,
        "branch" to branch,
        "commit" to commitIdAbbrev,
        "commitMessage" to commitMessage,
        "commitTime" to commitTime,
        "buildTime" to buildTime,
        "isDirty" to isDirty
    )

    fun toJson(): String =
        toMap().entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":\"${it.value}\"" }
}