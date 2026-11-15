package kr.toxicity.damage.compatibility.valhallammo

import kr.toxicity.damage.api.BetterDamage
import kr.toxicity.damage.api.data.DamageEffectData
import kr.toxicity.damage.api.trigger.DamageTriggerType
import kr.toxicity.damage.compatibility.Compatibility
import kr.toxicity.damage.manager.TriggerManagerImpl
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ValhallaMMO already computes typed damage and exposes a public damage-indicator
 * strategy. Observing it avoids guessing a type from Bukkit's general damage cause
 * (which would lose extra elemental and damage-over-time instances).
 *
 * Reflection keeps ValhallaMMO optional and avoids requiring its premium jar to build.
 */
class ValhallaMMOCompatibility : Compatibility {
    companion object {
        val TYPES = listOf(
            "fire", "magic", "projectile", "explosion", "poison", "falling",
            "melee", "bleed", "freezing", "lightning", "bludgeoning",
            "radiant", "necrotic", "other"
        )

        @Volatile var active: Boolean = false
            private set

        private const val DUPLICATE_WINDOW_NANOS = 200_000_000L
        private val forwarded = ConcurrentHashMap<UUID, Long>()

        /** Suppress the generic Bukkit text for an instance already handled by Valhalla. */
        fun wasForwarded(target: UUID): Boolean {
            if (!active) return false
            val last = forwarded[target] ?: return false
            if (System.nanoTime() - last <= DUPLICATE_WINDOW_NANOS) return true
            forwarded.remove(target, last)
            return false
        }
    }

    override fun start() {
        val strategyType = Class.forName("me.athlaeos.valhallammo.entities.damageindicators.DamageIndicatorStrategy")
        val registryType = Class.forName("me.athlaeos.valhallammo.entities.damageindicators.DamageIndicatorRegistry")
        val damageType = Class.forName("me.athlaeos.valhallammo.dom.CustomDamageType")
        val listenerType = Class.forName("me.athlaeos.valhallammo.listeners.EntityDamagedListener")
        val dummyType = Class.forName("me.athlaeos.valhallammo.entities.Dummy")

        val getName = damageType.getMethod("getType")
        val getLastDamager = listenerType.getMethod("getLastDamager", LivingEntity::class.java)
        val isDummy = dummyType.getMethod("isDummy", LivingEntity::class.java)

        @Suppress("UNCHECKED_CAST")
        val strategies = registryType.getMethod("getStrategies").invoke(null) as MutableList<Any>

        TYPES.forEach { TriggerManagerImpl.registerType(DamageTriggerType("valhallammo_$it")) }

        val strategy = Proxy.newProxyInstance(
            strategyType.classLoader,
            arrayOf(strategyType)
        ) { proxy, method, args ->
            when (method.name) {
                "use" -> {
                    val target = args?.getOrNull(0) as? LivingEntity
                    target != null && findViewer(target, getLastDamager) != null
                }
                "sendDamage", "sendCriticalDamage" -> {
                    val target = args?.getOrNull(0) as? LivingEntity
                    if (target == null) false
                    else {
                        val typeName = getName.invoke(args?.getOrNull(1)) as? String ?: "OTHER"
                        val name = typeName.lowercase(Locale.ROOT).takeIf { it in TYPES } ?: "other"
                        val amount = (args?.getOrNull(2) as Number).toDouble()
                        val viewer = findViewer(target, getLastDamager)
                        if (viewer != null && amount.isFinite() && amount > 0.0) {
                            val effect = BetterDamage.inst().playerManager().player(viewer.uniqueId)
                                ?.data()?.effect(DamageTriggerType("valhallammo_$name"))
                            if (effect != null && (!BetterDamage.inst().config().skipZeroDamage() || amount >= 0.5)) {
                                effect.play(DamageEffectData(viewer, target, amount))
                                forwarded[target.uniqueId] = System.nanoTime()
                            }
                        }
                        // The native indicator cancels damage to training dummies.
                        // Preserve that behavior when taking its place.
                        isDummy.invoke(null, target) == true
                    }
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "BetterDamage-ValhallaMMO strategy"
                else -> null
            }
        }
        // ValhallaMMO chooses its FIRST applicable strategy. Our renderer must run
        // before its default text display or the callback is never received.
        strategies.add(0, strategy)
        active = true
    }

    private fun findViewer(target: LivingEntity, getLastDamager: Method): Player? {
        val damager = runCatching { getLastDamager.invoke(null, target) as? Entity }.getOrNull()
        val attacker = when (damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        }
        val candidates = listOfNotNull(attacker) + target.trackedBy.toList() + listOfNotNull(target as? Player)
        val manager = BetterDamage.inst().playerManager()
        return candidates.distinctBy { it.uniqueId }
            .firstOrNull { it.isOnline && manager.player(it.uniqueId) != null }
    }
}
