# Stage 2 — Review candidate

Baseline: exact Bobin_craft(4).zip, version 2.7.7.
SHA-256: 16b502c9cea8a9d0698df52f51c9354aad4e4dba0863c2cc433c18c8932bc57d

## Implementation

Ordinary explosions previously emitted industrial particles in DroneExplosionEffects AND again in VoxelBlastClient. Replaced those overlapping weapon paths with one versioned explosion_visual_v1 event. All 12 categories have stable namespaced identifiers and distinct scale, emission count, lifetime, heat, streak and spread parameters. RPG and hand-grenade identity is resolved from the actual source, independently of shared gameplay blast enums.

CombatEffects sends one event per eligible viewer, including drone view origins. The client rejects malformed/version-mismatched packets, skips other dimensions, suppresses repeated event IDs within a bounded 512-event per-world window, and resets the window on world replacement/disconnect. Unknown profile IDs use a single neutral puff. Raw material IDs are session-local only and validated against the current client registry. No new persistent data or save format is introduced. A matching client/server mod installation is required; older clients without the new channel will not receive this custom cosmetic event.

Weapon smoke uses ExplosionSmokeParticle with existing voxel_cloud sprites, independent of all refinery parameters/factories. Maximum puff lifetime is 101 ticks (5.05 seconds); actual active custom smoke is capped at 384 per client. Particle settings reduce emission. Flash, material debris, streaks and dust belong to the same intended event. The existing GROM-7 ImpactDebris event is retained as its secondary material fragment layer; existing structural collapse and terrain-debris events remain independently owned by those systems.

The industrial spawnRefineryPlumePuff helper is retained byte-for-byte. Weapon event handlers never call it. All destruction/industrial source files and assets are unchanged. A weapon hitting and igniting an eligible refinery can still produce that structure's independent persistent fire; this is intended gameplay, not weapon-particle inheritance.

## Scope and compatibility evidence

Exactly six original files changed. DroneExplosion.java and Grom7ChargeEntity.java differ only in arguments to their visual calls. CombatEffects.blood is unchanged. Every other original file matches the baseline archive. No original files were deleted; no metadata, mixins, presets, registries, damage values, pressure, shrapnel, terrain calculations, sound calls, flight, chunk streaming, tank movement or refinery-fire settings changed. Save compatibility is supported by absence of persistent/schema/registry changes; actual saved-world loading remains NOT EXECUTED.

## Verification results

- Java runtime: OpenJDK 17.0.20.
- Pure Java compilation: PASS via `java -m jdk.compiler/com.sun.tools.javac.Main --release 17 ...` (the javac launcher is absent, but the compiler module is installed).
- Dependency-free Java contracts: PASS, 4196 assertions. Tests cover all profile definitions, source mappings, serialization, every truncated packet prefix, unsupported versions, trailing/oversized packets, invalid IDs/coordinates, unknown profiles, deterministic shapes, zero/limited particle budgets, short lifetimes and replay-window limits/reset.
- Java parser check: PASS for 86 files. Parsing does not validate Minecraft/Fabric symbols.
- JSON: PASS, 1512 files under src; no resources changed.
- Baseline/scope comparison: PASS. Original ZIP hash, exact modified-file allowlist, visual-only callsite changes and unchanged blood implementation checked.
- Weapon/industrial isolation: PASS by source inspection and automated dispatch checks. One receiver, no industrial calls in weapon handling, no old visual packet senders.
- `bash gradlew clean build -x test`: BLOCKED before compilation, exit 1. Gradle 8.8 download failed with UnknownHostException: services.gradle.org. This is not a successful build or a Java compiler failure.
- `bash gradlew test`: BLOCKED before tests, exit 1, same dependency-download failure. The JUnit wrapper was not executed by Gradle; the shared contract was compiled/run directly.
- Full Fabric compilation, GameTest, client/server launch, screenshots, FPS/TPS, multiplayer, water rendering, shader interaction and actual saved-world tests: NOT EXECUTED. Minecraft dependencies/runtime are unavailable here.

Logs are included in docs/stage2-validation. Run `python scripts/verify_stage2.py /path/to/Bobin_craft(4).zip` to reproduce the independent Java/scope checks. On Windows run the required `gradlew.bat clean build -x test` and separately `gradlew.bat test`.

## All-category verification matrix

| Source | Profile (bobin_craft namespace) | Mapping/contract checks | Minecraft visual check |
|---|---|---|---|
| Standard FPV | fpv_standard | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Fragmentation FPV | fpv_fragmentation | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Breaching FPV | fpv_breaching | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Bomber HE | bomber_he | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Bomber fragmentation | bomber_fragmentation | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Heavy bomber | bomber_heavy | PASS (static source routing + Java contracts) | NOT EXECUTED |
| FP-1 | fp1_strike | PASS (static source routing + Java contracts) | NOT EXECUTED |
| GROM-7 | grom7_demolition | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Tank HE shell | tank_he | PASS (static source routing + Java contracts) | NOT EXECUTED |
| RPG | rpg_impact | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Hand grenade | hand_grenade | PASS (static source routing + Java contracts) | NOT EXECUTED |
| Dropped grenade | dropped_grenade | PASS (static source routing + Java contracts) | NOT EXECUTED |

## Manual runtime acceptance

Use a matching client and server. In a test world, detonate each listed category once on dry terrain away from industrial structures, once in water, and once near a wall. Observe flash, dust direction/spread, material chips, smoke duration and category identity. Compare to the baseline using equivalent targets: entity health, fragment injuries, pressure, terrain changes and sounds must remain equivalent. FP-1 should have the largest transient weapon effect; none should create the sustained refinery plume.

Test two observers and a drone-camera observer. Each should see one intended effect per explosion, without a second overlapping industrial burst. Disconnect/reconnect and change dimensions: no stale events or smoke should cross sessions. Repeat bursts at all particle settings and confirm custom-smoke limits. Visual quality/distinctness requires user acceptance; numeric profile differences alone are not visual verification.

## Existing-save regression plan

Use backup copies of 2.7.7 saves, preserving originals. Before triggering new gameplay, compare structure HP/stage/fire, pending transitions, world preset, tank state, placed blocks/containers and player inventory with baseline. Loading must not trigger a migration, geometry reset, or collapse replay introduced by this stage. Test intact/damaged/critical/destroyed structures, active fires, interrupted transitions, unload/reload boundaries, both grenade sources, GROM-7 activation and drone flight/camera/streaming. Save/reopen twice, then test the 12 explosions in a separate area. Existing destruction defects remain outside Stage 2; compare their baseline behaviour rather than claim they are repaired.

## Remaining limitations and next gate

Full Fabric symbol compilation and actual visual behaviour are unverified. No claim is made that gameplay/runtime regression checks passed. Same-version client/server deployment is expected for the new cosmetic channel. Stage 3 is not started; refinery smoke, geometry, transitions and persistent debris are unchanged.

The changed-files ZIP overlays the full baseline root Bobin_craft/. No file deletion is required. The full ZIP includes every original source-project file plus the listed additions, without build caches.
