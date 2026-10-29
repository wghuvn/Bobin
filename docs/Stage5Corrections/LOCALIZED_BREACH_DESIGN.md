# Required follow-up: impact-local openings and conditional breach emitters

Status: DESIGN ONLY. Implementation requires the user's explicit scope approval. Stage 6 support graphs and Stage 7 art are separate and unstarted. This document does not claim that coarse Stage 5 HP transitions create RPG wall holes.

## Entry point and damage budget

Keep `RpgProjectileEntity.detonateAt -> StructureDamageManager.applyDamage` as the one RPG structural event. `DroneExplosion` must keep excluding the already-dispatched RPG. Add local planning inside the accepted, deduplicated damage transaction, not as another explosion or HP deduction. Retain `StructureDamagePolicy` at the common boundary: hand grenades and drone-dropped payloads cannot damage bridge/refinery structural HP or bypass that exclusion through a local operation.

The accepted event supplies dimension, event UUID, attacker, actual surface position/normal, source kind, effective game damage and component. The correction already attaches a block-surface normal to RPG events when the actual block raycast supplies it. Other sources with no valid normal must supply a real loaded raycast or use nonpenetrating impact feedback; never infer penetration from the structure center. Entity hits must not become guessed tank-wall hits.

Introduce a small pure planner `StructureLocalDamage` called by `StructureDamageManager` after target/policy validation. It returns a bounded plan, not world edits. Inputs include current component HP, actual source-cell geometry/state, prior local wear and protection. Keep the existing HP amounts and source multipliers. Material profiles map **existing game damage units**, not real-world energy or ammunition design, to resistance/threshold/maximum affected cells. Differentiate thin sheet/steel equipment/concrete/timber through registered block identities or data tags. Default unknown materials to no penetration. No global damage multiplier changes.

Proposed limits for initial review: inspect at most 256 candidate source cells/event; authorize at most 32 new detached cells/event; process through existing write/query budgets; merge cosmetic opening descriptors under a separate bounded per-structure cap. These are proposed product safeguards, to be measured before approval of final values. Retain all existing fragment/storage/network caps; reject/defer before mutation at capacity.

## Geometry, thickness and accumulated local damage

1. Resolve the impacted owned source cell using actual block collision/outline shape and the validated surface normal, allowing exact slab/panel boundaries. The real Java-generated blueprint supplies the reference; a coarse hit-zone box alone is insufficient ownership.
2. Traverse a bounded short corridor through eligible source geometry along the impact direction. Track material resistance and thickness per occupied cell. Stop at protection, block entities, unloaded data, nonstructure geometry, exhausted inspection budget or exhausted damage budget. A weak hit adds bounded per-cell wear without a through-opening.
3. Select adjacent damaged cells with deterministic surface-distance/material rules. Preserve surrounding geometry. Persist wear so repeated nearby hits can enlarge/connect openings; a detached cell is never charged or removed twice. Connected removed cells become source-derived fragment patches using `DebrisPlan`, with their exact native state/shape.
4. A planned opening becomes an opening **only after source commits**. Actual world AIR and native collision, not a decal, establish the hole. Do not render intact wall meshes over committed cells. Ordinary Minecraft block updates remain the geometry transport.
5. HP-stage changes must calculate `target stage minus every durably detached local cell`. A later coarse transition cannot heal a hole or duplicate its already detached material. The correction's `committedSource` guard is an initial integration point, not the localized feature itself.

## One ownership and commit pipeline

Reuse the existing `RefineryTransitionState`/`TransitionLedger` provenance and protected-cell semantics, now used for tanks, bridge sections and substation parts. Existing known generated ownership is required. A matching material in an old world is not proof of ownership. Keep unresolved legacy cells parked and visible in diagnostics. Do not automatically adopt them.

Each local operation has key `breach/<event UUID>` under the parent structure, plus a reference/source revision and the validated desired state of each affected cell. Map it into the same authoritative desired-cell reconciliation path used by stage transitions. It needs an explicit operation-kind field in scheduling so coarse retargets cannot replace an unfinished local operation's identity or reservations. This is an extension of the existing ledger, not a competing queue or damage engine.

For each source: proven ownership -> fragment/storage reservation -> captured exact state and VoxelShape -> durable ARMED journal barrier -> revalidate the live state/protection -> real block removal -> REMOVED commit -> existing fragment physics. Failures preserve source and retry/park through existing budgets. Unknown/protected/container cells do not produce detached meshes. Update HP once per accepted event even if geometry is queued; expose pending/partially committed geometry separately instead of pretending the hole already exists.

The same world cell has a unique source claim across local/stage operations. Extend `bySource`/`DebrisLifecycle` conflict rules rather than maintaining another removed-cell authority. If another event encounters ARMED or REMOVED cells, coalesce wear or reference the existing commit; never create a second fragment for them. Retargeting cannot revoke a durable removal or grant ownership of a player replacement.

