package kr.toxicity.damage.manager

import kr.toxicity.damage.api.BetterDamage
import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.manager.TriggerManager
import kr.toxicity.damage.api.pack.PackAssets
import kr.toxicity.damage.api.trigger.DamageTrigger
import kr.toxicity.damage.api.trigger.DamageTriggerType
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent

object TriggerManagerImpl : TriggerManager {
    private val triggerMap = hashMapOf<String, DamageTriggerType>()
    private val damageCategories = listOf(
        "fire", "magic", "projectile", "explosion", "poison", "falling",
        "melee", "bleed", "freezing", "lightning", "bludgeoning",
        "radiant", "necrotic", "other"
    )
    private val extras = listOf(
        "healing", "regeneration", "lifesteal", "blocked", "dodged",
        "resisted", "absorbed", "critical", "elite", "elite_high"
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

    override fun start() {
        addTrigger(DamageTrigger.builder(EntityDamageByEntityEvent::class.java)
            .type(DamageTriggerType("bukkit_damage"))
            .mapper {
                val cfg = BetterDamage.inst().config()
                if (cfg.valhallaEnabled() && cfg.valhallaDamageEnabled() &&
                    org.bukkit.Bukkit.getPluginManager().isPluginEnabled("ValhallaMMO"))
                    return@mapper null // The Valhalla bridge owns its own indicators.
                DamageEffectData(
                    when (val attacker = it.damager) {
                        is Player -> attacker
                        is Projectile -> attacker.shooter as? Player ?: return@mapper null
                        else -> return@mapper null
                    },
                    it.entity, it.damage
                )
            }
            .build())
        for (name in damageCategories + extras + damageCategories.map { "critical_$it" }) {
            val key = "valhalla_$name"
            triggerMap.putIfAbsent(key, DamageTriggerType(key))
        }
        CombatIndicatorBridge.start()
    }

    override fun reload(assets: PackAssets) {
        CombatIndicatorBridge.reload()
    }

    override fun end() {
        CombatIndicatorBridge.end()
    }
}
