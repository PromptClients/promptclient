# PromptClient: guide for Claude Code

PromptClient is a client-only Fabric mod for Minecraft 1.21.1. It is a modular base: features are `Module`s registered in one place, with Mixins as thin hooks into the game. This file is the source of truth for how to build, extend and ship it.

## Pinned stack

All versions live in `gradle.properties`.

| Component     | Version                                         |
|---------------|-------------------------------------------------|
| Minecraft     | 1.21.1                                          |
| Mappings      | **Yarn** `1.21.1+build.3` (not Mojang mappings) |
| Fabric Loader | 0.20.1                                          |
| Fabric API    | 0.116.18+1.21.1                                 |
| Loom          | 1.17.21, plugin id `net.fabricmc.fabric-loom-remap` |
| Gradle        | 9.8.2 (wrapper)                                 |
| Java          | 21                                              |

- **Don't bump Loom to 1.19+.** It needs Java 25 to run Gradle, and 1.17.x is the newest line that runs on Java 21.
- **Keep the plugin id `net.fabricmc.fabric-loom-remap`.** It is the Loom variant for obfuscated versions with Yarn remapping, and it is what Fabric's official 1.21.1 template uses.
- Fabric API for 1.21.1 is effectively final. Check `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` before bumping it.

## Commands

Run these from the repo root with the **Bash tool**; `./gradlew` is the POSIX wrapper.

| Command | Purpose |
|---------|---------|
| `./gradlew compileClientJava` | Fast compile check of all mod code. Use it while iterating. |
| `./gradlew build` | Full build with remapping. Produces `build/libs/promptclient-<version>.jar`. **Required before finishing.** |
| `./gradlew runClient` | Launches a dev client. It opens a game window and never exits on its own (see "Runtime smoke test"). |
| `./gradlew genSources` | Decompiles Minecraft with Yarn names, for reading vanilla code. |

**JDK:** on this machine `JAVA_HOME` is Java 17, and that is fine. `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to Adoptium 21. Gradle picks up the JDK in `~/.gradle/jdks/` or downloads one through foojay. Don't set `org.gradle.java.home` and don't edit that file by hand; regenerate it with `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium`.

## Layout

```
src/main/resources/fabric.mod.json      metadata only. NO Java in src/main.
src/client/java/com/promptclient/
  PromptClient.java                     ClientModInitializer: registerModules(), tick + HUD callbacks
  module/Module.java                    abstract base (name, description, category, keyCode, enabled)
  module/Category.java                  RENDER, MOVEMENT, COMBAT, PLAYER, MISC
  module/ModuleManager.java             registry, lookups, keybind polling, per-tick dispatch
  module/impl/<category>/<Name>.java    one class per module
  mixin/<Target>Mixin.java              Mixins (client side)
  ui/HudRenderer.java                   HudRenderCallback: watermark (6,6) + enabled-module list
src/client/resources/promptclient.mixins.json
```

## Architecture rules

1. **All code goes in `src/client/`.** Loom uses `splitEnvironmentSourceSets()`, so `src/main` can't see `net.minecraft.client.*`. `src/main` holds only `fabric.mod.json`. The mod is `"environment": "client"`.
2. **Adding a module:**
   - Create `module/impl/<category-lowercase>/<Name>.java` as a `public final class <Name> extends Module`.
   - Call `super(name, description, Category.X, GLFW.GLFW_KEY_*)`, or use the three-argument constructor for no keybind.
   - Override only the hooks you need:
     - `onEnable()`
     - `onDisable()`: it must undo every side effect of `onEnable()` or `onTick()`.
     - `onTick()`: runs on `END_CLIENT_TICK` while the module is enabled.
   - Use `client()` to get the `MinecraftClient`. Null-check `client().player` and `client().world`.
   - Register it with one line in `PromptClient#registerModules()`, under the comment for its category. This is the only registration point.
3. **Keybinds** are raw GLFW key codes, polled by `ModuleManager.handleKeybinds()` and edge-triggered. They only fire in a world with no screen open. Don't register vanilla `KeyBinding`s for toggling modules.
4. **Categories:** use an existing `Category`. Add a new enum constant only when no existing one fits, and give it a matching `impl/` subpackage.
5. **Mixins:**
   - Put them in `com.promptclient.mixin`, named `<TargetClass>Mixin`, as abstract classes.
   - **Add the simple class name to the `"client"` array in `src/client/resources/promptclient.mixins.json`.** A Mixin that isn't listed there silently does nothing.
   - Prefix handler methods with `promptclient$`.
   - Prefer `@Inject`, `@ModifyArg`, `@ModifyVariable`, or MixinExtras `@WrapOperation` / `@ModifyExpressionValue` (bundled with Fabric Loader). Avoid `@Redirect` and `@Overwrite`, because they conflict with other mods.
   - Keep Mixins thin. Read state through `PromptClient.getModuleManager().isEnabled(X.class)` or `.get(X.class)`, and keep the logic in the module. `ModuleManager` is created eagerly, so this is null-safe at any time.
   - `injectors.defaultRequire = 1`, so a target that doesn't match **crashes at startup**. That is deliberate: fail fast.
   - **There is no refmap, and that is expected.** Loom 1.17 remaps Mixin annotation strings directly inside `remapJar`. Don't add a `"refmap"` key.