## Save format and crash recovery

Add an optional `LocalDamage` compound to the existing structure snapshot, with its own version 1: bounded per-cell wear, committed local mask, operation/event IDs, material/reference revision, pending intents and compact opening descriptors derived from committed cells. Missing compound means no known historic local damage; do not invent it from global HP. Preserve unsupported nested data and disable local mutation conservatively rather than overwriting it.

Include this same compound in the existing debris WAL's structure proof. Extend proof restoration to reconcile the known compound before rebuilding target maps. Avoid a new independent breach journal whose save ordering could disagree with the current fragment WAL and native chunk saves. Optional format changes require actual old/new/future NBT and network tests before shipping.

Recovery cases:

- Before durable ARMED: retain source, no visible fragment/hole.
- ARMED but source still expected: replay only the exact authorized, unprotected state, under write budget.
- Source AIR after an interrupted removal: confirm the existing identity once.
- Source replaced by a player/container/protected edit: conflict/park; never delete or invent a fragment.
- REMOVED piece saved with an older chunk: replay the exact saved removal subject to the existing provenance rules; do not replay the explosion.
- Partially committed opening: reconstruct only its actually committed cells and emitter area; the pending remainder stays pending.

Persist wear/commit masks before optional visual descriptors. An opening descriptor can be recomputed from the committed source mask; it cannot authorize another removal. Retain significant wreckage and its pose across unload/restart/late joins exactly as Stage 5 does.

## Breach-dependent fire and smoke

Keep `StructureFireManager` as the owner of burning state, ignition, intensity and remaining duration. No new permanent fire is created merely because a hole exists. Existing source kind/ignition rules determine whether the tank is burning. Nonburning hits receive only ordinary impact VFX.

Create `BreachEmissionBoundary` descriptors from connected committed opening cells: opening ID/revision, centroid, outward normal, actual open area, bounded local extents and an interior sample point. Validate that the opening is still open in loaded world geometry. If a protected player repair occludes it, stop vent emission without deleting its persistent debris.

Extend the existing industrial emitter selection to divide a tank's **existing allocated particle budget** between central plume and eligible openings. Sum of emissions must remain within `IndustrialSmokeBudget` and the current global limits. Do not independently budget a second cloud per hole. Cap active opening emitters and combine nearby openings deterministically; unused descriptors stay persistent and can be selected later.

Sample opening centroid/interior and size the emission envelope from open area and current fire intensity. Apply the existing wind/turbulence motion after the initial outward/upward vent direction. Keep accepted industrial palettes, large central plume appearance, lifetime rules and R2 smoke allocation. Flame emission stops when the underlying fire ends. Nonburning bridges/substations do not receive refinery clouds.

Client metadata: world block updates carry holes; current fire state remains on the existing stream. Send optional versioned opening descriptors through a bounded companion S2C channel or a negotiated versioned extension, not unversioned appended bytes on an existing packet. Carry dimension/session, parent ID, opening ID/revision and tombstones. Late/drone observers receive current committed boundaries; no historical detonation replay. Incomplete/missing metadata can delay optional vent VFX but cannot draw an intact wall or change server collision.

## Implementation order and approval checkpoint

After explicit approval of this design/scope: implement planner/material profiles and durable local wear/operations; integrate stage/local reconciliation and fragment conservation; add conditional emitter descriptors/budget sharing; implement save/network migration; run the matrix below and actual Minecraft comparisons. This milestone must not silently include bridge support graph or art redesign. Stage 6 then consumes the same committed-source operations for topology-driven support loss under its own authorization.

## Required acceptance matrix

- Weak/strong RPG wall hits, roof hits and grazing hits at actual normals; concrete vs sheet vs mixed thickness; blocked penetration and ordinary terrain negative cases.
- Repeated same/nearby events, duplicate event UUID, simultaneous hits and stage thresholds: exactly one HP event and one removal/fragment per source cell; surrounding wall intact.
- Slabs/attachments/fences, hollow interior, thin boundaries, impact on a player replacement/container, stale source revision and unknown legacy ownership.
- Capacity/backlog, unloaded source/target chunks and late block-update delivery: no early hole/mesh, no forced chunk loading or ghost wall.
- Crash/save/reload at every intent/commit phase and during partial openings; old saves without LocalDamage; unknown future format preserved.
- Burning/nonburning/newly ignited/extinguished tanks, small/merged/blocked openings, wind changes; total emission never exceeds existing allocation; no bridge industrial cloud.
- Late join, reconnect, dimension change, drone camera, lost/out-of-order descriptors and F1; actual blocks/collision/opening VFX agree for both clients.
- Full mapped build, JUnit, registered GameTests, dedicated/client launches, profiler and frame comparisons. Pure Java geometry tests do not establish this acceptance.
