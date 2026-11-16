# Configuration Guide — BetterDamage × ValhallaMMO (Beta)

> [!WARNING]
> **BETA TESTING — NOT STABLE.** This integration is experimental. Back up your server, world, and configuration files before installing. Test on a separate Purpur 26.3 server first. The GitHub prerelease is **v2.2.0-valhalla-beta.1**; a successful compilation does not establish in-game compatibility.

This page documents the implementation in [this BetterDamage fork](https://github.com/schisophrenic/BetterDamageValhallammoIntegration). It does **not** change `ValhallaEquipment/config.yml`: all settings here are in **BetterDamage**.

## 1. Files and requirements

- `plugins/BetterDamage/config.yml` — primary switches and pack settings.
- `plugins/BetterDamage/effects/valhalla_effects.yml` — one effect per ValhallaMMO damage category, plus healing; choose glyph, font, color, duration, and animation.
- `plugins/BetterDamage/skins/default_skin.yml` — maps trigger IDs (such as `valhalla_fire`) to effect names.
- `plugins/ValhallaMMO/config.yml` — ValhallaMMO's **own** damage-indicator configuration, which is separate from BetterDamage.
- A functioning, enabled ValhallaMMO plugin and compatible BetterDamage beta JAR. An installed Minecraft resource pack is necessary for custom-font glyphs.

New effect preset files are added to existing BetterDamage plugin folders if absent; **existing user-edited files are not overwritten**. A customized `default_skin.yml` must be updated manually when you want mappings for the new triggers.

## 2. Enable or disable the integration

Edit `plugins/BetterDamage/config.yml`:

```yaml
valhalla-integration:
  enabled: true       # Enables the ValhallaMMO bridge
  damage:
    enabled: true     # Type-specific damage displays
  healing:
    enabled: true     # Healing of player entities via Bukkit regain-health events
  glyphs:
    enabled: true     # Show per-effect glyph before the damage number
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `valhalla-integration.enabled` | `false` | Master switch. It must be true **and** ValhallaMMO must be loaded for the bridge to work. |
| `valhalla-integration.damage.enabled` | `true` | Enable category-aware damage effects when the master switch is enabled. |
| `valhalla-integration.healing.enabled` | `true` | Enable the `valhalla_healing` effect for Bukkit health-regain events involving players. |
| `valhalla-integration.glyphs.enabled` | `true` | Display the configured `glyph` from each effect. Turning it off keeps the number. |

**Important:** The master switch ships **off**. Damage/healing/glyph settings are on by default but have no effect while `valhalla-integration.enabled: false`.

**Duplicate indicators:** When type-specific damage handling is active, the original BetterDamage `bukkit_damage` trigger is skipped for those hits. **ValhallaMMO's native floating indicators are not disabled automatically.** If you see two displays, adjust ValhallaMMO's own indicator settings separately.

After changing the configuration, restart your server for a clean test (or perform a compatible BetterDamage reload if available).

## 3. Damage types and effect IDs

These are the configured BetterDamage trigger and effect names; each is mapped in `skins/default_skin.yml`:

| ValhallaMMO category | BetterDamage trigger/effect | Built-in glyph | Built-in color |
| --- | --- | --- | --- |
| Fire | `valhalla_fire` | 🔥 | `#FF7440` |
| Magic | `valhalla_magic` | ✦ | `#BC75F5` |
| Projectile | `valhalla_projectile` | ➶ | `#EAA272` |
| Explosion | `valhalla_explosion` | ✹ | `#FFB74D` |
| Poison | `valhalla_poison` | ☠ | `#66CC69` |
| Falling | `valhalla_falling` | ↓ | `#EBA88B` |
| Melee | `valhalla_melee` | ⚔ | `#FF6666` |
| Bleed | `valhalla_bleed` | ◆ | `#BE4040` |
| Freezing | `valhalla_freezing` | ❄ | `#74D6FF` |
| Lightning | `valhalla_lightning` | ⚡ | `#FFF176` |
| Bludgeoning | `valhalla_bludgeoning` | ✠ | `#F28D63` |
| Radiant | `valhalla_radiant` | ✧ | `#FFF6B1` |
| Necrotic | `valhalla_necrotic` | ☽ | `#9987B9` |
| Other/unrecognized | `valhalla_other` | • | `#EEEEEE` |
| Player healing | `valhalla_healing` | + | `#70F090` |

The integration looks up ValhallaMMO's recorded custom damage cause, maps it through `CustomDamageType`, and chooses the corresponding BetterDamage effect. Unknown types use `valhalla_other`. Damage numbers attempt to use ValhallaMMO's last recorded damage amount and fall back to Bukkit's final damage.

## 4. Change glyphs, colors, fonts, and number format

Edit `plugins/BetterDamage/effects/valhalla_effects.yml`. Each `valhalla_...` entry is a separate effect. For example:

```yaml
valhalla_fire:
  image: default_image          # Image/font used for numbers
  glyph: "🔥"                   # Prefix character; can be a resource-pack glyph
  glyph-font: "minecraft:default"
  color: "#FF7440"              # Text color
  duration: 20                  # Duration in ticks
  interval: 2                   # Animation update interval in ticks
  show-global-player: false
  number-format: "#.#"
  transformation:
    position:
      x: 0
      y: 1.5sin(t / 40 * pi)
      z: 0
    rotation:
      x: 60(r - 0.5)
      y: 60(r - 0.5)
      z: 0
    scale:
      x: cos(t / 40 * pi)
      y: cos(t / 40 * pi)
      z: cos(t / 40 * pi)
```

- **`image`** — BetterDamage image/font asset for numbers. The bundled presets use `default_image`.
- **`glyph`** — icon before the number, or an empty string for no glyph.
- **`glyph-font`** — Minecraft font key used **only for the glyph**, such as `minecraft:default` or `my_pack:icons`. The number keeps the image font.
- **`color`** — hex color for the damage display, including its glyph.
- **`duration` / `interval`** — how long the display animates and its update cadence, in Minecraft ticks.
- **`number-format`** — Java DecimalFormat pattern (the default presets use `#.#`).
- **`show-global-player`** — whether BetterDamage's normal display renderer can broadcast the indicator to tracked players.
- **`transformation`** — BetterDamage position, rotation, and scale expressions over animation time.

### Example: a resource-pack glyph

```yaml
valhalla_lightning:
  image: default_image
  glyph: "\\uE101"             # Use the character itself if your YAML parser doesn't decode escapes
  glyph-font: "my_pack:icons"
  color: "#FFFF55"
  duration: 20
  interval: 2
  transformation:
    position: {x: 0, y: 1.5sin(t / 40 * pi), z: 0}
    rotation: {x: 60(r - 0.5), y: 60(r - 0.5), z: 0}
    scale: {x: cos(t / 40 * pi), y: cos(t / 40 * pi), z: cos(t / 40 * pi)}
```

Your resource pack must actually define the chosen character in the selected font. If the glyph renders as a square or is invisible, verify the font JSON, character mapping, namespace, and client pack installation. The integration does not automatically create your custom glyph assets.

## 5. Customize skins and trigger mappings

For existing installations, edit `plugins/BetterDamage/skins/default_skin.yml` and add or merge the new mappings:

```yaml
default_skin:
  skins:
    bukkit_damage: default_effect
    valhalla_fire: valhalla_fire
    valhalla_magic: valhalla_magic
    valhalla_projectile: valhalla_projectile
    valhalla_explosion: valhalla_explosion
    valhalla_poison: valhalla_poison
    valhalla_falling: valhalla_falling
    valhalla_melee: valhalla_melee
    valhalla_bleed: valhalla_bleed
    valhalla_freezing: valhalla_freezing
    valhalla_lightning: valhalla_lightning
    valhalla_bludgeoning: valhalla_bludgeoning
    valhalla_radiant: valhalla_radiant
    valhalla_necrotic: valhalla_necrotic
    valhalla_other: valhalla_other
    valhalla_healing: valhalla_healing
```

The left-hand key is a **trigger**; the right-hand key is an **effect**. You may point several triggers at the same effect, or create new effect definitions with custom glyphs/animations and point skins at those names.

The integration attempts a same-named effect as a fallback if the player's skin does not supply a matching trigger. For user-defined skins, explicitly add mappings to avoid surprises.

## 6. Healing indicators

`valhalla_healing` is a **new trigger** in this BetterDamage fork, not a feature of the original BetterDamage plugin.

- It handles Bukkit `EntityRegainHealthEvent` only if the healed entity is a **player**.
- The indicator uses the amount from the heal event, not a computed before/after health delta.
- Bukkit's heal event does not identify a separate healer; the indicator is shown to the recipient.
- It does not currently detect healing that bypasses Bukkit regain-health events, direct `setHealth` calls, or healing to non-player mobs.

To disable just healing and leave damage effects on:

```yaml
valhalla-integration:
  enabled: true
  damage:
    enabled: true
  healing:
    enabled: false
  glyphs:
    enabled: true
```

## 7. Other BetterDamage settings

These settings are in the same `plugins/BetterDamage/config.yml` file:

| Setting | Bundled default | Description |
| --- | --- | --- |
| `namespace` | `"betterdamage"` | Resource-pack namespace. |
| `pack-path` | `"BetterDamage/build"` | Generated pack location. |
| `pack-type` | `zip` | `zip` or `folder`. |
| `merge-with-external-resources` | `true` | BetterDamage's resource pack merge behavior. |
| `default-effect` | `"null"` | Default effect name, if configured. |
| `default-skin` | `"default_skin"` | Default skin selection. |
| `skip-zero-damage` | `true` | Skip very small indicator values; current trigger code skips values below 0.5. |
| `auto-save-time` | `300` | Player data auto-save period in seconds. |
| `debug` | `false` | Debug logging setting. |
| `metrics` | `true` | bStats metrics. |

## 8. Testing and troubleshooting

1. Back up your installed BetterDamage JAR/config and ValhallaMMO configs, then install the beta on a test server.
2. Enable the master switch in `plugins/BetterDamage/config.yml`. Restart and confirm both plugins load without errors.
3. Hit a mob with a player attack and check `valhalla_melee`. Try ValhallaMMO damage types such as fire/poison to check the different glyphs.
4. Receive damage as a player, then heal naturally or with an effect that fires Bukkit's heal event. Look for `valhalla_healing`.
5. Turn off just `damage.enabled`, then just `healing.enabled`, and verify each switch works independently.
6. Try a custom glyph-font only after verifying the correct resource pack is installed. Revert to `minecraft:default` if it doesn't render.
7. If two numbers show, inspect ValhallaMMO's native damage-indicator options; the bridge only suppresses BetterDamage's **own** generic duplicate.
8. If new effects appear missing, check for `valhalla_effects.yml` in the effects folder and manually merge `skins/default_skin.yml` mappings.
9. If numbers or damage types are incorrect, report a reproducible example and server console logs: ValhallaMMO's delayed damage processing may cause timing differences.

**Rollback:** Disable `valhalla-integration.enabled` to restore BetterDamage's generic behavior, or reinstall your previous backed-up JAR and configuration. Do not delete custom skin/effect files before backing them up.

### Related links

- [Beta release](https://github.com/schisophrenic/BetterDamageValhallammoIntegration/releases/tag/v2.2.0-valhalla-beta.1)
- [BetterDamage fork repository](https://github.com/schisophrenic/BetterDamageValhallammoIntegration)

---

## 9. Beta 2: Advanced combat integration (experimental)

The second beta extends the earlier damage/healing bridge with a prioritized ValhallaMMO `DamageIndicatorStrategy` adapter, post-hit health reconciliation, critical-effect variants, combat status messages, player preferences, damage aggregation, elite styles, PvP/PvE controls, and validation.

> [!CAUTION]
> The displayed applied-damage total is reconciled from health **plus absorption** after a short delay, so a hit that gets blocked or absorbed will not blindly show the raw Bukkit number. When multiple hits and other health changes happen in the same short window, exact per-type attribution may still be approximate. This is a beta, not a guarantee of perfect accounting for all ValhallaMMO mechanics.

### Settings

Append these settings to `plugins/BetterDamage/config.yml` and use `/bd reload` (or restart for initial installation):

```yaml
valhalla-integration:
  enabled: true
  damage:
    enabled: true
  healing:
    enabled: true
  glyphs:
    enabled: true

valhalla-combat:
  aggregation:
    enabled: true
    window-ticks: 3
  critical:
    enabled: true
  statuses:
    enabled: true
  elite:
    enabled: true
    high-level: 500
  visibility:
    outgoing: true
    incoming: true
    healing: true
    statuses: true
    pvp: true
    pve: true
  healing:
    mob-healing-visible: true
    mob-view-distance: 24
  direct-healing:
    enabled: true
    max-players: 100
    lifesteal-heuristic: true
```

| Setting | Behavior |
| --- | --- |
| `aggregation.enabled` | Group multiple damage ticks per mob/type/attacker within the same window, reducing visual spam. |
| `aggregation.window-ticks` | Number of server ticks to gather damage and allow ValhallaMMO's delayed health changes to finish (2–40, default 3). |
| `critical.enabled` | Use `valhalla_critical_<type>` effects on critical hits reported by the ValhallaMMO damage indicator strategy. |
| `statuses.enabled` | Enable supported combat-result indicators: blocked, dodged, resisted, absorbed. |
| `elite.enabled` | Use ValhallaEquipment's saved elite-mob marker to choose special styles when targeting an elite mob or receiving damage from one. |
| `elite.high-level` | Minimum ValhallaEquipment mob level for `valhalla_elite_high`. |
| `visibility.outgoing` | Show attacks dealt by a player. |
| `visibility.incoming` | Show attacks received by a player. |
| `visibility.healing` | Show applicable healing events and direct-health increases. |
| `visibility.statuses` | Show block/dodge/resist/absorb feedback. |
| `visibility.pvp` / `visibility.pve` | Allow indicators for the given combat mode. |
| `healing.mob-healing-visible` | Let nearby players see mob healing events. |
| `healing.mob-view-distance` | Distance in blocks for mob-healing visibility. |
| `direct-healing.enabled` | Sample online player health for increases not delivered through Bukkit regain-health events. |
| `direct-healing.max-players` | Maximum number of online players checked per sampling pass. |
| `direct-healing.lifesteal-heuristic` | Classify direct healing shortly after a player dealt damage as *probable* life steal. This is a heuristic, **not** a guaranteed ValhallaMMO life-steal event. Disable it if you prefer generic healing indicators. |

### New effect presets

The file `plugins/BetterDamage/effects/valhalla_combat_effects.yml` contains the added effects:

- `valhalla_critical_fire`, `valhalla_critical_melee`, etc. (14 variants).
- `valhalla_blocked` and `valhalla_dodged` — label-only effects via `hide-number: true`.
- `valhalla_resisted` and `valhalla_absorbed` — numeric combat-result effects.
- `valhalla_regeneration` and `valhalla_lifesteal` — distinct healing styles.
- `valhalla_elite` and `valhalla_elite_high` — level-based elite effects.

Customize these effects like the original `valhalla_*` entries: glyph, glyph font, color, duration, animation, and number formatting. **Existing effect files are not overwritten.** The new effects are included in the bundled default skin; for customized skins, add their trigger mappings manually. The bridge can also find an effect with the same name as its trigger even if your skin has no mapping.

### Player preference commands

Players can change their own visibility without changing the global server configuration:

```text
/bd combat outgoing off
/bd combat incoming on
/bd combat healing off
/bd combat statuses on
/bd combat pvp off
/bd combat pve on
/bd combat elite off
/bd combat glyphs on

/bd combatstatus
/bd combatreset
```

Changes persist in `plugins/BetterDamage/combat-preferences.yml` and survive restarts. `elite off` disables **elite styling**, not all damage against elite mobs. Player preferences cannot override globally disabled channels.

### How native integration works

When both ValhallaMMO and the bridge are enabled, BetterDamage inserts an implementation of ValhallaMMO's `DamageIndicatorStrategy` at the front of the strategy registry. This gives the fork native damage category and critical-hit information, avoids a duplicate ValhallaMMO floating number when the hook is active, and retains training-dummy damage-nullification behavior. It waits for ValhallaMMO's delayed health update, then reconciles the health + absorption change and merges close-together hits. With the bridge disabled, ValhallaMMO's normal native strategy remains available.

If the native strategy API cannot be loaded, the fork attempts a Bukkit event fallback. **This fallback is not guaranteed to show precise final ValhallaMMO custom damage**, and an indicator conflict is possible; check console messages.

**Combat result limitations:** A `DODGED` label uses ValhallaMMO's actual `EntityDodgeAttackEvent`, where available; `BLOCKED` uses a shield-blocking player and canceled/zero-damage hit; `RESISTED` depends on the mitigation amount passed by ValhallaMMO's strategy; `ABSORBED` depends on absorption-heart changes. Those results are deliberately not inferred from arbitrary canceled events or unknown custom plugin effects.

### Reload and troubleshooting

`/bd reload` refreshes the combat settings, glyph/effect definitions, and player preferences. The plugin validates installed effect names, font-key syntax, and status effects that hide numbers without a glyph. Restart the server if a new native plugin integration wasn't available when BetterDamage first loaded.

**Suggested beta tests:** verify critical melee and elemental hits; delayed ValhallaMMO damage; PvP and PvE separately; shield blocking and dodge perks; poison/bleed aggregation; capped absorption and one-shot protection; regeneration and direct healing; optional elite mob styling; each preference switch; and reload after deliberately using an invalid glyph-font key. Also test the bridge disabled to confirm original indicators remain usable.

**Rollback:** set `valhalla-integration.enabled: false` and reload/restart; if needed, restore the previous BetterDamage JAR and backed-up settings.
