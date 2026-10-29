# Bobin Craft — Stage 5 implementation candidate

Date: 2026-10-27. Minecraft 1.20.1, Fabric, Java 17, Loom 1.7.4, Gradle 8.8. Baseline: Minecraft-approved stabilization R2, full-source ZIP SHA-256 `bc6471e819a4bd1bbde0cd0958cbfcf8272748e3f48dd96efe51dc36f695344f`, all 2,325 project hashes verified before implementation.

**Stage 5 source implementation is delivered for review. Full Fabric compilation and Minecraft runtime/visual acceptance are NOT VERIFIED. Stages 6 and 7 have not started.** The user's approval of R2 in Minecraft is baseline evidence; it is not evidence that these new Stage 5 adapters have run in Minecraft.

## Delivered behavior and ownership

Scripted structural removals now reserve source-derived fragment records before removing blocks. The server owns source commits, poses, physics, support contacts, revisions and persistence. Clients render current authoritative block models with interpolation; they cannot choose a floor, bake an aftermath pile, move a fragment on their own or replay a collapse on late join.

No entity-per-fragment physics, new physics engine, world debris blocks, item drops, collision against players/vehicles, debris damage, new world layout, bridge load graph or artistic animation redesign was added. Actual block geometry remains the source of each significant fragment. Existing small impact chips remain transient and byte-identical to R2.

### Source removal and retargeting

`StructureUpdateQueue` integrates the existing non-refinery template authorization with a finite revisioned reservation. Live block states at eligible loaded source positions supply exact registry names/properties and collision/outline geometry. This preserves R2's existing template authorization rather than introducing a broad new block-destruction policy. Block entities are protected except the existing supported empty-hopper case. Refinery removals retain the exact R2 ownership/protection ledger gate.

Reservations carry dimension, structure ID, operation UUID, fragment index and a derived stable UUID. A cell begins RESERVED, can become ARMED, and cannot be removed until its intent is durably recorded. Actual successful removal changes that cell to REMOVED. Rejected/protected cells become CONFLICT and are excluded from the represented geometry. Only REMOVED cells are visible. A partial patch remains at its true source pose until its remaining decisions are resolved; unavailable source chunks pause it with a recorded reason. It never displays still-present source constituents as debris.

A new target can supersede an unfinished target. Uncommitted constituents transfer to a new reservation while the old reservation is retired in the same journal barrier. A globally indexed already-removed source cannot be claimed again. Protected source changes remain protected across subsequent operations. Later building at an old source position does not erase an already committed physical fragment. Source edits observed before an operation existed cannot retrospectively acquire a new ownership history: pre-Stage-5 non-refinery edits retain R2 template semantics. No legacy provenance is invented.

Capacity or journal failure defers detachment before removal. Non-refinery blocked candidates rotate through the bounded queue so they cannot permanently prevent later cells of an already-reserved connected patch from completing. Candidate checks, not just successful writes, consume the per-tick transition budget. The refinery's parked legacy/conflict scheduling remains intact: plans rebuild on target/provenance changes and bounded retries, not by rebuilding every parked ledger each tick.

### Real geometry and materials

`DebrisPlan` groups face-connected actual source cells into patches with at most 16 cells in a 4×2×4 partition. Roof portions can form recognizable 4×4 plates; walls and attachments form bounded connected strips instead of long synthetic model columns. Material changes within a patch are preserved per constituent. Initial pivot/local coordinates reconstruct the original world coordinates exactly, without a scale factor or random spawn displacement.

The block models and their registry/property state remain native. The renderer does not stretch a whole block model into the dimensions of a generic animation cube. Collision subshapes are captured from the actual source `VoxelShape`; outline geometry supplies non-colliding source models, with a conservative source-cell physical envelope only if both shapes are empty. This last envelope affects collision, not visible model dimensions. Excess subshape counts defer detachment rather than silently dropping geometry.

## Vertical block-cluster investigation

### Evidence available

The two accessible supplied files are `2026-10-27_20.06.27.png` and `2026-10-27_20.16.40.png`. They show impact fragments at a wall and beneath a slab. Neither accessible image contains the described refinery view with red circles. A search did not resolve another relevant refinery screenshot. Consequently **the exact circled object cannot be attributed from the available images**. No claim of matching its pixels or of visual acceptance is made.

The source trace independently establishes four different representations:

