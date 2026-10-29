# Bobin Craft — consolidated Stages 2.1–4 stabilization

Date: 2026-10-27. Revision: 2. Minecraft 1.20.1; Fabric; Java 17. This delivery is an implementation candidate awaiting independent review. Stage 5 has not started. All eight stabilization areas have source changes in this one checkpoint. Full Fabric compilation and runtime acceptance remain blocked/unverified as detailed below; this is not a verified release or a built mod JAR.

## Revision 2: independent-review correction

The independent review reconstructed all 2,309 R1 project hashes and reran its 35,991 stabilization checks successfully, then identified one remaining smoke-allocation defect. R2 preserves all R1 changes and corrects that denominator in one cumulative revision. Stage 5 remains not started.

`StructureDestructionClient.tickClientEffects()` previously counted damaged/critical non-burning structures even though they emitted no industrial smoke. It also counted unloaded/out-of-range views. A shared emitter predicate now requires a non-bridge industrial category, actual burning, positive intensity, a loaded origin chunk and a finite distance within the unchanged far LOD. Both the counting pass and emission pass use that predicate. Damaged substations retain their independent electrical arcs, without consuming smoke allocation.

The old `StructureFireManager` rate calculation moved without a formula change into the dependency-free production `IndustrialSmokeBudget`; the manager delegates with its existing mutable configuration values. Near/medium/far thresholds, rates, the default 200-particle cap, lifetime assumption, particle factories, smoke RGB/motion/appearance, per-tick particle reservations, fire state and server fire damage remain unchanged. Only eligible emitters share the allocation. Existing max-cap enforcement in the particle factory was not changed.

Regression evidence: the R2 client-wiring check fails against the actual archived R1 client source (`R2-smoke-before-fix.log`). With one near burning refinery at intensity .9, adding 256 damaged/critical non-burning structures previously lowered the base budget from 7.68 to approximately .19455 particles/second before the unchanged pulse multiplier. The production-kernel regression now adds 0–256 such structures at six LOD boundary positions and verifies an identical refinery budget throughout. Additional cases exclude unloaded, out-of-range, zero-intensity and bridge views and preserve multiple-real-fire cap scaling. The full stabilization contract passes **39,087 checks**, retaining all 35,991 prior checks and adding 3,096 smoke allocation checks. Java 17 parses 302 source/test files; the other explosion/profile/motion/ledger contracts also pass.

The requested geometry audit is detailed in `R2_GEOMETRY_AUDIT.md` and the reproducible source-bound `audit_geometry.py` output. It checks all four tank masks and attachments, current and older baked crossings, both orientations, index envelopes/chunk buckets and every terrain edit branch. All 6,166 tank mask centers and 43,162 near-face probes are covered. Bridge logical hit zones do not cover every endpoint/deep-support cell; this distinction is reported explicitly. For current authored bridge materials and forbidden source profiles it does not yield a reachable structural edit: forbidden sources never enter breach/fire branches and the remaining glass/soil/plant targets exclude those materials. Therefore `BlastTerrain.terrainAllowed()` and `StructureSpatialIndex.findPrimaryAt()` were left byte-identical to R1. No ordinary-terrain protection was broadened. A new actual-geometry Fabric GameTest checks real generated cells and actual tags when dependencies are available; it was not executed here.

R2 reran the Java kernel suite, resources/scope checks, static geometry audit and audio decode/preservation check. Real `clean build -x test`, `test`, `runGametest`, `runClient` and `runServer` were all attempted anew; all failed at wrapper download with `UnknownHostException: services.gradle.org`, before compilation or Minecraft launch. **Full Fabric build, Gradle JUnit, GameTests (including the new geometry test), live block changes, rendering/audio listening, saves, multiplayer and performance remain NOT EXECUTED.** `R2-execution-status.json` and `R2-*.log` record actual outcomes.

The full R1 archive SHA-256 was `f70cc0784321a020627eeef0b520e1c5f065bc8fd51e73c57148cbb77919d3a0`. It is retained as a checkpoint. The R2 patch remains cumulative against the original uploaded baseline, not a patch to apply onto R1. The exact R2-versus-R1 source changes are also supplied in `R2-vs-R1.diff` and `R2-scope.json`.

## Revision 1 history (preserved)

