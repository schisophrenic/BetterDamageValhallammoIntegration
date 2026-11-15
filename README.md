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
- ValhallaMMO - displays each calculated damage type with its own colored, pixel-art icon  

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


### ValhallaMMO damage types

If ValhallaMMO is installed, BetterDamage hooks into ValhallaMMO's damage-indicator
strategy to read its **actual calculated damage type and amount**, including
independent bonus elemental hits. No changes to ValhallaMMO's damage calculations
or combat balancing are made.

Built-in visuals cover: Fire, Magic, Projectile, Explosion, Poison, Falling,
Melee, Bleeding, Freezing, Lightning, Bludgeoning, Radiant, Necrotic, and Other.
Each damage type uses its own 16x16 icon in the generated BetterDamage resource pack.
You must deliver/merge that pack with the other resource packs used by your server.

The files `plugins/BetterDamage/effects/valhallammo_effects.yml` and
`plugins/BetterDamage/images/valhallammo_icons.yml` are installed automatically
if missing, even when upgrading an existing server. Existing customizations are
never overwritten. All skins gain default ValhallaMMO type mappings unless they
specify their own `valhallammo_<type>` trigger override.

To customize an icon, edit the matching `valhallammo_<type>` effect's
`icon`, `icon-image`, `icon-color`, or damage `color` fields and reload
BetterDamage. To disable a specific Valhalla indicator, remove its effect or
configure an appropriate fallback in your skin. When the hook is unavailable,
BetterDamage continues using its original Bukkit damage trigger.

**Duplicate indicators:** when a Valhalla damage instance was rendered,
the generic Bukkit trigger is suppressed for that target's immediate damage
event. This also preserves the native indicator fallback for unhandled events.

**Notes:** This integration uses ValhallaMMO's public damage-indicator
interfaces via reflection so the build does not require a premium ValhallaMMO
dependency. It needs in-server testing on Purpur 26.3, particularly for custom
damage ticks, dummies and existing custom skins.
