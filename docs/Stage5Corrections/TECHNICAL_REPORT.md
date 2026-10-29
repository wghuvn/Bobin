# Bobin Craft 2.7.7 — consolidated Stage 5 correction candidate

Date: 2026-10-28 (Europe/Kyiv). **Status: source correction candidate, NOT a compiled/visually accepted release. Stage 5 runtime acceptance remains failed/pending independent retest.** No new local-breach system, Stage 6 graph or Stage 7 art is claimed.

## Baseline and evidence

The supplied bundle was opened locally. Its nested full source contains 2,356 project files and has SHA-256 `78f5c3a35ede0c3522d758dda776702cce927bd3d734dfc6e0b66ee8e34936e4`, exactly matching the handoff. It was copied as the correction baseline; the cumulative R2-to-Stage-5 patch was NOT applied again. The separate final manifest identifies every file and every source-file hash. The packaged patch targets this exact supplied Stage 5 file baseline, not stabilization R2 or a ZIP-container identity guessed from another archive.

Read: consolidated attached task; original handoff; Stage 5 implementation report; relevant master-plan boundaries; runtime addendum; visual-failure review. Opened both bundled refinery screenshots and the bridge screenshot. All four original recordings were obtained and inspected through FFprobe/FFmpeg frames, including a dense 5 fps sequence around the FP-1 artifact. No prior AI summary was substituted for the originals. Video metadata and extracted contact sheets are included under evidence/. The recordings contain no server fragment/event telemetry or a verifiable loaded JAR hash; their exact live identities/build cannot be retroactively reconstructed from pixels.

| Original recording | Duration | Direct observations and useful times (seconds from recording start) | What the pixels do not establish |
|---|---:|---|---|
| 00.24.17.35 refinery | 18.099900 s | 1.51–7.54: close regular wall/panel/column surfaces and visible fire; 13.57–16.59: repeated vertical brown/corrugated cylindrical remains from above. Red-circled screenshot shows related tank-shaped clusters. | Which individual surfaces are static blocks versus stalled source-derived fragments; exact source/operation/phase. |
| 00.25.36.37 bridge | 9.201356 s | 0.00–1.53: diagonal separated concrete below deck; 6.90–8.43: separated fragments near water/remaining pier. | Permanent freezing, riverbed depth, valid contact manifold or unloaded chunks. Camera moves throughout. |
| 00.27.39.38 underside | 20.351367 s | 5.09–8.48: large angled concrete/gray pieces near a pier; 11.87–16.96: substantial deck/truss remains above water. | Whether pier support or another debris body legitimately supports each piece; a full bridge graph is not Stage 5. |
| 00.29.33.39 substation/FP-1 | 20.382811 s | Pale hard-edged plate at about 6.8 s, large close orange/pale layers around 7.0–8.6 s, another flash near 10.2 s and orange layers around 11–12 s; industrial smoke continues at 14–19 s. | Event UUID/render owner of each recorded quad; no controlled F1-on/off reproduction or target-client shader telemetry. |

The contact-sheet labels on `clip0_sheet.jpg` through `clip3_sheet.jpg` are absolute recording times. `fp1_dense.jpg` starts at recording 3.8 s and its internal timestamps are relative to that extraction: add 3.8 s. The report does not assert that all sampled apparent pauses are frozen fragments.

## Diagnosis and corrections

### 1. Some real structure parts are outside their damage zones

Confirmed in source: the old substation equipment bounds cover the switching-box neighborhood around site (+12,-17), but do not cover actual gantry posts at x offsets -19/+19 and z offsets -29/+16, or all high jumpers. Transformer boxes alone do not cover those missing parts either. An RPG hitting those world blocks can fail target resolution despite a valid weapon event. The generator source is `IndustrialFacilities.substation()`/`transformer()`; no invented replacement site is used.

Correction: explicit switchyard/gantry/high-jumper regions follow those actual generator envelopes, preserving existing structure IDs and reusing the existing equipment_hull component save key rather than inventing healthy new component HP for an older save. Bridge hit bands now follow every actual crossing column, slope, endpoint range, deep pier and upper truss height instead of a midpoint box with a fixed 14-block support depth and rounding gaps. This is hit registration, not a support/load-path graph. `StructureSpatialIndex` is reused unchanged.

