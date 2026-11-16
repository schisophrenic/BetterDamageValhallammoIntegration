package kr.toxicity.damage.manager

import kr.toxicity.damage.util.PLUGIN
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

/**
 * Per-player UI preferences, stored separately from the existing skin database.
 * All channels are enabled until the player explicitly opts out.
 */
object CombatPreferences {
    private val allowed = setOf("outgoing", "incoming", "healing", "statuses", "pvp", "pve", "elite", "glyphs")
    private val file get() = File(PLUGIN.dataFolder, "combat-preferences.yml")
    private var data = YamlConfiguration()

    fun load() {
        if (!PLUGIN.dataFolder.exists()) PLUGIN.dataFolder.mkdirs()
        data = YamlConfiguration.loadConfiguration(file)
    }

    fun allowedChannels(): Set<String> = allowed

    fun enabled(player: Player, key: String): Boolean =
        key in allowed && data.getBoolean("${player.uniqueId}.$key", true)

    fun set(player: Player, key: String, enabled: Boolean): String {
        if (key !in allowed) return "Unknown channel: $key. Available: ${allowed.joinToString(", ")}"
        data.set("${player.uniqueId}.$key", enabled)
        data.save(file)
        return "Combat indicator ${key}: ${if (enabled) "ON" else "OFF"}"
    }

    fun reset(player: Player): String {
        data.set(player.uniqueId.toString(), null)
        data.save(file)
        return "Combat indicator preferences reset to defaults."
    }

    fun status(player: Player): String =
        allowed.sorted().joinToString(" | ") { "$it: ${if (enabled(player, it)) "on" else "off"}" }
}
