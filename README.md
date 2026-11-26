# PromptClient

A lightweight, modular client-side mod for **Minecraft 1.21.1**, built on Fabric.

PromptClient is a clean base for client features: a module system with settings and keybinds, an animated ClickGUI in the style of macOS dark mode, a glass HUD, and restyled chat. All of it is drawn with batched 2D rendering that doesn't allocate per frame.

## Features

| Module     | Category | Default key   | What it does |
|------------|----------|---------------|--------------|
| ClickGUI   | Misc     | `Right Shift` | Opens the settings menu. **Accent** sets the highlight color everywhere. |
| Fullbright | Render   | `G`           | Lights up caves and dark areas. **Mode**: Lightmap (flat, maximum), Gamma (adjustable, keeps some shading), or NightVision. |
| HUD        | Render   | on by default | Glass watermark badge with version and FPS, plus a list of enabled modules (top right, or under the badge). |
| Chat       | Render   | on by default | Replaces the flat chat background with a rounded translucent card. New messages slide and fade in. Turn it off for vanilla chat. |

Module keybinds only fire while you're in a world with no screen open, so typing in chat won't toggle modules.

## Using the ClickGUI

Press **Right Shift** to open it. The game keeps running while it's open.

- **Left-click** a module to toggle it. **Right-click** it to expand its settings.
- **Drag** a panel by its header. It snaps to a grid and lines up with nearby panels.
- **Scroll** a panel's list with the mouse wheel.
- **Type** anywhere to filter modules. **Esc** clears the search, then closes the GUI.
- **Bind** row: click it, then press a key. **Esc** or **Delete** unbinds, and right-click also clears the binding.
- Mode settings: left-click for the next mode, right-click for the previous one.

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
├── main/resources/fabric.mod.json            # mod metadata (no Java code lives in src/main)
└── client/
    ├── java/com/promptclient/
    │   ├── PromptClient.java                 # entrypoint: registers modules, tick + HUD callbacks
    │   ├── module/                           # Module, Category, ModuleManager, impl/<category>/...
    │   ├── setting/                          # BooleanSetting, NumberSetting, ModeSetting
    │   ├── mixin/                            # ChatHudMixin, LightmapTextureManagerMixin
    │   └── ui/
    │       ├── clickgui/                     # ClickGuiScreen, panels, module rows, setting widgets
    │       ├── render/                       # RenderUtils (batched 2D primitives), Colors
    │       ├── anim/                         # frame clock and easing
    │       ├── HudRenderer.java, ChatRenderer.java, Theme.java
    └── resources/promptclient.mixins.json
```

## Adding a module

1. Create a class in `module/impl/<category>/`. Declare settings as fields; they appear in the ClickGUI automatically:

   ```java
   public final class Sprint extends Module {
       private final BooleanSetting omni = addSetting(new BooleanSetting("Omni", "Sprint in every direction.", false));

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

[`CLAUDE.md`](CLAUDE.md) documents the build commands, conventions, rendering rules and definition of done that Claude Code follows when working on this repository.