| Representation in R2 | Source and finding | Stage 5 action |
| --- | --- | --- |
| Ordinary weapon chips | `ImpactDebrisClient`; small client-only source-state chips, already repaired in stabilization | Preserved byte for byte; not promoted into meaningful persistent wreckage |
| Active structural bones | `DestructionKeyframeAnimator.renderActive`; keyframe cubes rendered by scaling complete block models, independently of actual source-cell commit | Competing geometry owner removed from the live path; actual committed patches replace it |
| Animator aftermath | `bakeSequenceToAftermath`, `SETTLED_WRECKAGE`; generic final cubes fabricated from clip dimensions, ground-column fallback, render-time height correction and eviction at 96 pieces | Removed; no new aftermath buffer or fake landing is substituted |
| World ruin geometry | `StructureGeometrySnapshot.stage` and `StructureCollapseManager` | Legitimate surviving source geometry retained; source-less new decorative bridge piles filtered so they cannot duplicate physical fragments |

The refinery model contains roof cubes of 8×0.625×4 blocks, shell cubes of 9×3.25×1, and a manifold cube of 1.5×3×1.25 after the loader's division by 16. The old renderer scaled block models to these generic dimensions, applied keyframes, then looked down one pivot column over 28 blocks with a fallback height. It could display geometry that had not actually been removed, resolve a pivot against unrelated overhead geometry, and create another representation at clip completion. This is a concrete code-level source of synthetic blocky structural forms; it is **not sufficient evidence to identify a particular unavailable circled pixel**.

The Stage 5 lifecycle adapter now lets stage packets own crack/dust feedback only. Geometry passes directly from committed source pose through the short server ANIMATING handoff into physical FALLING/SLEEPING records. Legacy assets remain available for the later Stage 7 redesign, but their generic cubes are no longer a competing live owner. This is the minimum ownership correction, not a new artistic clip implementation. The old `commitTickOffset` does not authorize geometry visibility; actual cell commits do.

The existing refinery stage mask/reference extraction and all six model/animation JSON assets are unchanged. Surviving shell/base blocks are not mass-hidden or deleted. Existing world blocks from older saves without ownership evidence are not swept away on the assumption that they are fake rubble. Any such legacy static defect must be identified using its block/state/position and provenance before cleanup. No random replacement pile was generated.

## Server physics and support

`DebrisCollision` is a deterministic server-record solver. Linear velocity is bounded to 1.5 blocks/tick, angular velocity to 0.12 radians/tick, and movement is split into at most 16 substeps. Broad-phase bounds include motion and rotation. Each captured component subshape is transformed as a volume; swept axis clipping checks floors, sides and ceilings against actual loaded Minecraft collision sub-boxes.

Angular sweeps compute extrema of rotating corners over the whole interval, including intermediate arc extrema. A bounded rotational contact search reaches contact instead of accepting a tunneling orientation. Multiple supporting contacts form a support hull, and loss of stable support introduces tipping about a surviving contact. Verified low motion/contact stability is required for sleep. Metal-containing patches have a different initial angular response; this is a bounded game approximation, not a full material fracture/elasticity model.

Other authoritative debris records participate through dimension-local body chunk buckets. Sleeping bodies can support other bodies; motion/removal notifies the reverse support index. These bodies do not enter Minecraft's entity collision APIs and do not obstruct players or tanks.

Water has no solid collision plane in this solver. A fragment falls toward actual riverbed geometry; no sea-level or guessed terrain height is substituted. Buoyancy was deliberately outside the approved Stage 5 scope.

The solver uses conservative rotated subshape AABBs. It may reject a very tight rotation or passage earlier than an exact oriented-polyhedron solver. Initial/external-placement overlap is held safely rather than corrected by a guessed upward teleport. Collision complexity/border/resource exhaustion can pause at the last pose, with diagnostics. These behaviors and dense contact tuning need real-game acceptance; kernel passes do not establish that every arrangement looks convincing.

### Support loss and unloaded chunks

The existing mapped successful `WorldChunk.setBlockState` observer is extended; no new mixin or selector was introduced. It covers block writes from removal/placement, pistons and explosions through that path. Exact support cells map to records. Notifications are coalesced and reverse-index traversal has a true per-fragment cursor budget, including the crowded-support case. Sleeping records also have a bounded rotating fallback check.

Off-thread notifications are marshalled through a bounded coalesced queue. Overflow protects unresolved source reservations conservatively. Main-thread edits outside indexed sources/supports have a fast exit. A support change wakes the existing pose into falling; rendering never changes its Y coordinate.

If any swept collision chunk is missing, the fragment pauses with a saved resume phase and chunk dependencies. It does not request chunk loads. Source recovery is likewise checked before a record becomes visible. Unreconciled saved body bounds defer neighboring body simulation rather than allowing new fragments to fall through a not-yet-recovered body.

## Persistence and crash handling

New data lives separately under the world's `data/bobin_craft_debris/`, keyed by a UUID derived from the dimension ID. Version-1 debris snapshots and an append-only WAL leave the existing structure schema 2, refinery journal 1 and geometry revision 2 unchanged.