A follow-up review reproduced an uncovered GROM-7 auditory case. The entity's existing sympathetic activation occurs two ticks after the initiating charge. By then, the first charge's three immediate audio layers had already been drained from the mixer, so a second nearby charge replayed them. A regression invoking the actual production mixer between detonations failed before the fix: 10 emitted layers instead of 7. The failed run is retained in `R1-audio-chain-before-fix.log`.

`ExplosionSoundMixer` now also retains a bounded, short-lived history of emitted layer anchors. Requests still match dimension, waveform/category, an 8-block radius, the original two-tick request window and due-time tolerance. A matching recently emitted onset is accepted without replaying, stopping or modifying its playing voice. Pending tails can still merge and receive bounded gain. The already emitted first onset is not retroactively amplified; this preserves responsiveness without adding another two-tick hold to every single-charge detonation. Queue capacity stays 128 groups, with a separate history capacity of 128. History expires, clears with the existing reset, and cannot grow across inactive dimensions.

In R1, only the mixer and its contract changed in production/test Java relative to R0. No gameplay/entity, pressure, activation, network packet, save schema, animation, motor asset or Stage 5 code was changed in this revision. New cases cover two/three/ten charges with a two-tick separation, late onset suppression, independent distant/dimension events, expiry, reset, clock rollback and bounded history. The previous 35,747-assertion stabilization contract passed 35,991 assertions in R1, an increase of 244 (R2 totals are above).

Historical R1 results are under `R1-execution-status.json` and `R1-*.log`, with canonical build/test logs refreshed. R1 clean-build and Gradle-test attempts still fail before compilation while resolving services.gradle.org; no cached Gradle distribution is available. The client/server/GameTest bootstrap attempts documented later were performed in R0 and were not redundantly repeated in R1. R2 retried those launch tasks with the same bootstrap failure. Those actual Minecraft/runtime checks remain NOT EXECUTED.

The R0 full-source archive hash was `b5f1dd60ba5cc13b14e5ecd9bb3c50f380bb99f1a86cf2a2b0d1da3654bf0335`. R1 supersedes that checkpoint while the cumulative patch remains based on the original attached archive, not on R0. The previous checkpoint is retained independently.

## Baseline and scope

Baseline: the supplied complete `Bobin_craft(1).zip`, SHA-256 `4a4729e35da7842671b03de4d91d01a4c17aefff2797e5eab9b77073b295abe0`, 2,278 files. The original attachment was preserved. The checkpoint contains the previously implemented Stage 2.1, 3 and 4 work plus these corrections, without applying historical overlays or starting a new architecture audit.

The external `BobinCraft_Stabilization_Manifest.json` lists every added/modified/deleted project path and old/new hashes, plus all baseline project hashes for safe patch application. The complete source ZIP is independently usable. The incremental ZIP is against this exact baseline, not any earlier Stage 2 archive. Test logs and scripts are under `docs/Stabilization/` in both deliverables.

Two project-local observational mixins were deliberately removed: `RefineryBlueprintScopeMixin` and `RefineryGeneratedCellMixin`. Their registrations were removed and their behavior replaced by direct calls in the same mod-owned generator functions, with `finally` restoration. No other original project file was deleted. `build.gradle` also lists these two retired files for cleanup when an overlay is extracted over an old tree. The patch applier explicitly removes them.

## 1. Structural damage exclusions

Root cause: dropped payloads and hand grenades were still mapped to positive structural damage; both normal and direct structural damage entries could reach component HP, ignition, delayed work and destruction events. Terrain collection protected breach/glass paths but not every relevant scorch/application path.

Added one `StructureDamagePolicy` decision function using existing stable source IDs and structure categories. `hand_grenade`, `drone_payload` and `heavy_drone_payload` are rejected for bridge sections and refinery tanks at the common direct mutation boundary. All ordinary, direct and deferred calls reach this guard before HP, ignition or transition work. Substation behavior is retained. FPV kamikaze, FP-1, GROM-7, tank, RPG, legitimate fire and admin sources retain their existing allowed rules.

