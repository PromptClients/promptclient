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
  module/Module.java                    abstract base: name, description, category, keyCode, enabled, settings
  module/Category.java                  RENDER, MOVEMENT, COMBAT, PLAYER, MISC
  module/ModuleManager.java             registry, lookups, keybind polling, per-tick dispatch
  module/impl/<category>/<Name>.java    one class per module (combat/: KillAura; render/: Fullbright, Hud, Chat; misc/: ClickGui)
  setting/                              Setting (base), BooleanSetting, NumberSetting, ModeSetting
  util/RotationManager.java             server-rotation controller: silent rotations, GCD, MoveFix, server raycasts
  mixin/<Target>Mixin.java              Mixins (client side): ChatHudMixin, ClientPlayerEntityMixin, KeyboardInputMixin, LightmapTextureManagerMixin, MinecraftClientMixin
  ui/Theme.java                         palette + Theme.accent() (from the ClickGUI module's Accent setting)
  ui/HudRenderer.java                   HudRenderCallback: glass watermark badge (8,8) + animated module list
  ui/ChatRenderer.java                  chat card drawing + new-message slide/fade (called by ChatHudMixin)
  ui/render/RenderUtils.java            batched 2D primitives: rounded rects, rings, shadows, lines, text, scissor
  ui/render/Colors.java                 packed-ARGB helpers (lerp, alpha) instead of java.awt.Color
  ui/anim/Anim.java, FrameClock.java    exponential approach + easing; real frame delta (capped at 0.05 s)
  ui/clickgui/ClickGuiScreen.java       the Screen: open/close animation, input, z-order, hint footer
  ui/clickgui/CategoryPanel.java        draggable card per Category with clipped, scrolling module list
  ui/clickgui/ModuleEntry.java          module row + settings accordion
  ui/clickgui/*Component.java           one widget per Setting type, plus KeybindComponent
  ui/clickgui/SearchBar.java, GuiFrame.java
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
   - Register it with one line in `PromptClient#registerModules()`, under the comment for its category. This is the only registration point. For an on-by-default module, chain `.setEnabled(true)` on that line.
   - Override `isVisibleInArrayList()` to return `false` for always-on cosmetic modules (HUD, Chat and ClickGUI do this).
   - The ClickGUI picks up every registered module and its settings automatically. There is no GUI code to write for a new module.
3. **Settings** (`setting/`): declare them as fields with `addSetting(...)`, in the order they should appear:
   ```java
   private final ModeSetting mode = addSetting(new ModeSetting("Mode", "How it works.", "Fast", "Fast", "Smooth"));
   private final NumberSetting speed = addSetting(new NumberSetting("Speed", "Blocks per tick.", 1.0, 0.1, 5.0, 0.1)
           .visibleWhen(() -> mode.getIndex() == 1));
   ```
   - `visibleWhen` runs every frame, so keep it trivial. Compare `getIndex()` rather than strings on hot paths.
   - Keep descriptions short (one line): they appear in the hint footer.
   - **Width budget:** a settings row is ~104 px wide, shared by the label and its control (mode pill, key pill, value). Keep the label plus the longest mode name under ~90 px (roughly 15 characters in total), or the control overflows.
   - A new Setting type needs a matching `*Component` in `ui/clickgui/` and a case in `Component.of(...)`.
4. **Keybinds** are raw GLFW key codes, polled by `ModuleManager.handleKeybinds()` and edge-triggered. They only fire in a world with no screen open. Don't register vanilla `KeyBinding`s for toggling modules. Users rebind them in the ClickGUI ("Bind" row: Esc or Delete unbinds).
5. **Categories:** use an existing `Category`. Add a new enum constant only when no existing one fits, and give it a matching `impl/` subpackage. The ClickGUI shows one panel per constant.
6. **Mixins:**
   - Put them in `com.promptclient.mixin`, named `<TargetClass>Mixin`, as abstract classes.
   - **Add the simple class name to the `"client"` array in `src/client/resources/promptclient.mixins.json`.** A Mixin that isn't listed there silently does nothing.
   - Prefix handler methods with `promptclient$`.
   - Prefer `@Inject`, `@ModifyArg`, `@ModifyVariable`, or MixinExtras `@ModifyExpressionValue` / `@WrapWithCondition` / `@WrapOperation` (MixinExtras 0.5.5 is bundled with Fabric Loader). Avoid `@Redirect` and `@Overwrite`, because they conflict with other mods.
   - On per-frame paths, prefer `@WrapWithCondition` (skip a void call) or `@ModifyArg` over `@WrapOperation`, which creates an `Operation` object per call. Use `@Local Type name` to capture a local by type when that type is unique in the method (`ChatHudMixin` does this for `ChatHudLine.Visible`).
   - When targeting by `ordinal`, record in the Mixin's Javadoc what each ordinal is, as verified with `javap -c`. A wrong ordinal doesn't crash; it silently hooks the wrong call.
   - Keep Mixins thin. Read state through `PromptClient.getModuleManager().isEnabled(X.class)` or `.get(X.class)`, and put the logic in the module or a `ui/` class. `isEnabled` is null-safe at any time; `get` is safe once `onInitializeClient` has run (always true for render and tick code). Mark helper methods and fields `@Unique`.
   - `injectors.defaultRequire = 1`, so a target that doesn't match **crashes at startup**. That is deliberate: fail fast.
   - **There is no refmap, and that is expected.** Loom 1.17 remaps Mixin annotation strings directly inside `remapJar`. Don't add a `"refmap"` key.
7. **Rendering:** see "Rendering and performance rules" below. In 1.21.1 `HudRenderCallback` gets a `RenderTickCounter`, not a `float`. HUD and GUI code goes in `ui/`.
8. Use `PromptClient.LOGGER` for logging. Don't use `System.out`.

## Combat and rotations

`util/RotationManager` owns every server-facing rotation. Modules call `configure(...)` and `aimAt(...)` each tick, then `tick()`; never write `yaw`/`pitch` around a packet by hand.

- **Silent rotations:** `ClientPlayerEntityMixin` injects at `HEAD`/`RETURN` of `sendMovementPackets`. HEAD runs the optional pre-movement task (attack-before-packet ordering), then pushes the spoofed yaw/pitch; RETURN restores the visible camera values. Never keep spoofed values in place after the call.
- **Third-person model:** `RotationManager#tick` also eases `renderYaw`/`renderPitch` (and their `last*` fields) so the F5 body follows the server rotation without moving the first-person camera. On release, vanilla `tickNewAi` eases them back.
- **GCD:** all deltas are snapped to the vanilla mouse step `(sens * 0.6 + 0.2)^3 * 1.2`. Remainders carry between ticks so slow turns still advance and every packet delta stays a multiple of the step. Keep `options.getMouseSensitivity()` as the only source.
- **MoveFix:** `KeyboardInputMixin` injects at `RETURN` of `KeyboardInput#tick` and rotates the input by `serverYaw - playerYaw`. Input space is `(sideways = x, forward = z)`, so the exact matrix is `forward' = forward*cos + sideways*sin`, `sideways' = sideways*cos - forward*sin`. Swapping those signs makes the movement lag the rotation by twice the error and Grim flags Simulation on every moving tick (verified on a local Grim server). It must run after vanilla samples the keys but before `tickNewAi` copies the fields; injecting into `ClientPlayerEntity#tickMovement` would be too late.
- **Raycasts:** `RotationManager#canSee` uses only server-held state: orientation `sentYaw`/`sentPitch` (updated in `popServerRotation`, i.e. after the packet was built) and origin `lastX`/`lastBaseY`/`lastZ` (the last position sent). It raycasts the exact hitbox within reach, then checks block occlusion (honouring `wallRange`). Never raycast the camera or the next tick's goal. Attack at `HEAD` before push (pre) or in the module tick *before* `RotationManager#tick` (post), never after. Using the goal rotation made Grim `Hitboxes` cancel every second attack while turning.
- **Sprint reset:** vanilla `PlayerEntity.attack` already calls `setSprinting(false)` on knockback hits. Only queue our own reset when the player is still sprinting after the attack loop (the flush after movement checks this), or packets duplicate and prediction desyncs.
- **Vanilla attacks:** while silent rotations are active, `MinecraftClientMixin` suppresses vanilla entity attacks at `HEAD` of `doAttack`. The visible crosshair does not match the server look, so allowing both would send attacks with the wrong rotation and reset the cooldown mid-aura. Block attacks are unaffected.
- **Sprint reset:** `ClientPlayerEntityAccess` exposes `lastSprinting`; keep it in sync when sending manual sprint packets or vanilla will emit duplicates or skip the restart.
- **Reuse:** combat and world modules share this manager. Reset it with `release()` from `onDisable`, and push settings every tick; don't keep module-specific rotation state in a second place.

### Anti-cheat behaviour to preserve

These come from reading Grim, Matrix, Intave and Polar check docs, and they should not regress:

- **Grim `Reach`/`Hitboxes`** re-raycast every attack using the current, last or last-last server look (3.0 + 0.0005 + movement threshold). The attack must be valid under the look the server has, not a future one.
- **Grim `Post`/`PacketOrder`** prefer the vanilla order: attack the tick *before* the flying packet. Keep `Order` (pre-move) default. `PacketOrderB` wants an animation between attacks, so `Swing: None` is not fully safe.
- **Grim `AimDuplicateLook` / `AimModulo360` / GCD processor**: only changed looks are sent (vanilla does this), yaw stays **unbounded** (wrapping at ±180 trips AimModulo360), and every delta is an exact sensitivity step.
- **Matrix KillAura** checks rotation-at-attack, aim sync, shake, snap, sensitivity and acceleration; it also spawns a fake player. Aura must attack NPCs (Anti-Bot defaults off), jitter the aim, ramp the turn speed and use GCD.
- **Intave `AttackRaytrace`** re-performs the client raytrace at 0.001-block accuracy (exact hitbox, 3.0 max). **Intave `Heuristics`** checks rotation against movement, so MoveFix must stay on and targets should be prioritized/limited.
- **Polar** mitigates on combat heuristics; closest-point aim, gaussian micro-jitter, FOV limits and post-hit delay variance are the relevant behaviours.
- Server-side state only. Every attack ray must be valid under the orientation/position the server actually holds. Vanilla's own server reach uses the last sent position, so current-eye raycasts miss on jumps even at the same yaw.
- Lobby/test servers put every player on its own white team and NPCs are missing from the tab list: `Teams` and `Anti-Bot` must stay off by default or nothing is targeted.

Never trade these for convenience without a setting and a preset entry.

`DevHarness` is an inert dev tool: create `<gameDir>/promptclient-autotest.txt` before launching to have it connect (`PROMPTCLIENT_TEST_HOST` overrides the host, e.g. `127.0.0.1:25565`), enable KillAura with the Grim preset and Debug, seek/attack targets, jump, rotate, log to `logs/latest.log` and screenshot. Delete the marker for normal launches.

## Rendering and performance rules

All UI (ClickGUI, HUD, chat) follows these rules. Check new UI code against them before finishing.

**Zero allocation per frame in our code.** Inside `render(...)`, `onHudRender(...)` and Mixin handlers on per-frame paths:
- No `new ArrayList`, streams, `String.format`, string concatenation, `new Color`, or lambdas or method references.
- Build lists once, in constructors or a lazy `initialize()`. Iterate with indexed loops or over arrays. `Module#getSettings()` and `ModuleManager#getModules()` return cached views and are fine; `getModulesByCategory` and `getEnabledModules` allocate, so call them at setup only.
- Cache strings and widths. `TextRenderer#getWidth` allocates internally, so measure once, or only when the string changes (compare by identity: `NumberSetting#getDisplayValue()` and `ModeSetting#getMode()` return the same instance until the value changes). Rebuild things like the FPS text at most every 250 ms.
- Colors are packed `0xAARRGGBB` ints. Use `ui/render/Colors` for alpha and lerp.
- Unavoidable, and accepted: vanilla creates one `BufferBuilder`/`BuiltBuffer` per batch, and one text `Drawer` per string drawn.

**Batch with `RenderUtils`.** In 1.21.1 every `DrawContext#fill` and `drawText` is its own draw call (`tryDraw()` flushes each one). Instead, for each layer:
```java
RenderUtils.begin(context);          // opens one TRIANGLES / POSITION_COLOR buffer
RenderUtils.shadow(...); RenderUtils.roundedGradientV(...); RenderUtils.roundedOutline(...); RenderUtils.circle(...);
RenderUtils.end();                   // flushes anything DrawContext queued, then draws all shapes in 1 call
RenderUtils.text(context, textRenderer, "Hi", x, y, color, false);   // queued, not drawn yet
RenderUtils.flush(context);          // all queued text in ~1 call
```
- Draw order is the order of `end()`/`flush()` calls. Queue text only *after* `end()`: `text()` throws inside a batch, because the text would otherwise be flushed underneath the shapes.
- `RenderUtils.setAlpha(f)` multiplies everything drawn afterwards (fades). Save it with `getAlpha()` and restore it afterwards.
- Clipping: use `RenderUtils.enableScissor(context, x, y, w, h)` / `disableScissor(context)`. They take the current matrix into account (so the ClickGUI's scale animation clips correctly) and flush queued text first.
- Text with alpha < 4 is skipped (vanilla would draw it fully opaque).
- RenderUtils uses its own `BufferAllocator`. Never call vanilla draw helpers (`drawTexture`, item rendering) between `begin` and `end`.

**Animation.** Use `FrameClock#tick()` for frame time (seconds, capped at 0.05) and `Anim.approach(current, target, speed, dt)` for exponential easing. Never animate with the `delta` that render methods receive: that is the partial tick, not frame time. Store every animated value in a field.

**No blur shaders.** `ClickGuiScreen#renderBackground` is overridden to a no-op, because vanilla's runs a multi-pass blur. The "frosted glass" look is alpha gradients plus 1 px borders (`Theme`).

**ClickGUI structure.** `ClickGuiScreen` → `CategoryPanel` (one per `Category`) → `ModuleEntry` (row + accordion) → `Component` (one per setting, then `KeybindComponent`). Layout is computed during render and reused for hit-testing on the next input event. Mouse input is mapped through the inverse of the open/close scale (`toContentX/Y`).

## Visual verification (UI changes)

`./gradlew build` only proves the code compiles. For UI work, take screenshots with a **temporary, uncommitted** harness:
1. Create `src/client/java/com/promptclient/DevHarness.java` with a step machine on `ClientTickEvents.END_CLIENT_TICK`. Hook it in `PromptClient#onInitializeClient` with a line ending in `// TEMP-HARNESS`, guarded by `System.getenv("PROMPTCLIENT_DEV_HARNESS") != null`.
2. Have the step machine:
   - Set `options.pauseOnLostFocus = false`, resize the window with `GLFW.glfwSetWindowSize(handle, 1600, 900)`, set GUI scale 2 and call `onResolutionChanged()`. That gives 800x450 scaled.
   - Create a world: `CreateWorldScreen.create(client, new TitleScreen())`. Once that screen is open, configure `getWorldCreator()` (name, `Mode.CREATIVE`, cheats, the `WorldPresets.FLAT` type), then call the private `createLevel()` by reflection (dev runtime uses Yarn names).
   - Once in a world, drive the UI directly: module `setEnabled`, `ModeSetting#setMode`, `currentScreen.mouseClicked/charTyped/keyPressed`, chat `addMessage`, `sendChatCommand("time set midnight")`.
   - Save screenshots with `ScreenshotRecorder.saveScreenshot(client.runDirectory, "name.png", client.getFramebuffer(), msg -> {})` into `run/screenshots/`, then call `client.scheduleStop()`.
3. Run `PROMPTCLIENT_DEV_HARNESS=1 ./gradlew runClient`, then view the PNGs with the Read tool. To compare brightness, sample pixels rather than eyeballing them; the ClickGUI dims the screen on purpose.
4. **Delete the harness and the `TEMP-HARNESS` line before committing.** `grep -rn "DevHarness\|TEMP-HARNESS" src/` must print nothing.

At 800x450, the five 124 px panels start at x = 70 (RENDER), 204, 338, 472, 606 (MISC), at y = 44. A panel's first module row is centered at y ≈ 81.

## Finding Yarn names (1.21.1)

Never guess a mapping. Check the Yarn-mapped Minecraft jar:

```bash
JDK21=$(ls -d ~/.gradle/jdks/*21*/ | head -1)
JAR=$(find .gradle/loom-cache/minecraftMaven -name 'minecraft-clientOnly-*.jar' ! -name '*-sources.jar' | head -1)
"$JDK21/bin/javap" -p -cp "$JAR" net.minecraft.client.render.LightmapTextureManager      # members
"$JDK21/bin/javap" -c -p -cp "$JAR" net.minecraft.client.render.LightmapTextureManager   # bytecode: exact INVOKE targets for @At
```

For readable source, run `./gradlew genSources` and read the `*-sources.jar` next to that jar with `unzip -p`. Mixin `@At(target = ...)` strings use Yarn names in JVM descriptor form, for example `Lnet/minecraft/client/texture/NativeImage;setColor(III)V`.

Yarn reminders: `MinecraftClient`, `ClientPlayerEntity`, `ClientWorld`, `DrawContext`, `TextRenderer`, `Text`, `InputUtil`, `GameOptions`, `SimpleOption`.

Version-specific behavior the code relies on. Re-verify all of it before porting to another Minecraft version:
- The lightmap is computed on the CPU in `LightmapTextureManager#update(float)` and rebuilt every tick. All three Fullbright modes hook it (see `LightmapTextureManagerMixin`'s Javadoc).
- `ChatHud#render` draws one `fill` per line, then the text at z+50. `ChatRenderer` mirrors its line-visibility and opacity logic.
- `Screen#renderBackground` applies the menu blur. The ClickGUI overrides it.
- The `BufferBuilder(BufferAllocator, DrawMode, VertexFormat)` constructor and `BufferRenderer.drawWithGlobalProgram(BuiltBuffer)` are what `RenderUtils` uses.

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
- [ ] UI changes follow "Rendering and performance rules" (no per-frame allocation, batched through `RenderUtils`) and were checked visually with the temporary harness. The harness is deleted before commit.
- [ ] `README.md` is updated: the features table for module changes, and any changed commands.
- [ ] The change is committed and pushed: `git push origin main` succeeded, and `git status` is clean.
