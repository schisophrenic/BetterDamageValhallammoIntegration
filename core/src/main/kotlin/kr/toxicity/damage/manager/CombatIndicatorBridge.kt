package kr.toxicity.damage.manager

import kr.toxicity.damage.api.BetterDamage
import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.trigger.DamageTriggerType
import kr.toxicity.damage.util.PLUGIN
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Optional, non-invasive bridge to ValhallaMMO's DamageIndicatorStrategy.
 *
 * ValhallaMMO calls its strategy with already-calculated damage and the actual
 * CustomDamageType (including its critical flag). It may apply health after the
 * indicator callback and can later apply one-shot protection. We therefore
 * compare final health+absorption across a short configured tick window before
 * rendering, avoiding use of the raw Bukkit damage as an authoritative number.
 *
 * The native strategy is retained but moved behind our proxy. When disabled,
 * this proxy declines use, returning control to the ordinary native indicator.
 * For Valhalla dummies, return the original nullify-damage result.
 */
object CombatIndicatorBridge : Listener {
    private val categoryNames = setOf(
        "fire", "magic", "projectile", "explosion", "poison", "falling",
        "melee", "bleed", "freezing", "lightning", "bludgeoning",
        "radiant", "necrotic", "other"
    )
    private val extraNames = setOf(
        "critical", "blocked", "dodged", "resisted", "absorbed",
        "healing", "regeneration", "lifesteal", "elite", "elite_high"
    )
    private val effectNames = (categoryNames.map { "valhalla_$it" } +
        categoryNames.map { "valhalla_critical_$it" } +
        extraNames.map { "valhalla_$it" }).toSet()
    private var yaml = YamlConfiguration()
    private var proxy: Any? = null
    private var registered = false
    private var dodgeRegistered = false
    private val pending = LinkedHashMap<UUID, TargetBatch>()
    private val recentAttack = mutableMapOf<UUID, Long>()
    private val healEventUntil = mutableMapOf<UUID, Long>()
    private val healthSnapshot = mutableMapOf<UUID, Double>()
    private var nativeAvailable = false

    data class Hit(
        val amount: Double,
        val category: String,
        val critical: Boolean,
        val mitigated: Double,
        val attacker: Entity?,
        val dummy: Boolean
    )
    data class TargetBatch(
        val victim: LivingEntity,
        val initialHealth: Double,
        val initialAbsorption: Double,
        val hits: MutableList<Hit> = mutableListOf()
    )

    private fun flag(path: String, default: Boolean): Boolean = yaml.getBoolean("valhalla-combat.$path", default)
    private fun int(path: String, default: Int) = yaml.getInt("valhalla-combat.$path", default)
    private fun double(path: String, default: Double) = yaml.getDouble("valhalla-combat.$path", default)
    private fun integration(): Boolean {
        val config = BetterDamage.inst().config()
        return config.valhallaEnabled() && Bukkit.getPluginManager().isPluginEnabled("ValhallaMMO")
    }
    private fun damage(): Boolean = integration() && BetterDamage.inst().config().valhallaDamageEnabled()