`BlastTerrain.Plan` retains its source kind; collection and application both protect affected bridge/NPZ cells for forbidden sources, including soil/plant scorch. Ordinary terrain and entity damage remain separate. GROM-7's terrain envelope uses a bomber HE blast type, so `StructureDamageSource.fromDroneBlast` now recognizes the actual GROM-7 entity as demolition rather than misclassifying it as a dropped payload. This avoids accidentally blocking the intended heavy source through the new shared policy. RPG's existing dedicated structural call and the common explosion skip are unchanged. Forbidden sources cannot schedule new FP-1 delayed damage because rejection precedes mutation/scheduling; existing independent fires are not cancelled.

Tests: production policy table passed. A registered GameTest exercises normal and direct manager calls and delayed ticks against bridge/NPZ records while comparing full compact state, but it could not execute here. Actual entity damage and terrain comparisons remain manual acceptance items.

## 2. Industrial smoke color and ownership

Root cause: lower-core RGB was warmed repeatedly using already-warmed channel values. Ordinary structural hit and transition handlers emitted large industrial particles; a periodic fallback emitted industrial smoke for a damaged non-burning structure.

Color now derives from immutable base channels and bounded, stateless age/height illumination; the production color kernel is exercised across repeated updates. Existing smoke wind, turbulence, multi-lobe silhouettes, scale, lifetime and server fire gameplay remain. Ordinary hits and transitions now use short POOF/material dust; electrical sparks are restricted to appropriate electrical categories. No hit/transition branch emits refinery core/column/canopy. Continuous industrial emission requires actual burning state; an unknown client view does not imply burning.

Particle accounting now tracks actual factory-created custom particles, with queued emissions reserved in the controller estimate. The existing configured cap (default 200) is enforced at the factory; near smoke remains at most three quads per particle. Short weapon effects have their own custom cap and vanilla secondary-emission budget. This corrects the old approximate lifetime accounting. It may alter plume density at saturation, so retaining satisfactory large-fire appearance must be checked in game. Particle dimensions, long-fire lifetimes, fire DPS, ignition and burn timers were not globally reduced or redesigned.

Tests: color, prior motion and R2 emitter allocation contracts passed; references and JSON passed. Live flame/plume continuity, shaders and FPS were not tested.

## 3. FP-1 and GROM-7 spatial/time identity

Root cause: the twelve-profile dispatcher existed, but FP-1 and GROM-7 used common distribution logic with mostly scalar differences. Old specialized shape methods were unused.

Kept all twelve namespaced profile IDs and cosmetic packet version 1. Added deterministic layer delays and specialized layouts within the single existing event: FP-1 pressure skirt, rolling fire, rising stem and delayed elevated cloud; GROM-7 short, low concentrated demolition fan/torus with local combustion. They differ in geometry and phase order, not only scale. Five layer kinds remain; small budgets degrade gracefully. No legacy duplicate explosion dispatch was restored. FP-1's last smoke can finish approximately 5.5 seconds after the original event, still a transient weapon effect.

Pending phases reserve capacity together with active custom particles under the existing cap of 384; world/disconnect reset clears them. Streaks, water response and vanilla secondary dust share a separate 160-emission budget per client tick. GROM-7's attached-face ray now prefers the actual attachment surface, improving material context and allowing the client to orient local effects using a verified collision ray without a packet-format change.

Tests: old twelve-profile/protocol/dedup contracts and new shape/timing/budget checks passed. In-game appearance and all-surface identity remain unverified.

## 4. Incorrect temporary cubes and source materials

Root cause: temporary impact debris simulation was advanced from rendering, checked a single point below, and could treat overlap with overhead geometry as a floor. It had no lateral full-volume test, substituted materials and retained cosmetic rubble for three minutes. These are separate from the animator's structural remnants.

Temporary chips now advance on client ticks. Their small source-state geometry is enclosed by a conservative volume covering all rotations. Movement clips that whole volume against Minecraft block collision sub-boxes, with swept bounds covering high-speed motion; floors, sides and overhead contacts are distinct. Initial overlaps, missing collision chunks or excessive query complexity retire the temporary cosmetic instead of guessing a landing or pushing through a wall. Verified contact may sleep a chip; lost support resumes from its pose. Chips expire after 120 ticks and are bounded to 96 plus a per-tick spawn budget. The existing short vehicle-push prop animation is retained.