RPG damage remains the existing one call from `RpgProjectileEntity.detonateAt`; `DroneExplosion` still excludes that RPG from another structural dispatch. Damage amounts, multipliers and grenade/drop-payload exclusion policy are unchanged. A dimension check prevents an unrelated dimension from hitting the global warzone registry by matching coordinates. Terminal targets are diagnosed explicitly rather than silently excluded as a target miss.

No original live shot was replayed here. Missing-zone geometry is a confirmed source defect; attribution of every no-op in the recordings still requires the supplied live trace.

### 2. Stage geometry and removal ownership disagree with generation

Confirmed in source: separate bridge templates reconstruct an approximation of the real generator (including different upper-truss coverage), and substation equipment stages omit large actual gantry/jumper geometry. Matching a generated material elsewhere is not sufficient removal ownership. Baseline queue fallback `isKnownStructuralMaterial` is too permissive for the user's proof-of-ownership requirement.

Correction: nonrefinery stage targets are projected onto actual generated bridge/substation source cells; surviving cells keep their authored material/state and nonexistent source cells never turn into static rubble. Existing coarse stage masks remain coarse: this does NOT implement impact-local holes. The real refinery snapshot stays authoritative. Its destroyed-height function no longer uses high-frequency coordinate modulo to make repeated column tops; retained tank cells no longer receive a generic rusted-metal replacement at the cut edge.

All four managed categories now use the existing durable `RefineryTransitions`/`TransitionLedger` path. Mod-owned generation observation is extended to bridge/substation blueprints through existing direct `BlueprintBlitter`/`ChunkWriter` hooks. No new mixin selector or separate ownership engine is added. Only generated occupied nonrefinery source cells are captured; broad air-clearance boxes do not grant terrain ownership. External edits, same-material remove/place replacements and block entities retain protection. Detached source cells are subtracted from later targets so a revised stage mask cannot recreate a wall behind an already committed fragment. Explicit existing administrator repair/reset is the separate restoration route.

**Legacy limitation:** previously generated bridge/substation cells generally have no R2 ownership ledger. They remain `LEGACY_UNRESOLVED`/provenance conflicts rather than being silently adopted or deleted. HP can change while an unproven geometry operation is parked; `/bobin destruction inspect` explains this. The candidate does not automatically clean historical static rubble or player edits. A safe separately reviewed provenance/adoption workflow would be needed for those cells; material matching alone cannot satisfy both guaranteed old-world destruction and proven ownership.

The red-circled screenshot's exact individual object ownership cannot be proven without its save and telemetry. Confirmed stage-mask artifacts are corrected at their source, but no arbitrary screenshot-selected world blocks are deleted.

### 3. Initial collision overlap can freeze a visible piece forever

Confirmed and reproduced against the actual baseline production kernel: `DebrisCollision.tick` detects an initial intersecting rotated AABB, sets linear/angular velocity to zero, and returns without a recoverable diagnostic. Each next tick repeats. The object stays nominally FALLING, so an aggregate sleeping count does not expose the failure.

Correction: `DebrisShape` retains each real VoxelShape sub-box's orientation. AABBs now provide broad phase, while separating-axis tests provide actual shape overlap, continuous fixed-orientation translation and real edge/face contact points. Other persistent debris also supply oriented shapes instead of solid enclosing AABBs. Empty corners of diagonal fragments cannot become support surfaces merely because their bounds overlap.

Initial overlap uses bounded collision-normal separation, at most eight 0.0125-block steps per solver visit, accepting only a reduction of total penetration. No guessed floor, source deletion, hidden fragment, disabled collision or ground teleport. An unresolved/imprisoned body enters `OVERLAP_RETRY`, retains its identity/momentum and retries; removal of the blocker resumes simulation. It is never classified as permanently settled on collision failure.

The included actual before/after probe reports:

```
supplied_baseline: bottom=-0.030000000000000027 phase=FALLING age=0 velocity=V[x=0.0, y=0.0, z=0.0]
correction: bottom=1.1102230246251565E-16 phase=SLEEPING age=12 velocity=V[x=0.0, y=0.0, z=0.0]
```

