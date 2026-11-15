package kr.toxicity.damage.manager

import kr.toxicity.damage.api.BetterDamage
import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.manager.TriggerManager
import kr.toxicity.damage.api.pack.PackAssets
import kr.toxicity.damage.api.trigger.DamageTrigger
import kr.toxicity.damage.api.trigger.DamageTriggerType
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * The ValhallaMMO indicators use BetterDamage's original effects/skins.
 * Damage categories come from ValhallaMMO's CustomDamageType registry, not
 * the Bukkit damage cause, so bleed, radiant, necrotic, etc stay distinct.
 */
object TriggerManagerImpl : TriggerManager {
    private val triggerMap = hashMapOf<String, DamageTriggerType>()
    private val categories = listOf(
        "fire", "magic", "projectile", "explosion", "poison", "falling",
        "melee", "bleed", "freezing", "lightning", "bludgeoning",
        "radiant", "necrotic", "other"
    )
    override fun type(name: String): DamageTriggerType? = triggerMap[name]

    override fun addTrigger(trigger: DamageTrigger<*>) {
        trigger.type().run {
            triggerMap.computeIfAbsent(name) {
                trigger.register()
                this
            }
        }
    }

    private fun valhallaAvailable(): Boolean =
        BetterDamage.inst().config().valhallaEnabled() &&
            Bukkit.getPluginManager().isPluginEnabled("ValhallaMMO")

    private fun caster(event: EntityDamageEvent): Player? {
        if (event is EntityDamageByEntityEvent) {
            return when (val attacker = event.damager) {
                is Player -> attacker
                is Projectile -> attacker.shooter as? Player
                else -> null
            }
        }
        return event.entity as? Player
    }

    /** Native ValhallaMMO exposes the last custom damage cause and amount.
     * Reflection ensures BetterDamage still starts when ValhallaMMO isn't installed.
     */
    private fun valhallaCause(target: LivingEntity): String {
        return runCatching {
            val plugin = Bukkit.getPluginManager().getPlugin("ValhallaMMO") ?: return "OTHER"
            val loader = plugin.javaClass.classLoader
            val damaged = loader.loadClass("me.athlaeos.valhallammo.listeners.EntityDamagedListener")
            val raw = damaged.getMethod("getLastDamageCause", LivingEntity::class.java)
                .invoke(null, target) as? String ?: return "OTHER"
            val types = loader.loadClass("me.athlaeos.valhallammo.dom.CustomDamageType")
            val registered = types.getMethod("getCustomType", String::class.java).invoke(null, raw)
            (registered?.javaClass?.getMethod("getType")?.invoke(registered) as? String ?: "OTHER")
                .lowercase(java.util.Locale.ROOT)
        }.getOrDefault("other").let { if (it in categories) it else "other" }
    }

    private fun valhallaDamage(target: LivingEntity, fallback: Double): Double =
        runCatching {
            val plugin = Bukkit.getPluginManager().getPlugin("ValhallaMMO") ?: return fallback
            val clazz = plugin.javaClass.classLoader
                .loadClass("me.athlaeos.valhallammo.listeners.EntityDamagedListener")
            val method = clazz.getMethod("getLastDamageTaken", java.util.UUID::class.java, java.lang.Double.TYPE)
            (method.invoke(null, target.uniqueId, fallback) as Number).toDouble()
        }.getOrDefault(fallback)

    private fun show(player: Player, target: org.bukkit.entity.Entity, amount: Double, key: String) {
        if (!amount.isFinite() || amount <= 0.0) return
        val trigger = triggerMap[key] ?: return
        val better = BetterDamage.inst()
        if (better.config().skipZeroDamage() && amount < 0.5) return
        val effect = better.playerManager().player(player.uniqueId)?.data()?.effect(trigger)
            ?: better.effectManager().effect(key)
            ?: return
        effect.play(DamageEffectData(player, target, amount))
    }

    override fun start() {
        // The old generic indicator stays active if ValhallaMMO integration is off.
        addTrigger(DamageTrigger.builder(EntityDamageByEntityEvent::class.java)
            .type(DamageTriggerType("bukkit_damage"))
            .mapper {
                if (valhallaAvailable() && BetterDamage.inst().config().valhallaDamageEnabled())
                    return@mapper null // Prevent duplicate damage numbers
                DamageEffectData(
                    when (val attacker = it.damager) {
                        is Player -> attacker
                        is Projectile -> attacker.shooter as? Player ?: return@mapper null
                        else -> return@mapper null
                    },
                    it.entity,
                    it.damage
                )
            }
            .build())

        // Register synthetic types for BetterDamage skins; one shared listener
        // decides which is appropriate for each combat/heal event.
        for (category in categories) {
            val key = "valhalla_$category"
            triggerMap.putIfAbsent(key, DamageTriggerType(key))
        }
        triggerMap.putIfAbsent("valhalla_healing", DamageTriggerType("valhalla_healing"))

        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onDamage(event: EntityDamageEvent) {
                val cfg = BetterDamage.inst().config()
                if (!valhallaAvailable() || !cfg.valhallaDamageEnabled()) return
                val entity = event.entity as? LivingEntity ?: return
                val player = caster(event) ?: return
                val category = valhallaCause(entity)
                val amount = valhallaDamage(entity, event.finalDamage)
                show(player, entity, amount, "valhalla_$category")
            }

            // Bukkit healing events don't expose a healer. Self/ambient
            // player healing is shown to the recipient only.
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onHeal(event: EntityRegainHealthEvent) {
                val cfg = BetterDamage.inst().config()
                if (!valhallaAvailable() || !cfg.valhallaHealingEnabled()) return
                val patient = event.entity as? Player ?: return
                show(patient, patient, event.amount, "valhalla_healing")
            }
        }, BetterDamage.inst() as JavaPlugin)
    }

    override fun reload(assets: PackAssets) {}
}
