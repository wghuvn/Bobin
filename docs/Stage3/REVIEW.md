# Stage 3 checkpoint — limited industrial smoke polish

Baseline: Stage 2.1. Reuses IndustrialSmokeParticle, StructureDestructionClient, existing particle registration, textures and LOD/emission system. No fire damage, ignition, burn timer, structure flammability, server intensity, or weapon visual profile changed. Existing per-structure fire mechanics/durations remain authoritative.

Added smooth coherent wind and bounded deterministic multi-scale curl fields; age/phase-dependent size variation and less synchronized late canopy fading. Upper canopy is emitted over a deeper range closer to the column, retaining original large scale and continuity. Lower core receives a subtle warm tint that decays with height/age; the upper plume stays gray/charcoal. Existing core/middle/canopy color hierarchy is preserved.

A fixed three-lobe near silhouette reuses each particle's original texture instead of a single giant circular boundary. Medium distance uses two lobes; far distance one. No extra particle instances and no raymarching/new rendering libraries. Near geometry cost can rise to 3x for these smoke quads; actual FPS/TPS is unmeasured. There is no claim of cost-free rendering. Source textures are unchanged: altering their circular silhouette was avoided in favour of bounded lobe composition and motion. Final realism still requires visual testing.

Verified Java contracts: 10000 deterministic motion samples, bounded curl/scale and continuous wind, plus all Stage 2/2.1 contracts. Weapon industrial-isolation source paths remain unchanged from Stage 2.1.

Build verification: BLOCKED BEFORE COMPILATION. The actual Java 17 Gradle wrapper attempts for `clean build -x test` and separately `test` exited 1 because services.gradle.org could not be resolved to download Gradle 8.8. This is not a successful Fabric build. Dependency-free production Java kernels/contracts were compiled and executed with the Java 17 compiler module. Java syntax parsing is separate and does not validate Minecraft/Fabric symbols or mixin targets. No new libraries.

NOT EXECUTED: Minecraft client/server, Fabric GameTest, actual JUnit through Gradle, mixin initialization, NBT save/load round trips, real-world recovery, multiplayer/drone observation, shader rendering, FPS/TPS and visual acceptance. There is no screenshot/render evidence from this environment. Reports use source inspection and numerical tests, not a claim of reproduced visuals.

Existing-world regression gate: use copies of 2.7.7/Stage 2 saves, never the only original. Cover intact/damaged/critical/destroyed tanks, all sizes/variants, active fires, partial transitions, unloaded boundaries, containers, same-material player replacements, multiple reloads and recovery interruption. Check HP/fire/stage/IDs and world preset before and after. Verify all 12 weapon types, GROM-7 activation, tank movement, NPC injuries, hub/raid deployment and drone cameras/streaming. These runtime checks remain NOT EXECUTED.

## Reproduction

Run `python scripts/verify_revisions.py` for Java 17 contracts; append `--gradle` when the Fabric/Gradle dependencies are available. Run `gradlew.bat clean build -x test` and separately `gradlew.bat test` on Windows.

## Checkpoint gate

Stages 2.1, 3 and 4 were authorized as one sequence. Stop after Stage 4 for independent review. Stage 5 is not authorized or implemented.
