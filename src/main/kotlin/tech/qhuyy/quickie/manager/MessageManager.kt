package tech.qhuyy.quickie.manager

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import tech.qhuyy.quickie.Quickie
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.Properties
import kotlin.text.Regex

class MessageManager(
    private val plugin: Quickie,
    private val bukkitAudiences: BukkitAudiences,
    private val fileName: String = "messages.properties"
) {
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()

    // List messages use an indexed-key convention (see class KDoc / messages.properties):
    //   help.line.0=...
    //   help.line.1=...
    // All keys sharing the same prefix and ending in ".N" (N = zero-based integer) are
    // collected and returned, in numeric order, by getMessageList("help.line").
    private val LIST_KEY_PATTERN = Regex("""^(.+)\.(\d+)$""")

    private data class Cache(
        val single: Map<String, String>,
        val lists: Map<String, List<String>>,
        val prefix: String
    )

    private val EMPTY_CACHE = Cache(emptyMap(), emptyMap(), "")

    // Volatile so a reload() publishing a brand-new Cache is immediately visible to every
    // thread/coroutine that calls sendMessage* (the plugin calls these from async scopes).
    @Volatile
    private var cache: Cache = EMPTY_CACHE

    // ───────────────────────────── Lifecycle ─────────────────────────────

    fun load() {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            // Copy the bundled default from the plugin jar. saveResource will NOT overwrite
            // an existing user file (replace = false), so custom edits are preserved.
            plugin.saveResource(fileName, false)
        }

        val props = Properties()
        file.inputStream().bufferedReader(StandardCharsets.UTF_8).use { props.load(it) }

        // Atomic swap: build the whole new cache first, then publish it in one write.
        cache = parse(props)
        plugin.logger.info(
            "Loaded messages from $fileName (${cache.single.size} keys, ${cache.lists.size} lists)"
        )
    }

    fun reload() = load()

    fun unload() {
        // Only drop the cache. BukkitAudiences is owned by Quickie, which closes it.
        cache = EMPTY_CACHE
    }

    private fun parse(properties: Properties): Cache {
        val listBuckets = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
        val single = LinkedHashMap<String, String>()

        for ((rawKey, rawValue) in properties) {
            val key = rawKey.toString()
            val value = rawValue.toString()
            val match = LIST_KEY_PATTERN.matchEntire(key)
            if (match != null) {
                val prefixKey = match.groupValues[1]
                val index = match.groupValues[2].toIntOrNull() ?: run {
                    // ".N" where N is not an integer: treat as a plain single key.
                    single[key] = value
                    return@run null
                } ?: continue
                // (a) Keep the key in BOTH maps. A key like "help.line.0" is still
                // addressable via getMessage("help.line.0"); it is additionally grouped
                // under the list prefix "help.line" for getMessageList. This way no single
                // key can ever "disappear" just because its suffix looks numeric.
                single[key] = value
                listBuckets.getOrPut(prefixKey) { mutableListOf() }.add(index to value)
            } else {
                single[key] = value
            }
        }

        val lists = listBuckets.mapValues { (prefix, pairs) ->
            val sorted = pairs.sortedBy { it.first }
            detectGaps(prefix, sorted.map { it.first })
            sorted.map { it.second }
        }
        // Prefix is read from its own key; fall back to empty string, never throw.
        val prefix = single["prefix"] ?: single["general.prefix"] ?: ""

        return Cache(single, lists, prefix)
    }

    // Warn (don't fail) when a list has a missing index, e.g. .0 and .2 but no .1,
    // so numbering mistakes are caught at reload time rather than during in-game tests.
    private fun detectGaps(prefix: String, indices: List<Int>) {
        if (indices.isEmpty()) return
        val present = indices.toSet()
        val max = indices.maxOrNull()!!
        for (i in 0..max) {
            if (i !in present) {
                plugin.logger.warning("[${plugin.name}] Gap detected in list key '$prefix' at index $i")
            }
        }
    }

    // ───────────────────────────── Get + fallback ─────────────────────────────

    fun hasMessage(key: String): Boolean =
        cache.single.containsKey(key) || cache.lists.containsKey(key)

    fun getMessage(key: String): String {
        val value = cache.single[key]
        if (value == null) {
            plugin.logger.warning("[${plugin.name}] Missing message key: $key")
            return "<red>Missing message: $key</red>"
        }
        return value
    }

    fun getMessageList(key: String): List<String> {
        val value = cache.lists[key]
        if (value.isNullOrEmpty()) {
            plugin.logger.warning("[${plugin.name}] Missing message list key: $key")
            return listOf("<red>Missing message list: $key</red>")
        }
        return value
    }

    // ───────────────────────────── Internal helpers ─────────────────────────────

    // Build a TagResolver from the vararg, or an empty one when none provided. Done in a
    // single place so every send/deserialize function behaves identically.
    private fun buildResolver(resolvers: Array<out TagResolver>): TagResolver =
        if (resolvers.isEmpty()) TagResolver.empty() else TagResolver.resolver(*resolvers)

    private fun deserialize(template: String, resolver: TagResolver): Component =
        miniMessage.deserialize(template, resolver)

    private fun send(target: CommandSender, component: Component) {
        // Route through BukkitAudiences so we are safe on both Spigot and Paper; never
        // call CommandSender.sendMessage(Component) directly (does not exist on Spigot).
        bukkitAudiences.sender(target).sendMessage(component)
    }

    private fun insertion(key: String, value: String): TagResolver =
        TagResolver.resolver(key, Tag.inserting(Component.text(value)))

    // ───────────────────────────── Public send API ─────────────────────────────

    fun sendMessage(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val resolver = buildResolver(resolvers)
        val component = deserialize(getMessage(key), resolver)
        send(target, component)
    }

    fun sendMessageWithPrefix(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val resolver = buildResolver(resolvers)
        val component = deserialize(cache.prefix + getMessage(key), resolver)
        send(target, component)
    }

    fun sendMessageList(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val resolver = buildResolver(resolvers)
        getMessageList(key).forEach { template ->
            send(target, deserialize(template, resolver))
        }
    }

    fun sendMessageListWithPrefix(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val resolver = buildResolver(resolvers)
        getMessageList(key).forEach { template ->
            send(target, deserialize(cache.prefix + template, resolver))
        }
    }

    fun deserialize(template: String, vararg resolvers: TagResolver): Component {
        return deserialize(template, buildResolver(resolvers))
    }

    fun deserializeKey(key: String, vararg resolvers: TagResolver): Component {
        return deserialize(getMessage(key), buildResolver(resolvers))
    }

    fun deserializeKeyWithPrefix(key: String, vararg resolvers: TagResolver): Component {
        return deserialize(cache.prefix + getMessage(key), buildResolver(resolvers))
    }

    fun resolvers(vararg pairs: Pair<String, Any>): TagResolver =
        TagResolver.resolver(pairs.map { (k, v) -> insertion(k, v.toString()) })

    fun placeholder(key: String, value: String): TagResolver = insertion(key, value)

    fun placeholder(key: String, value: Number): TagResolver = insertion(key, value.toString())

    fun text(text: String): Component = Component.text(text)
}
