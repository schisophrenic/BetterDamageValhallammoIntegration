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