This is a deliberately intersecting test fixture, not proof that a particular recorded bridge fragment hit that branch.

### 4. Phantom support and recurring collision-budget starvation

Confirmed source risks: the old solver builds support hulls from rotated bounding rectangles, so support can lie outside the actual rotated piece. It treats other moving records' AABBs as solid. A full round-robin pass can consume the world-query budget on the same early records on successive ticks and perpetually defer late records. More than 128 bodies anywhere in the queried chunk buckets can trip the neighbor cap before checking actual broad overlap.

Correction: support polygons come from actual oriented contact intersections, with an upward supporting contact required. Unsupported centers generate tipping around a contact; removal of an actual support wakes the same body. The first budget-deferred record returns to the front for the next tick and ends the current pass. Sleeping fallback gets a bounded share. Cached body bounds filter unrelated same-chunk neighbors before the retained cap. Support dependants are notified for actual pose/geometry changes rather than every diagnostic revision.

Launch impulse/torque is calculated from the real accepted impact and source-cell positions/material weights, not a random UUID heading. Free rotation uses the volume/material-weighted center of mass; support tipping uses actual contact. Existing gravity, velocity caps and damping remain bounded. Material masses are relative gameplay weights, not calibrated physical units. World collision still queries native loaded `VoxelShape`s; water itself inserts no floor, so riverbed contact requires real solid geometry.

Persistent optional fields retain launch momentum, retries, successful-step count and last successful server simulation tick. Existing version-1 data remains decodable with defaults and existing identities/committed cells are not rebuilt or erased. No new entity per cell.

**Solver limitations:** translation is continuous for fixed orientations, but angular narrow phase is bounded sampling at up to 0.01 blocks of corner travel, with a 32-sample/substep ceiling and existing substep limit. This is not a mathematically complete continuous rotating convex solver; adversarial ultrathin angular contacts remain a required runtime/precision stress gate. Fragments collide against other records' current authoritative poses; this is not a simultaneous many-body momentum/constraint solver. Densely interlocked geometry or retained complexity limits can remain explicitly paused pending environmental change. No high-load TPS/FPS or complete collision acceptance is claimed.

### 5. Unsupported world-buffer usage and F1

Confirmed source/API mismatch: debris, temporary chips, crack overlays and severed NPC parts append native-model vertices in `AFTER_TRANSLUCENT` using `context.consumers()`. Fabric's documented contract says this event is after buffered draws and its geometry must be submitted immediately; LAST likewise expects immediate framebuffer work and supplies the camera view transform. Checking for `hudHidden` is not the only possible failure: late or unavailable consumers and later hand/HUD flushes can make visibility render-path-dependent.

Correction: these world-model passes use LAST with their own client-only `VertexConsumerProvider.Immediate`/`BufferBuilder`, and explicitly `draw()` in the same world callback. They never borrow the HUD's flush. Simulation remains on server/client ticks. NPC severed-part/corpse physics is unchanged; only its render submission lifecycle is corrected. Industrial and weapon particles continue through the existing native particle manager and do not acquire HUD callbacks.

**Runtime cause remains unconfirmed:** the supplied recordings were not accompanied by an event/renderer trace or a reproducible F1 pair. The source/API defect is fixed, but the report does not claim that toggling F1 was reproduced or that native particles/shader interactions are visually accepted. In particular, the exact shader/API/client-JAR combination responsible for the original disappearance is unknown.

API references (official primary sources):

- https://maven.fabricmc.net/docs/fabric-api-0.92.1%2B1.20.1/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.html (adjacent 1.20.1 API documentation; direct 0.92.2 documentation request was unavailable to the retrieval tool)
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/client/render/VertexConsumerProvider.html
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/client/render/VertexConsumerProvider.Immediate.html

These references establish method/event contracts, not a successful compilation of the pinned Fabric dependencies.

### 6. Pale rectangular explosion layers