Server structural impact bursts sample actual loaded source materials instead of fixed cobblestone/terracotta. Unknown GROM-7 source material no longer invents cobblestone. The animator's two missing existing material keys, `pipe_iron` and `busbar_metal`, resolve to their real mod blocks; an unknown key no longer fabricates cobblestone. All existing model/animation JSON files remain byte-identical.

The full animator ground-search, structural-remnant persistence and final pose handoff have deliberately not been redesigned. Those remain Stages 5 and 7. The screenshots alone cannot establish which renderer produced every visible fragment; this candidate corrects the traced temporary path, not every future structural debris feature.

Tests: production swept-clipping kernel passed slab, thin wall, ceiling, diagonal and support-loss cases across 100 sizes. The actual Minecraft collision adapter, particle render depth and shader interactions require gameplay testing.

## 5. GROM-7 sound overlap

Root cause: each charge started three immediate recordings and four delayed layers, with additional server/client structure cues. The old queue was capped but did not combine nearby correlated audio.

Added an auditory-only dimension-aware mixer with at most 128 queued groups, an 8-block spatial radius, a two-tick request window and a three-tick due-time tolerance for matching sound/category. Immediate requests collect for one tick, allowing simultaneous charges to group before playback. Single requests keep their gain/pitch. Revision 1 also remembers recently emitted anchors so two-tick sympathetic charges cannot replay an onset that has already left the queue. Group gain grows sublinearly and caps at 1.35 times the strongest requested layer; already-playing sounds are not cut off. Distant/sequential detonations remain independent. Charge and structural server sounds route through this owner; duplicated client hit/transition recordings were removed. Distinct delayed fracture/settling cues can still sound separately.

No charge activation, sympathetic chain, pressure calculation, structural damage or event was deduplicated by the mixer. Source comparison confirms GROM-7 gameplay sections remain unchanged; only cosmetic surface context and sound routing changed in that entity.

Tests: one/three/ten simultaneous requests and two/three/ten requests split over the actual two-tick sympathetic delay retain one complete seven-layer identity; gain, capacity, dimensions and distant/sequential separation passed. These are scheduler tests, not acoustic measurements of a running game. In-game recording, clipping, perceived power and multi-listener/drone audio remain NOT EXECUTED.

## 6. FPV motor audio

Removed the multirotor bee-loop selection. Added an original procedural brushless/propeller loop, a dedicated registered event, mono 48 kHz Vorbis asset, locale subtitles, smoothed throttle pitch/gain, and 64-block observer range/asset attenuation. Existing pilot/observer loop ownership remains. The fixed-wing asset and fixed-wing branch tuning were preserved.

The asset is synthesis, not a field recording of a specific real motor. Its generator and provenance are delivered under `docs/audio/`. No external recording/license claim or inappropriate vanilla replacement is used. FFmpeg decoded all eight seconds: peak approximately 0.587, RMS approximately 0.112, negligible DC, no clipped samples; the loop join step was within ordinary adjacent-sample changes. The actual encoded SHA is recorded in `audio-analysis.json`.

Limitation: perceived realism, pilot loudness, sound-engine loop continuity and observer attenuation were not auditioned in a game. An appropriate original asset was created; its final acoustic acceptance remains open. No additional FPV explosion sound was added.

## 7. Refinery queue, provenance and compatibility

Root cause: each tick rebuilt/sorted all pending cell lists and repeatedly spent budget on permanently unresolved/protected cells. Provenance binding and dirty marking also repeated without useful changes.

The durable version-1 ledger fields remain; no journal/schema or geometry revision was silently advanced. Rebuildable runtime indexes distinguish actionable work, unloaded-chunk waits, absent provenance, protected conflicts and completion. Retargeting orders once; normal ticks process ready candidates round-robin. Provenance wakes matching cells. Chunk-load notifications wake indexed waits. A rotating fallback examines at most 16 deferred chunk keys every 40 ticks; wake processing has a separate 64-cell budget and notification cap. Normal provenance ingestion is capped at 256 records per tick. Parked legacy/protected cells do not consume the active update budget or get sorted each tick. Writes remain server-owned and check exact expected state and block-entity safety. Rejected writes become conflicts instead of hot retries. Journal dirty marking is tied to mutations. Diagnostics expose classifications and current check/write/maintenance counts.

