package kr.toxicity.damage.manager

import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.manager.TriggerManager
import kr.toxicity.damage.api.pack.PackAssets
import kr.toxicity.damage.api.trigger.DamageTrigger
import kr.toxicity.damage.api.trigger.DamageTriggerType
import kr.toxicity.damage.compatibility.valhallammo.ValhallaMMOCompatibility
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent

object TriggerManagerImpl : TriggerManager {

    private val triggerMap = hashMapOf<String, DamageTriggerType>()

    override fun type(name: String): DamageTriggerType? = triggerMap[name]

    fun registerType(type: DamageTriggerType) {
        triggerMap.putIfAbsent(type.name, type)
    }

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
                if (ValhallaMMOCompatibility.wasForwarded(it.entity.uniqueId)) return@mapper null
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
    }

    override fun reload(assets: PackAssets) {
    }
}