6. **Rendering:** use `DrawContext` inside `HudRenderCallback`. In 1.21.1 the callback gets a `RenderTickCounter`, not a `float`. Add HUD elements in `ui/`.
7. Use `PromptClient.LOGGER` for logging. Don't use `System.out`.

## Finding Yarn names (1.21.1)

Never guess a mapping. Check the Yarn-mapped Minecraft jar:

```bash
JDK21=$(ls -d ~/.gradle/jdks/*21*/ | head -1)
JAR=$(find .gradle/loom-cache/minecraftMaven -name 'minecraft-clientOnly-*.jar' ! -name '*-sources.jar' | head -1)
"$JDK21/bin/javap" -p -cp "$JAR" net.minecraft.client.render.LightmapTextureManager      # members
"$JDK21/bin/javap" -c -p -cp "$JAR" net.minecraft.client.render.LightmapTextureManager   # bytecode: exact INVOKE targets for @At
```

For readable source, run `./gradlew genSources` and read the `*-sources.jar` next to that jar with `unzip -p`. Mixin `@At(target = ...)` strings use Yarn names in JVM descriptor form, for example `Lnet/minecraft/client/texture/NativeImage;setColor(III)V`.

Yarn reminders: `MinecraftClient`, `ClientPlayerEntity`, `ClientWorld`, `DrawContext`, `TextRenderer`, `Text`, `InputUtil`, `GameOptions`, `SimpleOption`. In 1.21.1 the lightmap is computed on the CPU in `LightmapTextureManager#update(float)` and rebuilt every tick. Fullbright depends on that, so re-verify it before porting to another Minecraft version.

## Runtime smoke test

Run this when a change touches Mixins, startup or HUD code:

1. Start `./gradlew runClient > <scratchpad>/runclient.log 2>&1` in the background.
2. Wait for either `Sound engine started` or a failure marker: `InvalidInjectionException`, `MixinApplyError`, `crash-reports`, `BUILD FAILED`, or `Incompatible mods`.
3. Check that the log contains `PromptClient initialized with N module(s)` and no `/ERROR]` lines.
4. Stop the task, then **kill the leftover game JVM**, because it outlives the Gradle task. It is the `java.exe` whose command line contains `fabric.dli`:
   `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ? { $_.CommandLine -match 'fabric.dli' } | % { Stop-Process -Id $_.ProcessId -Force }`

These messages are known and harmless; don't chase them:
- `duplicate input class net/fabricmc/fabric/mixin/tag/package-info` during configuration. It comes from two Fabric API jars.
- `Class path entries reference missing files: ...build\classes\java\main`. `src/main` has no Java.
- Vanilla warnings such as `Missing sound for event: minecraft:item.goat_horn.play` and `could not find sampler named Sampler2`.

## Git

- Remote: `origin` → `https://github.com/PromptClients/promptclient.git`. Work on `main`.
- Commit identity is set in the repo-local config: `PromptClients <promptclients@users.noreply.github.com>`. If `git config user.name` is empty (for example on a fresh clone), set those two values locally with `git config`, never `--global`. Don't use any personal email.
- Use small, focused commits with imperative messages, for example `Add Sprint module`.
- Never force-push and never rewrite published history. If a push is rejected, run `git pull --rebase origin main`, rebuild, then push.
- Never commit `build/`, `run/`, `.gradle/`, IDE files or `.claude/settings.local.json` (all are covered by `.gitignore`).
- CI (`.github/workflows/build.yml`) runs `./gradlew build` on every push.

## Definition of Done

A feature, module or fix is done only when all of these hold:

- [ ] The code is under `src/client/` and follows the architecture rules above.
- [ ] New modules are registered in `PromptClient#registerModules()`, and new Mixins are listed in `promptclient.mixins.json`.
- [ ] Every Yarn name and Mixin target was checked against the mapped jar, not guessed.
- [ ] `./gradlew build` ends with **BUILD SUCCESSFUL**. Don't finish on a failing or skipped build.
- [ ] If Mixins, startup or HUD code changed, the runtime smoke test passes.
- [ ] `README.md` is updated: the features table for module changes, and any changed commands.
- [ ] The change is committed and pushed: `git push origin main` succeeded, and `git status` is clean.