Direct project-owned generation observation is scoped with `finally`, confirms the actual successful block state, and carries a server-generation epoch to reject an already-running obsolete scope after reset. Spatial buckets replace global tank scans. Graceful shutdown drains the finite provenance backlog before persistent-state saving, without block writes. Abrupt crashes still have the existing non-atomic chunk/journal-save limitation: unknown provenance remains unresolved, never permission to delete.

Both removed generator mixins were replaced explicitly. The remaining external-edit observer has an exact `WorldChunk.setBlockState(BlockPos,BlockState,boolean):BlockState` selector checked against official Yarn 1.20.1 build 10 documentation. Full Mixin application is not verified. The current authoritative final blueprint path, four tank dimensions, geometry mask, structure schema 2, journal version 1, geometry revision 2 and existing save serializers remain unchanged. Current tank generator attachments use SET writes, so no speculative placement-mode conversion was introduced.

Tests: previous geometry/retarget/conflict contracts passed; a 10,000-cell unknown backlog stays parked without candidate polling or dirty mutation, provenance and chunk wakeups work, negative chunk packing and protection survive repeated operations in production kernels. NBT round-trip/future-version preservation GameTest was added but not executed. Live generation-to-snapshot equality, all variants, real-save recovery and actual mixin tracking remain release gates. Legacy unknown blocks can intentionally retain an old roof defect; no ownership or missing historical component HP is invented.

## 8. Verification actually performed

| Check | Result |
| --- | --- |
| Java 17 syntax parse | PASS: 301 source/test files; no Minecraft symbol resolution claimed |
| Dependency-free production Java compilation | PASS, through the installed `jdk.compiler` module |
| Previous explosion/profile contracts | PASS: 4,196 assertions plus 60 layer/profile/budget scenarios |
| Previous smoke-motion contract | PASS: 10,000 samples |
| Previous refinery geometry/ledger contract | PASS, with protected target retention updated explicitly |
| New stabilization contract | PASS: 39,087 checks (35,991 retained + 3,096 R2 checks) |
| R2 source-bound geometry audit | PASS static equations/edit gates; actual Minecraft geometry GameTest NOT EXECUTED |
| JSON | PASS: 1,512 files |
| Particle sprite references | PASS: 25 local references found |
| Sound references and metadata classes | PASS: local assets and registered class paths found |
| NBT | PASS: all 19 structure files decoded, palette/coordinate validity checked; six bridge template contents reported |
| Protected source scope | PASS: 189 protected Java files byte-identical; all NBT/model/animation resources and existing save serializers/snapshot preserved; GROM gameplay sections compared separately |
| FPV sound encoding | PASS: decoded format/sample/peak/DC/boundary checks; listening not performed |
| `bash gradlew clean build -x test` | BLOCKED BEFORE COMPILATION: Gradle 8.8 download fails with `UnknownHostException: services.gradle.org` |
| `bash gradlew test` | BLOCKED BEFORE COMPILATION: same wrapper bootstrap failure |
| `bash gradlew runGametest` | BLOCKED BEFORE COMPILATION: no GameTest methods executed |
| `bash gradlew runClient` | BLOCKED BEFORE COMPILATION: Minecraft not launched |
| `bash gradlew runServer` | BLOCKED BEFORE COMPILATION: dedicated server not launched |
| Live saves, multiplayer, drone observation, FPS/TPS, audio listening | NOT EXECUTED |

The Java source launcher uses the available Java 17 compiler module despite the missing standalone `javac` command. No generated classes/caches are shipped. Failed first checks are retained: an overly strict float comparison in a new test was corrected, and the resource checker was corrected to resolve particle textures beneath `textures/particle/`. Neither initial failure was represented as a final pass. Full build/bootstrap logs contain actual commands and exit statuses.

GameTest registration and a separate Loom run were added. `test` and `clean build -x test` do not execute the GameTest environment. New fixtures cover manager-level exclusions and journal NBT protection; these are not falsely counted among executed checks. No EULA was accepted automatically.

**MINECRAFT RUNTIME TESTS: NOT EXECUTED.**

## 9. Manual acceptance matrix