WAL frames have a sequence, length and CRC. An intent barrier uses actual file writes and `FileChannel.force` before source removal becomes eligible. Incremental frames serialize changed records and operation/proof changes rather than rewriting every sleeping fragment for every detachment. Pose checkpoints occur on the bounded server cadence; source intents force earlier barriers. Compaction writes an atomic snapshot, and its sequence prevents old WAL frames from replaying if a crash occurs before WAL truncation.

A truncated final frame retains the valid prefix; the next successful append truncates only that incomplete tail. Corrupt complete frames, invalid identities/source claims, unsupported formats or failed IO disable further detachments and preserve files. Meaningful old wreckage is not evicted to make room.

Recovery distinguishes unarmed reservations from durable intents. A saved target-air outcome can confirm an armed removal once. If chunk storage rolled back an exact authorized source state, recovery can reapply that removal; unknown/protected/container conflicts cannot authorize removal. Already committed pieces are retained when a tracked later placement changes their old source. Repeated reconciliation uses the same identity instead of creating a second body.

The WAL also stores the known structure snapshot accompanying a new removal plan. A newer authoritative proof can restore structure progress when the independent legacy structure save lagged behind a durable detachment intent. It uses the existing compact serializer and marks the state as loaded without replaying the explosion. It does not infer unknown legacy component HP or overwrite unsupported future structure data.

This is not a claim of universal atomicity for every hardware/power failure or every unrelated vanilla write. The journal layer was tested with real filesystem writes and interruption/corruption cases; actual Minecraft process-kill/save-order combinations and same-state external edits at crash boundaries remain runtime verification gates. Pose persistence can resume from the last durable pose checkpoint rather than the final unsaved tick. No explosion is replayed to reconstruct the pose.

Explicit operator resets/repairs clear the corresponding fragment ownership through a durable barrier before rebuilding source geometry. A failed barrier prevents that reset from silently discarding the records.

## Multiplayer and rendering

`structure_debris_v1` is a new cosmetic S2C channel. Initial/late observers receive paged current state, then bounded corrections and visibility tombstones. A stream epoch and sequence prevent old connection/dimension data from resurrecting a representation; persistent geometry revisions order changes to a fragment. A returning observer can receive the same persistent identity again. No historical detonation or animation event is sent as a reconstruction mechanism.

Observer distance uses the existing `DroneNetworking.viewOrigin(player)`. Drone controls, sessions, chunk tickets and streaming implementation were not changed. Moving records receive priority, while paged stored-state service retains a share of service. Under load, packet limits take precedence over correction frequency.

The client validates payload size, version, identity fields, phase, finite pose, geometry/material limits and ordering. Unknown materials do not become cobblestone substitutes. Only committed source cells are drawn. Rendering samples lighting, interpolates authoritative poses and uses native block-model dimensions. Missing client chunks defer drawing; they do not cause a local invented landing.

| Safeguard | Current bound/behavior |
| --- | --- |
| Persistent records | 8,192 per dimension; no meaningful-remnant eviction |
| Stored source cells | 65,536 |
| Patch/subshapes | 16 cells, 8 subshapes per cell |
| Newly reserved active work | 32 by default; detachment waits at capacity |
| Active simulation visits | 32 per tick, plus a separate bounded sleeping-support fallback |
| World collision query work | 16,384 block-query units/tick, with body-query work charged too |
| Per-query complexity | 4,096 block-volume/obstacle bounds and 128 neighboring bodies |
| Recovery | 8 records visited, at most 16 replayed source writes/tick |
| Support notifications | 32 per-fragment cursor steps/tick; sleeping fallback visits 8 |
| Async observed writes | Coalesced queue capped at 16,384; bounded ingestion |
| Network | 24,576-byte maximum page; 32,768 bytes and 16 messages/player/tick |
| Client meshes | 512 fragments and 2,048 block models/frame; stored identities retained separately |
| Journal | 32 MiB frame limit, 128 MiB WAL guard; compaction after 8 MiB |

Dense/missing-chunk workloads can create a visible backlog or pause; they do not silently delete existing debris or remove unreserved source geometry. Use `/bobin destruction debris` to inspect stored/visible/paused/reserved counts, query work, deferrals, writable state and journal timing/bytes. These are safeguards, not measured FPS/TPS guarantees.

## Files and validation

The exact manifest names every addition/modification and every final hash. No original R2 project files are deleted. Ten existing Java files change for integration; new debris/domain/client/test files and Stage 5 documentation supply the implementation. All other R2 source/resources are byte-identical, including world-generation inputs, gameplay damage prefix, stabilization policy, smoke budget/appearance, audio/motor assets, temporary chips and all six artistic destruction assets.