Confirmed resource defect: `VoxelBlastClient -> ExplosionSmokeParticle` uses the sprite provider registered for `voxel_cloud`. That JSON selects a 16x16 tiled noise texture whose alpha is uniformly 255. Scaling/rotating this sprite for FP-1 FLASH/FIRE/DUST exposes opaque rectangular plates. This owner is distinct from the small material chips and persistent source-block meshes; the recorded pale quad/noise appearance is consistent with it. The original recording does not expose the event UUID, so exact per-quad attribution remains an inference.

Correction: retain the original texture asset, add `weapon_cloud_soft.png` with a transparent radial perimeter, and point only this weapon particle sprite list at it. No global particle-size reduction, source-fragment shrink or industrial sprite change. FP-1/GROM-7 layer schedules, radii, damage and smoke budgets stay byte-identical. Optional client event logging identifies the live visual owner/profile for independent comparison.

## Diagnostics

`/bobin destruction trace on` enables a 256-row bounded ring; `trace` shows the last 16; `trace off` clears/disables it. Rows identify candidate counts, event/source, impact position, actual RPG block normal when known, selected structure/component/zone, effective damage, HP and component HP before/after, stage changes and scheduled source operations. Rejections distinguish duplicate events, no hit zone, terminal target, dimension mismatch, excluded weapon policy, zero effective damage and unchanged health. Source reserve rows link event to operation; commit rows identify actual cells. Trace is off by default; no persistent log spam.

`/bobin destruction inspect` combines structure state and journal provenance/conflict status. Queue totals include the reused ledger path. `/bobin destruction debris [offset]` returns totals and pause-reason counts plus eight bounded detailed rows with identity, owner/operation, phase/resume, pose/quaternion, linear/angular velocity, support IDs, required chunks, retries, last successful simulation tick and recovery/active backlog. An incomplete source transfer is labeled SOURCE_COMMIT_PENDING.

Client VM option `-Dbobin.destruction.trace=true` adds weapon event/profile/render-owner console records. The server chat toggle does not set another process's system property. Every recorded gameplay pixel still needs a live identity/owner comparison; instrumentation does not retroactively supply missing video telemetry.

## Preserved scope and boundaries

Existing R2 policy, weapon damage configuration, explosion layer choreography, drone explosion dispatcher, industrial fire logic, industrial wind/color/budget kernels and industrial particle sprites are checked byte-identical. World generation geometry/materials is not redesigned; its observation scope alone changes. No dependency, physics engine, animation framework or new mixin is introduced.

The unjustified legacy cascade that kills other already-CRITICAL bridge sections on a crossing is retired. Existing primary component/global HP behavior remains coarse. No new load-path logic is substituted: real bridge dependency behavior remains Stage 6, subject to separate explicit authorization. No Blockbench/Blender asset or Stage 7 artistic animation work is done.

Real local RPG holes and conditional breach-origin fire/smoke are **design only**, in LOCALIZED_BREACH_DESIGN.md. That design defines data, damage/geometry interaction, unique source claims, durable commits, migration, rendering, fire budget ownership and additional acceptance cases. It needs scope approval before code implementation.

## Executed validation and blocked checks

Java runtime: OpenJDK 17.0.20. The installed JDK compiler module is used by the existing standalone runners; absence of a `javac` executable is handled by the comparison runner using `com.sun.tools.javac.Main`.

| Check | Actual status |
|---|---|
| Exact source ZIP identity | PASS; matches handoff hash, 2,356 baseline files |
| Original Stage 5 production-kernel runner | PASS: 21,955 checks |
| Corrected production kernels plus added regressions | PASS: 21,963 checks (actual final log included) |
| Actual baseline/corrected initial-overlap comparison | PASS; reproduced old stuck branch and corrected settling |
| Java 17 parse of all production/test sources | PASS; syntax only, not Minecraft/Fabric symbol resolution |
| R2 stabilization production kernels | PASS: 39,087 checks |
| Existing explosion/protocol assertions | PASS: 4,196 assertions |
| Existing Stage 2.1, smoke motion and refinery contracts | PASS: 60 profile/budget scenarios, 10,000 motion samples and existing four-size/four-variant refinery cases |
| Resource JSON, weapon alpha perimeter, protected R2 source/industrial sprite equality | PASS |
| Clean Fabric build / JUnit / GameTests / client / dedicated server | BLOCKED at Gradle 8.8 bootstrap: `UnknownHostException: services.gradle.org`; no mapped compilation or launch reached |
| Mixin application, side-isolation runtime, real native VoxelShape integration | NOT EXECUTED |
| F1, bridge/refinery/substation gameplay after-capture, multiplayer/drone view, restart phases | NOT EXECUTED |
| Server tick/client frame profiling, JFR, network/journal load acceptance | NOT EXECUTED; no Minecraft process available |
| Full-source vs patch per-file reconstruction | Verified by the included packaging verification log; no container-hash equality is substituted for file equality |