Use a copied save and this exact full-source checkpoint. On Windows run `gradlew.bat clean build -x test`, then `gradlew.bat test`, then `gradlew.bat runGametest`, and launch `runClient`/a dedicated server as appropriate. Resolve the pinned dependencies and inspect both client/server logs for successful mixin application before trusting gameplay results. The normal build outputs a JAR only if it actually succeeds.

| Scenario | Required observation | Current live status |
| --- | --- | --- |
| Twelve weapon profiles, fixed seed/settings | One event, distinct expected identity, bounded transient effects; no industrial cloud by default | NOT EXECUTED |
| FP-1 terrain, wall, enclosed room | Phased burst and late upper cloud; no large hanging temporary cubes or wall tunneling | NOT EXECUTED |
| GROM-7 one/three/ten near a point | Recognizable single-charge identity; bounded cluster loudness; every charge still damages/activates | NOT EXECUTED |
| GROM-7 distant/sequential, two listeners | Independent spatial events, no cutoff or duplicate client/server beds | NOT EXECUTED |
| Hand/dropped grenades, HE/fragmentation/heavy drone payloads vs bridge/NPZ | Zero structural HP/component/stage/ignition/transition delta, including delayed ticks and reload | NOT EXECUTED |
| Same forbidden sources vs entities/ordinary terrain/substation | Existing permitted gameplay retained | NOT EXECUTED |
| FPV, FP-1, GROM-7, RPG, tank vs same structures | Existing intended structural damage retained | NOT EXECUTED |
| Non-burning bridge/substation hits and transitions | Short dust/material/electrical effects only; no industrial core/column/canopy | NOT EXECUTED |
| Burning refinery/power infrastructure, several fires | Existing fire mechanics/large scale; acceptable density at real particle cap; stable bounded colors | NOT EXECUTED |
| Temporary chips: slab, stairs, fence, wall, ceiling, support removed | Whole-volume collision, no upward snapping; tick-driven behavior; short expiration | NOT EXECUTED |
| FPV pilot/observer, throttle, mode switch, multiple drones | Clear motor character, smooth gain/pitch, correct distance and no duplicate loops; FP-1 sound retained | NOT EXECUTED |
| Four tank sizes × four variants and attachments | Final generated reference matches live result; no filled hollow interior or extra roof band in owned geometry | NOT EXECUTED |
| Retarget during transition; unload/reload; save-order interruption | Idempotent completion of owned work; parked waits wake correctly without forced loads | NOT EXECUTED |
| Old partially destroyed saves, player containers/same-material replacements, unknown versions | No healing, resetting, replaying or ambiguous-block deletion; unsupported records retained | NOT EXECUTED |
| Hub/raid/world preset, tanks, drone camera/signal/streaming, NPC injuries | Protected systems behave as before | NOT EXECUTED |
| Repeated bursts, many parked cells, far/near camera and multiple observers | Measure frame/tick time, particles, queues, memory and traffic; no unbounded growth or active unresolved scans | NOT EXECUTED |

## 10. Delivery and deliberate deferrals

The full source ZIP and incremental ZIP are one consolidated milestone. Extract the patch into a separate folder and use its `apply_stabilization_patch.py TARGET_PROJECT`; it verifies original hashes and payload hashes before mutation, backs up modified/deleted paths, removes the two obsolete mixins, and rolls back if application fails. It refuses a different baseline; the full source ZIP is preferable if the local tree already diverged. The patch reconstruction is byte-compared with the full source checkpoint. The manifest includes explicit deletions and hashes. ZIP caches/build outputs are excluded.

No support-aware persistent structural debris, bridge load graph or animation asset redesign was implemented. The older animator's client-only structural remnants, limited vertical landing search and future pose handoff remain documented Stage 5/7 work. Bridge NBT `intact.nbt` is still semantically incomplete (4,136 air + 28 andesite); it remains unused as a replacement for the Java generator and must not be switched in as a fix. Real-save compatibility, final rendering/audio quality and performance remain unverified rather than silently assumed.

The implementation candidate is ready for independent source review and execution in an environment with the dependencies. Passing the local kernels does not remove the build/runtime gates. No Stage 5 work is authorized or started.

**STAGES 2.1–4 STABILIZATION COMPLETE — AWAITING USER REVIEW. STAGE 5 NOT STARTED.**

Here “complete” identifies the implemented stabilization package; compilation/runtime acceptance is explicitly blocked, not claimed complete.
