# Stage 2.1 checkpoint — layered weapon effects

Baseline: previously delivered Stage 2 source. All 12 stable profile IDs and packet routing remain intact. Each profile now specifies flash lifetime/intensity, fire duration and expansion rate, in addition to smoke density/lifetime, spread and streaks. A single deterministic choreography emits flash, fire lobes, low dust, rising smoke and fading embers within the existing client budget. Weapon fire/flash/embers use local emissive brightness; industrial particles are not used. Existing material chips and cosmetic rings remain secondary layers of that same event, not another explosion pipeline. Water retains a bounded splash/bubble response.

Source-confirmed: gameplay calculations, sounds, industrial fire source/resources and persistent data are unchanged. Verified Java contracts: 4196 existing assertions plus 60 layered profile/budget scenarios, including zero budget, deterministic output, flash durations and all five layer kinds. The JUnit adapter also invokes the new contract when Gradle is available. Visual identity/quality for each of the 12 categories requires in-game acceptance.

Build verification: BLOCKED BEFORE COMPILATION. The actual Java 17 Gradle wrapper attempts for `clean build -x test` and separately `test` exited 1 because services.gradle.org could not be resolved to download Gradle 8.8. This is not a successful Fabric build. Dependency-free production Java kernels/contracts were compiled and executed with the Java 17 compiler module. Java syntax parsing is separate and does not validate Minecraft/Fabric symbols or mixin targets. No new libraries.

NOT EXECUTED: Minecraft client/server, Fabric GameTest, actual JUnit through Gradle, mixin initialization, NBT save/load round trips, real-world recovery, multiplayer/drone observation, shader rendering, FPS/TPS and visual acceptance. There is no screenshot/render evidence from this environment. Reports use source inspection and numerical tests, not a claim of reproduced visuals.

Existing-world regression gate: use copies of 2.7.7/Stage 2 saves, never the only original. Cover intact/damaged/critical/destroyed tanks, all sizes/variants, active fires, partial transitions, unloaded boundaries, containers, same-material player replacements, multiple reloads and recovery interruption. Check HP/fire/stage/IDs and world preset before and after. Verify all 12 weapon types, GROM-7 activation, tank movement, NPC injuries, hub/raid deployment and drone cameras/streaming. These runtime checks remain NOT EXECUTED.

## Reproduction

Run `python scripts/verify_revisions.py` for Java 17 contracts; append `--gradle` when the Fabric/Gradle dependencies are available. Run `gradlew.bat clean build -x test` and separately `gradlew.bat test` on Windows.

## Checkpoint gate

Stages 2.1, 3 and 4 were authorized as one sequence. Stop after Stage 4 for independent review. Stage 5 is not authorized or implemented.
