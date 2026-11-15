<div align="center">  

![0](https://github.com/user-attachments/assets/b72c6d7c-d614-4b88-975d-44beb3997570)

Welcome to BetterDamage!

[SpigotMC](https://www.spigotmc.org/resources/123850/) | [GitHub](https://github.com/toxicity188/BetterDamage) | [Wiki](https://github.com/toxicity188/BetterDamage/wiki)  

[![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.com/invite/rePyFESDbk)
[![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/toxicity188/BetterDamage?style=for-the-badge&logo=github)](https://github.com/toxicity188/BetterDamage/issues)
[![bStats Servers](https://img.shields.io/bstats/servers/24961?style=for-the-badge&logo=minecraft&label=bStats&color=0%2C150%2C136%2C0)](https://bstats.org/plugin/bukkit/BetterDamage/24961)

</div>

![](https://i.imgur.com/BCoo007.gif)  

### Provides simple damage skin for Minecraft Bukkit.
This plugin implements damage skin.
- Auto resource pack build
- Custom skin per user
- Multi trigger support

### Supported Plugins
- BetterModel, ModelEngine - detect model's height  
- MythicLib - support damage event  

### Platform
- Spigot, Paper and Folia or fork of them.

### Version
- 1.20.5-26.1.2

### Build
./gradlew build

### API
[![](https://jitpack.io/v/toxicity188/BetterDamage.svg)](https://jitpack.io/#toxicity188/BetterDamage)
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies { 
    implementation("com.github.toxicity188:BetterDamage:Tag")
}
```

## Documentation

- 📖 **[ValhallaMMO configuration guide](docs/wiki/Configuration.md)** — complete options, skins, glyph fonts, healing indicators, examples, troubleshooting, and beta testing instructions.
- [Wiki-style guide index](docs/wiki/Home.md)

## ValhallaMMO integration (optional, experimental)

The fork supports ValhallaMMO custom damage categories through BetterDamage's existing renderer and selectable skins, without a hard compile dependency on ValhallaMMO. The bridge reads `CustomDamageType.getCustomType(EntityDamagedListener.getLastDamageCause(victim))` and ValhallaMMO's `getLastDamageTaken` with the Bukkit final-damage fallback. When enabled it suppresses BetterDamage's generic `bukkit_damage` hit trigger for the same event to avoid duplicate **BetterDamage** indicators. It does not automatically suppress ValhallaMMO's **own** native indicators; configure those separately (for example `dummies_only: true` in ValhallaMMO's main config if appropriate).

Set in `plugins/BetterDamage/config.yml` and reload BetterDamage:

```yaml
valhalla-integration:
  enabled: true
  damage:
    enabled: true
  healing:
    enabled: true
  glyphs:
    enabled: true
```

Configured damage categories are `fire`, `magic`, `projectile`, `explosion`, `poison`, `falling`, `melee`, `bleed`, `freezing`, `lightning`, `bludgeoning`, `radiant`, `necrotic`, and `other`. Each has a distinct `valhalla_<type>` effect and trigger. This fork additionally registers `valhalla_healing` for Bukkit `EntityRegainHealthEvent` **when the healed entity is a player**. The Bukkit healing event does not identify a separate healer, so mob healing by players and direct `setHealth` calls are not yet included.

Every new effect is in `plugins/BetterDamage/effects/valhalla_effects.yml`. Example:

```yaml
valhalla_fire:
  image: default_image
  glyph: "🔥"
  glyph-font: "minecraft:default"
  color: "#FF7440"
  duration: 20
  interval: 2
  transformation: ...
```

For resource-pack glyphs, change `glyph` to your desired character (including Unicode private-use code points) and `glyph-font` to the font ID from your installed resource pack, e.g. `my_pack:icons`. The glyph uses its own font; digits continue to use BetterDamage's numeric font. Custom glyphs require a correctly merged and installed client resource pack.

The default skin maps all `valhalla_*` triggers to these effects. Existing user-edited `default_skin.yml` files are preserved: manually merge the new mappings, or create a separate skin. For skins that do not specify the mappings, the integration tries the corresponding named effect as a fallback. New effect preset files are copied into existing BetterDamage folders without overwriting user edits.

This is **not** a claim that native custom damage is reproduced exactly in every case. It needs server testing on Purpur 26.3, especially ValhallaMMO's delayed health updates, non-player damage sources, crit styling, and interactions with existing native damage indicators. All new switches default OFF until explicitly enabled. When disabled, vanilla BetterDamage behavior remains unchanged.