Added registered GameTests cover real generated contact/material coverage and actual manager collision-budget service, and the existing NBT fixture now exercises nondefault added fields. Existing actual slab/support-loss, partial commit, disk/NBT and future-format tests remain registered. **None of these Fabric GameTests executed here.** New pure regressions exercise oriented empty-hull corners, bounded initial-overlap recovery, explicit retry/resume, continuous thin rotated-obstacle sweep and rotated-debris false support.

All five requested Gradle commands were actually attempted, with separate genuine logs and a machine-readable runtime-status.json. They fail before project configuration/Java compilation. This candidate is not a substitute for completing those gates on a machine with dependencies available.

## Before/after acceptance matrix

| Original issue | Source correction / non-game evidence | Matched new gameplay capture |
|---|---|---|
| Missing bridge/substation response | Generated hit regions and ownership/stage alignment; new registered contact test; trace supplied | NOT EXECUTED |
| Suspended bridge pieces | Old overlap freeze reproduced; corrected fixture settles; oriented support and budget queue corrected | NOT EXECUTED; recorded piece identities still unknown |
| Angled part near pier | Oriented world/debris contacts and actual support polygon | NOT EXECUTED; legitimate resting contact remains possible |
| Artificial refinery columns | Original occupied-cell silhouette/material mask corrected; no speculative cleanup | NOT EXECUTED; per-object render owner requires live trace |
| Pale explosion plates | Original fully opaque sprite vs new transparent perimeter validated; event owner logging | NOT EXECUTED; recorded-owner attribution is inferential |
| F1 disappearance | Unsupported late shared-buffer submission replaced with explicit own world draw | NOT REPRODUCED; F1/shader matrix still required |
| Persistence/reconnect/load/late join/drone | Existing stable identities, WAL, source commits, observer stream retained; optional new fields tested only by registered tests | NOT EXECUTED |

## Independent review procedure and unresolved work

1. Verify/apply the exact Stage 5 patch or use the full project; do not overlay the earlier cumulative R2 patch. Build with Java 17, Gradle wrapper 8.8 and Loom 1.7.4, then run JUnit and runGametest separately.
2. Launch client and dedicated server; inspect mixin and client-only class loading. Compare the actual mod JAR/source build identity before attributing new footage to this candidate.
3. Test a copied provenance-bearing new world and the user's older save separately. Turn trace on, reproduce RPG deck/pier/beam, transformer/frame/equipment and refinery shell/roof hits, and retain per-hit event/operation/commit diagnostics. Unknown legacy ownership must remain explicit.
4. Reproduce original waterbed/near-pier cases with per-fragment pose/support logs. Test slabs, stairs, fence/wall/ceiling, stacking, initial overlap, unload/reload and disappearance of each real support. Stress bounded angular precision and large crowded buckets before accepting the solver.
5. Repeated F1 on/off with native explosion particles, industrial smoke/flame, temporary chips, falling/sleeping structural pieces and severed limbs, including actual shaders/fabulous settings. Record both view and simulation state.
6. Save/restart at RESERVED/ARMED/partial/ANIMATING/FALLING/SLEEPING/PAUSED; test new/old/future data, pending stage revisions, player edits, late joins, drone camera and dimensions. Profile actual server/client/network/journal load.
7. Capture matched new before/impact/after videos. Only then can visual/runtime acceptance change. No later stage begins automatically after a build.

Unresolved blockers include unavailable mapped build/runtime, unverified shader/F1 behavior, recorded per-object provenance, old-world ownership without a journal, angular sampling precision, very dense/interlocked contact states, genuine performance and multiplayer/restart evidence. These are explicit review gates, not completed features.
