package kr.toxicity.damage.api.data;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Damage effect data
 * @param player caster
 * @param entity target
 * @param damage damage
 */
public record DamageEffectData(@NotNull Player player, @NotNull Entity entity, double damage, boolean useGlyphs) {
    /** Original constructor retained for BetterDamage API compatibility. */
    public DamageEffectData(@NotNull Player player, @NotNull Entity entity, double damage) {
        this(player, entity, damage, true);
    }
}
