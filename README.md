# PromptClient

A lightweight, modular client-side mod for **Minecraft 1.21.1**, built on Fabric.

PromptClient gives you a small, clean base for client features: a module system with categories, keybinds and lifecycle hooks, a minimal HUD, and Mixin-based hooks into the game.

## Features

| Module     | Category | Default key | What it does                                                 |
|------------|----------|-------------|--------------------------------------------------------------|
| Fullbright | Render   | `G`         | Renders caves and dark areas at full brightness. Press again to restore normal lighting. |

**HUD:** a `PromptClient` watermark sits in the top-left corner with the currently enabled modules listed under it. It hides when the HUD is hidden (`F1`) or the debug screen is open (`F3`).

Keybinds only fire while you're in a world with no screen open, so typing in chat won't toggle modules.

## Requirements

- Minecraft **1.21.1**
- [Fabric Loader](https://fabricmc.net/use/) **0.20.1** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1
- Java **21**

## Installing

1. Build the mod (see below) or download a jar from the repository's GitHub Actions artifacts.
2. Copy `promptclient-<version>.jar` (not the `-sources` jar) into your `.minecraft/mods` folder, next to Fabric API.
3. Launch Minecraft 1.21.1 with the Fabric profile.

## Building from source

```bash
./gradlew build        # builds build/libs/promptclient-<version>.jar
./gradlew runClient    # launches a development client with the mod loaded
```

On Windows, use `gradlew.bat` from `cmd`/PowerShell, or `./gradlew` from Git Bash.

Gradle runs on Java 21. `gradle/gradle-daemon-jvm.properties` asks for it, so Gradle uses an installed JDK 21 or downloads one automatically. Your `JAVA_HOME` only needs to be Java 17 or newer.

## Project layout

```
src/
├── main/resources/fabric.mod.json          # mod metadata (no Java code lives in src/main)
└── client/
    ├── java/com/promptclient/
    │   ├── PromptClient.java               # entrypoint: registers modules, tick + HUD callbacks
    │   ├── module/
    │   │   ├── Module.java                 # base class: name, description, category, key, lifecycle
    │   │   ├── Category.java               # RENDER, MOVEMENT, COMBAT, PLAYER, MISC
    │   │   ├── ModuleManager.java          # registry, lookups, keybind polling
    │   │   └── impl/render/Fullbright.java
    │   ├── mixin/LightmapTextureManagerMixin.java
    │   └── ui/HudRenderer.java             # watermark + enabled-module list
    └── resources/promptclient.mixins.json
```

## Adding a module

1. Create a class in `module/impl/<category>/`:

   ```java
   public final class Sprint extends Module {
       public Sprint() {
           super("Sprint", "Always sprint.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
       }

       @Override
       protected void onTick() {
           if (client().player != null) client().player.setSprinting(true);
       }
   }
   ```

2. Register it in `PromptClient#registerModules()`:

   ```java
   MODULE_MANAGER.register(new Sprint());
   ```

3. If it needs a Mixin, add the class to `com.promptclient.mixin` and list it in `promptclient.mixins.json`.

## Developing with Claude Code

[`CLAUDE.md`](CLAUDE.md) documents the build commands, conventions and definition of done that Claude Code follows when working on this repository.
