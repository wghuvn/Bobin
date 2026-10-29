# R2 managed geometry and forbidden terrain sources

This is a source/geometry audit. The source-bound Python audit ran successfully; the new Fabric GameTest did not run because Gradle could not bootstrap. These are different evidence levels. Neither the index nor terrain policy was changed in R2.

## Exact call paths and edit gates

`DroneExplosion.explodeAt()` collects `BlastTerrain` before pressure/shrapnel and applies that collected plan afterwards. `StructureDamageSource.fromDroneBlast()` maps `DROPPED_GRENADE` to `hand_grenade`, HE/fragmentation bombs to `drone_payload`, and `HEAVY_STRUCTURAL_BOMB` to `heavy_drone_payload`. GROM-7 retains its explicit demolition exception. Hand-thrown and dropped grenade entities use this same explosion path. None of the four forbidden blast profiles equals `KAMIKAZE_BREACH` or `FP1_DEEP_STRIKE`.

| Mutation | Reachable for forbidden profiles | Protection/evidence |
| --- | --- | --- |
| Structural HP/component/fire/stage/transition | Rejected | Existing common direct-manager policy rejects all three source IDs before mutations; policy production-kernel checks pass. This does not depend on the selected support/deck/roof component. |
| Breach block list | No | `canBreach` requires KAMIKAZE_BREACH or FP1; these are allowed heavy/kamikaze sources, not forbidden payload profiles. |
| New fire list | No | The only `fires.add` call requires FP1 `megaStrike`. |
| Glass | Yes, ordinary terrain | Collection excludes any `findPrimaryAt` hit; collection and application also call `terrainAllowed`. Authored bridge materials are not shatterable glass. |
| Soil and plants above it | Yes, ordinary terrain | Collection checks the soil cell, application rechecks both soil and above-cell policy. Authored bridge materials are neither scorchable soil nor scorchable plants. |

`terrainAllowed()` samples the center of **each proposed block edit**, not the explosion center or nearest face of the hit entity. Attachments and a blast center on an unusual face therefore do not replace that per-cell check. Application repeats it. `findPrimaryAt()` checks the impact chunk and eight neighbors, uses hit-zone tolerance 1.25, then chooses the closest origin among matches. Registration buckets cover overall bounds expanded by 2 blocks. Region selection eventually falls back to HULL inside the permitted overall envelope (subject to the tank radial check); a gap between named hit regions is not automatically an unprotected hole.

## Refinery: source-bound coordinate evaluation

The registry and `IndustrialFacilities.tank()` use radii/heights `(8,10), (6,8), (7,11), (5,9)`. `StructureGeometrySnapshot.includes()` uses `RefineryGeometryRules.includes()` for the authoritative capture, including the circular base, hollow cylinder, east ladder, south pipe/panel and top hopper. The static audit exhaustively enumerated that mask, including explicit air. All 6,166 reference-mask cells have centers inside the primary-match envelope and radial limit. It also passed 43,162 center/near-face probes at offsets ±0.499, using negative coordinates crossing chunk boundaries. No reference/attachment cell was missed.

Counts by tank: 2,289; 1,103; 1,894; 880. These are mask cells including air, **not claimed counts of generated solid blocks**. This result follows the checked-in coordinate equations. It does not assert that Minecraft has loaded these chunks or that a running data pack uses the expected tags.

## Bridges: important geometric distinction

The three current v10 crossings include both orientations and concrete, truss and timber styles. Logical sections start three blocks inside each abutment; their support envelope extends 14 blocks below the section origin. Consequently `findPrimaryAt()` is **not an exact ownership lookup for every generated bridge block**. Some endpoint deck/parapet cells and potentially deep pier/abutment cells lie outside its expanded envelopes. For example, the central bridge's deck cell `(0,68,-15)` is authored cracked asphalt and lies outside those envelopes. The source-bound audit reports these differences instead of calling the index geometrically exact.

The conservative candidate sets in v10 contain 2,125 / 2,325 / 2,155 cells, respectively. Pier/abutment columns were evaluated down to the blueprint floor Y=40; actual columns start at sampled natural terrain, so those counts are upper sets, not actual generated counts. Older baked layouts were also evaluated. Full per-part counts are in `R2-geometry-audit.log`.

This mismatch does not expose a forbidden-source mutation in the current authored geometry: the forbidden profiles cannot populate breach or fire lists, and bridge source/stage materials (including abutments, trusses, pier caps, ruined steps/slabs and riverbed debris) are concrete/asphalt/gravel, logs, metal, bars, terracotta, stone slabs or stairs. They are not the current glass/soil/plant targets. The material set has empty intersection with all explicit target lists. A timber log is not a plank, door, fence or plant; furthermore the forbidden profiles do not take the breach branch regardless of material. Tank reference cells additionally have the per-cell policy guard even if an authoritative site blueprint overwrites a material.

Expanding `findPrimaryAt` to the entire bridge blueprint bounding box would also capture the riverbed, open water and ordinary terrain between supports. That would change terrain/gameplay semantics without fixing an observed reachable forbidden mutation. R2 leaves both production methods byte-identical to R1 and the original index byte-identical to the baseline. It does not expand protection radii, introduce a terrain halo, change tags or alter the generator. Existing R1 envelope tolerances remain as before.

## Executable evidence and limits

`audit_geometry.py` pins the source equations and gates with SHA-256 hashes, evaluates their coordinate bounds, checks chunk coverage, checks the smoke selector wiring, and checks the explicit target lists. Its output is a static audit, not execution of `Box`, `BlockState`, the index or the terrain collector. No fake Minecraft classes or replacement runtime was used.

`stabilizationManagedGeometryTerrainPolicy` is a real registered Fabric GameTest. It traverses actual authoritative refinery snapshots and all four stages, all actual full bridge blueprints and each bridge section stage; obtains real `BlockState` tag membership; invokes the unchanged private `terrainAllowed()`; calls the actual `findPrimaryAt()`; and rejects any forbidden-source mutable structural cell left unprotected. It also checks that a distant ordinary-terrain position remains allowed. That test will additionally catch changes in nested/dynamic tags. It has **NOT EXECUTED** here.

The existing manager-level GameTest still checks forbidden HP/component/fire/stage/transition mutation through normal/direct calls and delayed ticks. Actual before/after block snapshots for explosions placed at every boundary/attachment, data-pack changes, in-world visibility, chunk lifecycle, save/reload and multiplayer remain **NOT EXECUTED**. The conclusion is supported for the current authored content and source gates; runtime behavior and externally modified tags remain verification gates. No runtime success is inferred from the static audit.