Executed:

- Java 17 syntax parse of all 316 main/test Java files. This does not resolve Minecraft/Fabric symbols.
- The unchanged stabilization kernel suite: **39,087 checks**, plus the existing explosion/protocol, 60 profile/budget scenarios, 10,000 motion samples and refinery transition contracts.
- Stage 5 production kernels: **21,955 checks**. These execute the actual planner, collision solver, lifecycle/revision rules, support index and filesystem journal. The connected tank fixture conserves all 813 constituent cells in 172 patches. This is a deterministic test fixture, not a claimed screenshot or an exported running Minecraft tank.
- Real file-journal append/read, all tested final-frame truncation offsets, CRC rejection without rewriting evidence, atomic snapshot replacement, post-checkpoint sequences and an actual filesystem write-failure case.
- Resource/metadata/NBT checks and exact R2 protected-source comparisons.
- Patch reconstruction, idempotence, altered-baseline/corrupt-payload rejection, injected-copy rollback, CRC and final archive hash equality (see packaging log).

Real commands attempted: `bash gradlew clean build -x test`, `bash gradlew test`, `bash gradlew runGametest`, `bash gradlew runClient`, `bash gradlew runServer`. All stopped during Gradle 8.8 bootstrap with `UnknownHostException: services.gradle.org`. No compilation or Minecraft launch occurred. Pinned Yarn API documentation was consulted; that does not replace a successful build or mixin launch.

The old R2 source-bound geometry audit pins the old collapse-manager hash and remains historical R2 evidence. Stage 5 legitimately changes the collapse-manager's source-less pile path; the new scope checks and real-geometry GameTest cover that new responsibility. It is not relabelled as a current runtime pass.

Added real registered GameTests cover NBT phases/network encoding, durable disk and future-format preservation, duplicate commit and rolled-back source recovery, protected partial commits, actual slab collision/support removal, and absence of new source-less bridge piles while preserving surviving source geometry. **None of these GameTests executed here.** Existing R2 GameTests also remain unexecuted in this environment.

**MINECRAFT RUNTIME TESTS: NOT EXECUTED. VISUAL ACCEPTANCE: NOT ESTABLISHED.**

## Required review/runtime matrix

On a copied save, resolve the pinned toolchain and run the build, JUnit and configured GameTests separately. Then test two real clients plus drone observation. Do not treat the pure kernel count as any of these checks.

| Scenario | Required evidence | Status here |
| --- | --- | --- |
| Refinery damage/destruction with all four tank sizes | Before/after block ownership, recognizable roof/wall/attachments, no synthetic tall model columns, no duplicate parts | NOT EXECUTED |
| Exact red-circled objects | Original marked screenshots plus matched world position/representation and new in-game screenshots/video | Images not available; NOT VERIFIED |
| Floor, slab, stairs, fences, walls, ceiling, rotation and narrow passages | No tunneling, guessed heights, floating or implausible settling; inspect conservative-collision restrictions | NOT EXECUTED |
| Deep water | Falling to actual bed, no sea-level landing | NOT EXECUTED |
| Support loss by piston/explosion/player removal, including stacked bodies | Existing pose wakes; no teleport or invented replacement | NOT EXECUTED |
| Save/restart during every phase and partial mixed commit | Same identities, materials and geometry once; no explosion replay | NOT EXECUTED |
| Process termination at chunk/journal write-order cuts | Reconcile conservative source outcomes; inspect external same-state edit boundaries | NOT EXECUTED |
| Late join, remote drone, disconnect and dimension return | Current paged snapshots, no duplicate old representation or explosion | NOT EXECUTED |
| Active/storage/network capacity and dense rubble | Deferred source removal, retained meaningful remnants, bounded work and acceptable TPS/FPS | NOT EXECUTED |
| Legacy saves without debris records and future versions | No retrospective guessed fragments or broad block cleanup; unsupported data preserved | NOT EXECUTED |
| All R2 gameplay regressions | Grenade policy, fire/smoke allocation, audio, tank/drone/NPC/hub/raid behavior unchanged | Source/kernel checks PASS; new runtime NOT EXECUTED |

The incremental patch targets the exact full R2 source checkpoint above, not the original 2,278-file archive or an R1 tree. Extract the patch separately and run `python apply_stage5_patch.py PATH/TO/Bobin_craft`. The full source ZIP is independently usable. No built mod JAR is claimed.

Stage 5 is submitted as a source implementation candidate with the listed verification gates. Work stops for independent review. **Stage 6 and Stage 7 NOT STARTED.**
