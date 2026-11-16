package kr.toxicity.damage.manager

import kr.toxicity.damage.api.ReloadState
import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.manager.CommandManager
import kr.toxicity.damage.api.pack.PackAssets
import kr.toxicity.damage.command.effect
import kr.toxicity.damage.command.register
import kr.toxicity.damage.command.skin
import kr.toxicity.damage.util.*
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.parser.standard.DoubleParser.doubleParser
import org.incendo.cloud.parser.standard.StringParser.stringParser
import org.incendo.cloud.suggestion.SuggestionProvider.blockingStrings

object CommandManagerImpl : CommandManager {
    override fun reload(assets: PackAssets) {
    }

    override fun start() {
        LegacyPaperCommandManager.createNative(
            PLUGIN,
            ExecutionCoordinator.simpleCoordinator()
        ).apply {
            if (hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
                registerBrigadier()
                brigadierManager().setNativeNumberSuggestions(true)
            } else if (hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) registerAsynchronousCompletions()
        }.register(
            "betterdamage",
            "All-related command.",
            "bd"
        ) {
            create(
                "reload",
                "Reloads BetterDamage.",
                "re", "rl"
            ) {
                handler(::reload)
            }
            create(
                "test",
                "Tests effects.",
                "t"
            ) {
                senderType(Player::class.java)
                    .required("effect", stringParser(), blockingStrings { _, _ -> EffectManagerImpl.effectNames() })
                    .required("damage", doubleParser())
                    .handler(::test)
            }
            create(
                "select",
                "Selects player's damage skin.",
                "s"
            ) {
                required("player", offlinePlayerParser())
                    .required("skin", stringParser(), blockingStrings { _, _ -> DamageSkinManagerImpl.allNames() })
                    .handler(::select)
            }
            create(
                "add",
                "Adds player's damage skin.",
                "a"
            ) {
                required("player", offlinePlayerParser())
                    .required("skin", stringParser(), blockingStrings { _, _ -> DamageSkinManagerImpl.allNames() })
                    .handler(::add)
            }
            create(
                "remove",
                "Removes player's damage skin.",
                "r"
            ) {
                required("player", offlinePlayerParser())
                    .required("skin", stringParser(), blockingStrings { _, _ -> DamageSkinManagerImpl.allNames() })
                    .handler(::remove)
            }
            create(
                "combat",
                "Set personal ValhallaMMO combat indicator channels."
            ) {
                senderType(Player::class.java)
                    .required("channel", stringParser(), blockingStrings { _, _ -> CombatPreferences.allowedChannels().sorted() })
                    .required("state", stringParser(), blockingStrings { _, _ -> listOf("on", "off") })
                    .handler(::combat)
            }
            create(
                "combatstatus",
                "Show your combat indicator preferences."
            ) {
                senderType(Player::class.java)
                    .handler(::combatStatus)
            }
            create(
                "combatreset",
                "Restore your default combat indicator preferences."
            ) {
                senderType(Player::class.java)
                    .handler(::combatReset)
            }
            create(
                "info",
                "Infos some player's data.",
                "i"
            ) {
                required("player", offlinePlayerParser())
                    .handler(::info)
            }
        }
    }

    private fun reload(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender()
        async {
            when (val state = PLUGIN.reload()) {
                is ReloadState.Failure -> state.throwable.handle(sender, "Unable to reload.")
                is ReloadState.OnReload -> sender.audience().warn("This plugin is still on reload!")
                is ReloadState.Success -> sender.audience().info("Reload success: (${state.time.withComma()} ms)")
            }
        }
    }

    private fun test(ctx: CommandContext<Player>) {
        val player = ctx.sender()
        val effect = ctx.effect("effect") { return player.audience().warn("This effect doesn't exist: $it") }
        val d = ctx.get<Double>("damage")
        effect.play(DamageEffectData(player, player, d))
        player.audience().info("Successfully played.")
    }

    private fun select(ctx: CommandContext<CommandSender>) {
        val offline = ctx.get<OfflinePlayer>("player")
        val sender = ctx.sender().audience()
        val skin = ctx.skin("skin") { return sender.warn("This skin doesn't exist: $it") }
        offline.setPlayerData {
            if (select(skin)) sender.info("Successfully selected: ${skin.name()}")
            else sender.warn("This player doesn't have this skin: ${skin.name()}")
        }
    }

    private fun add(ctx: CommandContext<CommandSender>) {
        val offline = ctx.get<OfflinePlayer>("player")
        val sender = ctx.sender().audience()
        val skin = ctx.skin("skin") { return sender.warn("This skin doesn't exist: $it") }
        offline.setPlayerData {
            if (add(skin)) sender.info("Successfully added: ${skin.name()}")
            else sender.warn("This player already have this skin: ${skin.name()}")
        }
    }

    private fun remove(ctx: CommandContext<CommandSender>) {
        val offline = ctx.get<OfflinePlayer>("player")
        val sender = ctx.sender().audience()
        val skin = ctx.skin("skin") { return sender.warn("This skin doesn't exist: $it") }
        offline.setPlayerData {
            if (remove(skin)) sender.info("Successfully removed: ${skin.name()}")
            else sender.warn("This player doesn't have this skin: ${skin.name()}")
        }
    }

    private fun info(ctx: CommandContext<CommandSender>) {
        val offline = ctx.get<OfflinePlayer>("player")
        val sender = ctx.sender().audience()
        offline.getPlayerData {
            sender.info("--------------------")
            sender.info("Selected skin: ${selectedSkin()?.name()}")
            sender.info("Skins:")
            skins().forEach {
                sender.info(" - ${it.name()}")
            }
            sender.info("--------------------")
        }
    }
    private fun combat(ctx: CommandContext<Player>) {
        val player = ctx.sender()
        val channel = ctx.get<String>("channel").lowercase()
        val state = ctx.get<String>("state").lowercase()
        if (state !in setOf("on", "off")) {
            player.sendMessage("Usage: /bd combat <channel> <on|off>")
            return
        }
        try {
            player.sendMessage(CombatPreferences.set(player, channel, state == "on"))
        } catch (e: Exception) {
            PLUGIN.logger.warning("Failed to save BetterDamage combat preference: ${e.message}")
            player.sendMessage("Could not save combat preferences; check server logs.")
        }
    }

    private fun combatStatus(ctx: CommandContext<Player>) {
        val player = ctx.sender()
        player.sendMessage("Combat indicators: ${CombatPreferences.status(player)}")
    }

    private fun combatReset(ctx: CommandContext<Player>) {
        val player = ctx.sender()
        try {
            player.sendMessage(CombatPreferences.reset(player))
        } catch (e: Exception) {
            PLUGIN.logger.warning("Failed to reset BetterDamage combat preferences: ${e.message}")
            player.sendMessage("Could not reset combat preferences; check server logs.")
        }
    }

}