    fun start() {
        if (registered) return
        registered = true
        CombatPreferences.load()
        Bukkit.getPluginManager().registerEvents(this, PLUGIN)
        installNativeHook()
        installDodgeHook()
        // Sampling catches direct setHealth() healing that bypasses Bukkit regain events.
        // Only online players are sampled to keep the process bounded.
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable { sampleDirectHealing() }, 5L, 5L)
    }

    fun reload() {
        pending.clear()
        yaml = YamlConfiguration.loadConfiguration(File(PLUGIN.dataFolder, "config.yml"))
        CombatPreferences.load()
        if (!nativeAvailable) installNativeHook()
        if (!dodgeRegistered) installDodgeHook()
    }

    fun end() {
        pending.clear()
        healthSnapshot.clear()
        recentAttack.clear()
        healEventUntil.clear()
        val p = proxy
        if (p != null) {
            try {
                val cls = valhallaLoader()?.loadClass("me.athlaeos.valhallammo.entities.damageindicators.DamageIndicatorRegistry")
                @Suppress("UNCHECKED_CAST")
                val strategies = cls?.getMethod("getStrategies")?.invoke(null) as? MutableList<Any>
                strategies?.remove(p)
            } catch (e: Exception) {
                PLUGIN.logger.warning("Unable to remove BetterDamage's Valhalla strategy: ${e.message}")
            }
        }
        proxy = null
        nativeAvailable = false
    }

    fun validate() {
        val issues = mutableListOf<String>()
        if (flag("aggregation.enabled", true) && int("aggregation.window-ticks", 3) !in 2..40)
            issues += "aggregation.window-ticks must be 2–40"
        if (int("elite.high-level", 500) < 1)
            issues += "elite.high-level must be positive"
        if (flag("direct-healing.enabled", true) && int("direct-healing.max-players", 100) < 1)
            issues += "direct-healing.max-players must be positive"
        val effects = BetterDamage.inst().effectManager()
        for (name in effectNames) {
            if (effects.effect(name) == null) issues += "missing effect: $name"
        }
        // Validate every bundled/existing Valhalla glyph font before rendering it.
        val validFont = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
        for (name in listOf("valhalla_effects.yml", "valhalla_combat_effects.yml")) {
            val source = File(PLUGIN.dataFolder, "effects/$name")
            if (!source.exists()) continue
            val content = YamlConfiguration.loadConfiguration(source)
            for (entry in content.getKeys(false)) {
                val section = content.getConfigurationSection(entry) ?: continue
                val font = section.getString("glyph-font", "minecraft:default") ?: "minecraft:default"
                if (!validFont.matches(font)) issues += "invalid glyph-font $font in $name: $entry"
                if (section.getBoolean("hide-number", false) && section.getString("glyph").isNullOrBlank())
                    issues += "$name: $entry hides numbers without supplying display text"
            }
        }
        for (issue in issues) PLUGIN.logger.warning("[Valhalla indicator beta] $issue")
        if (issues.isEmpty()) PLUGIN.logger.info("Valhalla indicator effects/config validation passed.")
        PLUGIN.logger.info(
            "Valhalla combat bridge: ${if (nativeAvailable) "native final-damage strategy installed" else "Bukkit fallback only"}; " +
                "integration: ${if (integration()) "enabled" else "disabled"}"
        )
    }

    private fun valhallaLoader(): ClassLoader? =
        Bukkit.getPluginManager().getPlugin("ValhallaMMO")?.javaClass?.classLoader

    /** Keep ValhallaMMO's dummy-protection semantics, or decline registration. */
    private fun isDummy(target: LivingEntity): Boolean {
        return try {
            val loader = valhallaLoader() ?: return false
            val cls = loader.loadClass("me.athlaeos.valhallammo.entities.Dummy")
            cls.getMethod("isDummy", LivingEntity::class.java).invoke(null, target) as Boolean
        } catch (_: Exception) { false }
    }

    private fun installNativeHook() {
        if (proxy != null) return
        try {
            val loader = valhallaLoader() ?: return
            val marker = loader.loadClass("me.athlaeos.valhallammo.entities.Dummy")
            marker.getMethod("isDummy", LivingEntity::class.java) // validate dummy safety
            val iface = loader.loadClass("me.athlaeos.valhallammo.entities.damageindicators.DamageIndicatorStrategy")
            val registry = loader.loadClass("me.athlaeos.valhallammo.entities.damageindicators.DamageIndicatorRegistry")
            @Suppress("UNCHECKED_CAST")
            val strategies = registry.getMethod("getStrategies").invoke(null) as MutableList<Any>
            val handler = Proxy.newProxyInstance(loader, arrayOf(iface)) { instance, method, args ->
                when (method.name) {
                    "use" -> damage()
                    "sendDamage", "sendCriticalDamage" -> {
                        val victim = args?.getOrNull(0) as? LivingEntity
                        if (victim != null) {
                            val type = args.getOrNull(1)
                            val name = (type?.javaClass?.getMethod("getType")?.invoke(type) as? String)
                                ?.lowercase() ?: "other"
                            val raw = (args.getOrNull(2) as? Number)?.toDouble() ?: 0.0
                            val mitigated = (args.getOrNull(3) as? Number)?.toDouble() ?: 0.0
                            collect(victim, name, raw, mitigated, method.name == "sendCriticalDamage")
                            isDummy(victim)
                        } else false
                    }
                    "equals" -> instance === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(instance)
                    "toString" -> "BetterDamage-ValhallaStrategy"
                    else -> false
                }
            }
            strategies.add(0, handler)
            proxy = handler
            nativeAvailable = true
            PLUGIN.logger.info("ValhallaMMO damage strategy registered first; native duplicate indicators prevented.")
        } catch (failure: Exception) {
            nativeAvailable = false
            PLUGIN.logger.warning("ValhallaMMO native indicator hook unavailable; Bukkit fallback active: ${failure.message}")
        }
    }

    private fun installDodgeHook() {
        if (dodgeRegistered) return
        try {
            val loader = valhallaLoader() ?: return
            val klass = loader.loadClass("me.athlaeos.valhallammo.event.EntityDodgeAttackEvent")
            @Suppress("UNCHECKED_CAST")
            val eventType = klass.asSubclass(Event::class.java) as Class<out Event>
            Bukkit.getPluginManager().registerEvent(
                eventType, this, EventPriority.MONITOR,
                EventExecutor { _, event ->
                    if (!integration() || !flag("statuses.enabled", true)) return@EventExecutor
                    if (event is Cancellable && event.isCancelled) return@EventExecutor
                    val attack = event.javaClass.getMethod("getAttack").invoke(event) as? EntityDamageByEntityEvent
                        ?: return@EventExecutor
                    val victim = attack.entity as? LivingEntity ?: return@EventExecutor
                    dispatchStatus(victim, resolveSource(attack.damager), "valhalla_dodged", 1.0)
                },
                PLUGIN, true
            )
            dodgeRegistered = true
        } catch (e: Exception) {
            PLUGIN.logger.warning("Optional ValhallaMMO dodge event unavailable: ${e.message}")
        }
    }

    private fun resolveSource(raw: Entity?): Entity? =
        (raw as? Projectile)?.shooter as? Entity ?: raw

    private fun lastDamager(victim: LivingEntity): Entity? = try {
        val cls = valhallaLoader()?.loadClass("me.athlaeos.valhallammo.listeners.EntityDamagedListener")
        resolveSource(cls?.getMethod("getLastDamager", LivingEntity::class.java)?.invoke(null, victim) as? Entity)
    } catch (_: Exception) { null }

    private fun category(victim: LivingEntity): String = try {
        val loader = valhallaLoader() ?: return "other"
        val damaged = loader.loadClass("me.athlaeos.valhallammo.listeners.EntityDamagedListener")
        val cause = damaged.getMethod("getLastDamageCause", LivingEntity::class.java)
            .invoke(null, victim) as? String ?: return "other"
        val types = loader.loadClass("me.athlaeos.valhallammo.dom.CustomDamageType")
        val custom = types.getMethod("getCustomType", String::class.java).invoke(null, cause)
        val name = custom?.javaClass?.getMethod("getType")?.invoke(custom)?.toString()?.lowercase() ?: "other"
        if (name in categoryNames) name else "other"
    } catch (_: Exception) { "other" }

    private fun collect(victim: LivingEntity, category: String, raw: Double, mitigated: Double, crit: Boolean) {
        if (!damage()) return
        if (!raw.isFinite() || !mitigated.isFinite()) return
        val id = victim.uniqueId
        val attacker = lastDamager(victim)
        if (attacker is Player) recentAttack[attacker.uniqueId] = System.currentTimeMillis()
        val entry = Hit(raw.coerceAtLeast(0.0), category.takeIf { it in categoryNames } ?: "other", crit,
            mitigated, attacker, isDummy(victim))
        val active = pending[id]
        if (active != null) {
            active.hits.add(entry)
            return
        }
        if (pending.size >= 512) {
            PLUGIN.logger.warning("Combat indicator batch limit reached (512); dropping additional floating numbers.")
            return
        }
        val created = TargetBatch(victim, victim.health + victim.absorptionAmount, victim.absorptionAmount)
        created.hits.add(entry)
        pending[id] = created
        val ticks = if (flag("aggregation.enabled", true)) int("aggregation.window-ticks", 3).coerceIn(2, 40) else 2
        Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
            val batch = pending.remove(id) ?: return@Runnable
            flush(batch)
        }, ticks.toLong())
    }

    private fun flush(batch: TargetBatch) {
        if (!damage()) return
        val victim = batch.victim
        val before = batch.initialHealth
        val after = max(0.0, victim.health + victim.absorptionAmount)
        val actuallyApplied = (before - after).coerceAtLeast(0.0)
        val totalRequested = batch.hits.sumOf { it.amount }
        val factor = if (totalRequested > 0) (actuallyApplied / totalRequested).coerceIn(0.0, 1.0) else 0.0
        // One indicator per damage category/critical status/attacker, even if a
        // poison or bleed effect ticks many times in the aggregation window.
        val grouped = batch.hits.groupBy {
            Triple(it.category, it.critical, it.attacker?.uniqueId)
        }
        for (hits in grouped.values) {
            val first = hits.first()
            val amount = hits.sumOf { if (it.dummy) it.amount else it.amount * factor }
            if (amount >= 0.05) {
                val critical = first.critical && flag("critical.enabled", true)
                val key = if (critical) "valhalla_critical_${first.category}" else "valhalla_${first.category}"
                dispatch(victim, first.attacker, key, amount, false)
            }
            val reduced = hits.sumOf { (-it.mitigated).coerceAtLeast(0.0) }
            if (flag("statuses.enabled", true) && reduced > 0.05)
                dispatchStatus(victim, first.attacker, "valhalla_resisted", reduced)
        }
        val absorbed = (batch.initialAbsorption - victim.absorptionAmount).coerceAtLeast(0.0)
        if (flag("statuses.enabled", true) && absorbed > 0.05) {
            dispatchStatus(victim, batch.hits.lastOrNull()?.attacker, "valhalla_absorbed", absorbed)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onBlockedDamage(event: EntityDamageEvent) {
        if (!damage() || !flag("statuses.enabled", true)) return
        if (!event.isCancelled && event.finalDamage > 0.05) return
        val victim = event.entity as? Player ?: return
        if (!victim.isBlocking) return
        val source = (event as? EntityDamageByEntityEvent)?.damager?.let(::resolveSource)
        dispatchStatus(victim, source, "valhalla_blocked", 1.0)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFallbackDamage(event: EntityDamageEvent) {
        if (!damage() || nativeAvailable) return
        val victim = event.entity as? LivingEntity ?: return
        val source = (event as? EntityDamageByEntityEvent)?.damager?.let(::resolveSource)
        if (source is Player) recentAttack[source.uniqueId] = System.currentTimeMillis()
        // Only used if the native strategy interface isn't available; not guaranteed
        // final when ValhallaMMO uses delayed custom-health processing.
        dispatch(victim, source, "valhalla_${category(victim)}", event.finalDamage, false)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHealing(event: EntityRegainHealthEvent) {
        if (!integration() || !BetterDamage.inst().config().valhallaHealingEnabled()) return
        val target = event.entity as? LivingEntity ?: return
        val before = target.health
        val amount = event.amount.coerceAtLeast(0.0)
        healEventUntil[target.uniqueId] = System.currentTimeMillis() + 1000
        val reason = event.regainReason.name
        Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
            if (!integration()) return@Runnable
            if (!target.isValid) return@Runnable
            val actual = (target.health - before).coerceAtLeast(0.0).coerceAtMost(amount)
            if (actual < 0.05) return@Runnable
            val recent = System.currentTimeMillis() - (recentAttack[target.uniqueId] ?: 0) in 0..600L
            val key = when {
                reason == "CUSTOM" && recent && flag("direct-healing.lifesteal-heuristic", true) -> "valhalla_lifesteal"
                reason.contains("REGEN") || reason == "SATIATED" -> "valhalla_regeneration"
                else -> "valhalla_healing"
            }
            heal(target, actual, key)
            healthSnapshot[target.uniqueId] = target.health
        }, 2L)
    }

    private fun sampleDirectHealing() {
        if (!integration() || !BetterDamage.inst().config().valhallaHealingEnabled() ||
            !flag("direct-healing.enabled", true)) return
        val now = System.currentTimeMillis()
        val limit = int("direct-healing.max-players", 100).coerceIn(1, 500)
        for (player in Bukkit.getOnlinePlayers().take(limit)) {
            val id = player.uniqueId
            val prev = healthSnapshot.put(id, player.health) ?: continue
            val increased = player.health - prev
            if (increased < 0.05 || (healEventUntil[id] ?: 0) > now) continue
            val lifeSteal = flag("direct-healing.lifesteal-heuristic", true) &&
                now - (recentAttack[id] ?: 0) in 0..600L
            heal(player, increased, if (lifeSteal) "valhalla_lifesteal" else "valhalla_healing")
        }
        if (healthSnapshot.size > 750) {
            val valid = Bukkit.getOnlinePlayers().mapTo(mutableSetOf()) { it.uniqueId }
            healthSnapshot.keys.retainAll(valid)
            recentAttack.keys.retainAll(valid)
            healEventUntil.keys.retainAll(valid)
        }
    }

    private fun heal(target: LivingEntity, amount: Double, key: String) {
        if (target is Player) {
            show(target, target, amount, key, "healing", target, null)
        } else if (flag("healing.mob-healing-visible", true)) {
            val range = double("healing.mob-view-distance", 24.0).coerceIn(1.0, 128.0)
            for (viewer in target.world.players.filter { it.location.distanceSquared(target.location) <= range * range }) {
                show(viewer, target, amount, key, "healing", target, null)
            }
        }
    }

    private fun dispatchStatus(victim: LivingEntity, attacker: Entity?, effect: String, amount: Double) {
        if (flag("statuses.enabled", true)) dispatch(victim, attacker, effect, amount, true)
    }

    private fun dispatch(victim: LivingEntity, attacker: Entity?, key: String, amount: Double, status: Boolean) {
        if (!amount.isFinite() || amount <= 0.0) return
        val source = attacker as? Player
        val mode = if (source != null && victim is Player && source.uniqueId != victim.uniqueId) "pvp" else "pve"
        val elite = flag("elite.enabled", true) && (elite(victim) || (attacker is LivingEntity && elite(attacker)))
        val tier = when {
            !elite || status -> key
            level(victim) >= int("elite.high-level", 500) -> "valhalla_elite_high"
            else -> "valhalla_elite"
        }
        if (source != null) {
            val styled = if (CombatPreferences.enabled(source, "elite")) tier else key
            show(source, victim, amount, styled, if (status) "statuses" else "outgoing", victim, attacker, mode)
        }
        if (victim is Player && victim.uniqueId != source?.uniqueId) {
            val styled = if (CombatPreferences.enabled(victim, "elite")) tier else key
            show(victim, victim, amount, styled, if (status) "statuses" else "incoming", victim, attacker, mode)
        }
    }

    private fun elite(entity: LivingEntity): Boolean {
        val key = NamespacedKey.fromString("valhallaequipment:elite_mob_v1") ?: return false
        return entity.persistentDataContainer.get(key, PersistentDataType.BYTE)?.toInt() == 1
    }
    private fun level(entity: LivingEntity): Int {
        val key = NamespacedKey.fromString("valhallaequipment:mob_level_v1") ?: return -1
        return entity.persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: -1
    }

    private fun show(
        viewer: Player, target: Entity, amount: Double, effectKey: String,
        direction: String, victim: Entity, attacker: Entity?, mode: String = "pve"
    ) {
        if (!viewer.isOnline) return
        if (!flag("visibility.$direction", true) || !CombatPreferences.enabled(viewer, direction)) return
        if (direction != "healing") {
            if (!flag("visibility.$mode", true) || !CombatPreferences.enabled(viewer, mode)) return
        }
        val cfg = BetterDamage.inst().config()
        if (cfg.skipZeroDamage() && amount < 0.5 && direction != "statuses") return
        val trigger = TriggerManagerImpl.type(effectKey) ?: DamageTriggerType(effectKey)
        val better = BetterDamage.inst()
        val state = better.playerManager().player(viewer.uniqueId)?.data()
        val chosen = state?.selectedSkin()?.effectMap()?.get(trigger)
        val effect = chosen ?: cfg.defaultSkin()?.effectMap()?.get(trigger)
            ?: better.effectManager().effect(effectKey)
            ?: return
        effect.play(DamageEffectData(viewer, target, amount, CombatPreferences.enabled(viewer, "glyphs")))
    }
}
