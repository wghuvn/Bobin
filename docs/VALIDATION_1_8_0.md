# 1.8.0 validation

Target: Minecraft 1.20.1, Yarn 1.20.1+build.10, Java 17, Fabric Loader 0.15.11,
Fabric API 0.92.2+1.20.1. Production sources compile using the cached dependency
pipeline with real Fabric access wideners, TinyRemapper and MixinExtension.
The regular Gradle build remains supported; this host uses the existing offline
fallback to avoid its previously recorded Loom socket-probe limitation.

## Results

| Check | Result |
| --- | --- |
| Full JUnit suite | 269 passed, 0 failed, 0 skipped |
| Final focused drone / surface-mark checks | 48 passed, 0 failed, 0 skipped |
| Production remapping audit | 14 Mixins, 22 selectors, 4 shadows, 3 invocation targets, 1 constant target; passed |
| Drone resources | 7 items/recipes, native soldier egg, 2 creative tabs, atlas, 33 Java translation references and 19 registry labels in 3 languages; passed |
| Deterministic drone assets | Regeneration check passed |
| Combat resources | 17 translation keys in 3 languages, 10 textures, 2 particle atlases, recipe, damage types and terrain tags; passed |
| Deterministic combat assets | Regeneration check passed; new decal pixels inspected at nearest-neighbour scale |

The final focused run follows the last release-bay alignment correction and
covers the affected drop profile and renderer geometry, plus the new HUD, signal,
video policy, screen input, tracked variants, optics, fuses and persistent marks.

## Coverage

HUD projection tests use native Minecraft boxes and JOML transforms to check the
actual view-matrix rotation order, four headings, downward view, bank, behind-camera
rejection and optical zoom scaling. Screen tests exercise the production handler:
help starts hidden, H is explicit, zoom is bounded and does not queue flight
commands. Existing click/focus/cursor safety, FPV near-plane/probe and physical
camera geometry regressions remain covered. OpenGL is not invoked by these tests.

Signal tests check a clean 15-block link with thin-wall/foliage obstruction, retained
loss behind deep terrain, smooth distance falloff, invalid values, hard range and
the 40-tick loss grace. Video policy checks retain native pixel resolution while
varying cadence, latency, held-frame selection, corruption and blackouts.

Payload tests cover the ordinary 60-tick fuse, HE arming/contact/fallback and
fragmentation arming/low-airburst/contact/fallback. The three fall paths and motion
inheritance differ. Every release remains wholly below the carrier; side bays and
the third fragmentation bay have distinct positions. Native model vertices at
multiple yaw angles stay inside the 0.36 × 0.48 block dropped-charge collider and
rest on its bottom. Variant NBT clamps saved counts to 3/2/3, preserves type and
blast profile and resolves the correct recovery item. Previously loaded live
charges remain inert by the existing server policy.

Persistent marks are tested through NBT round trips, bounded FIFO eviction,
invalid-entry rejection, unknown future-version preservation and explicit removal.
Network field round trips reject invalid sides and non-finite coordinates.
Six-face geometry checks verify outward winding, bounded UVs and clipping to slab
edges; a changed slab height invalidates its old surface. Scorched soil is treated
as the same support material, while removed/changed supporting blocks are rejected.
Snapshots are bounded to 512 marks; the saved FIFO is bounded to 4,096 per dimension.

The full suite retains damage caps, shrapnel injury energy, NPC injury poses,
neutral hurt feedback, world generation, weapons, hub, raids, guard rosters,
bleeding/treatment and persistence checks. These are automated policy, geometry,
tracked-entity and serialization tests, not a live combat or multiplayer simulation.

## Release verification

The final production JAR passes CRC and duplicate-path checks, has version 1.8.0
metadata, and matches all 1,675 files in the remapped production staging tree.
Current source resources match their embedded counterparts (the version-filtered
Fabric metadata is checked separately). New HUD, payload-profile and mark classes
and both 16×16 alpha decal textures are included. Tests and smoke-harness classes
are excluded.

```text
bobin_craft-1.8.0.jar
Size: 3,964,772 bytes
SHA-256: 58449c5f4ed551b953067ea4aa1c49d51afd02008b40390b64e931b69f02b82f
```

The project ZIP overlays current src, tools, docs, README and Gradle properties on
the 1.7.0 archive. Unrelated baseline entries remain byte-identical; only the
superseded 1.7.0 production JAR is removed. The separately delivered 1.8.0 JAR and
the one inside the project archive are byte-identical. Archive CRC, source overlays
and baseline preservation are checked before delivery.

The earlier broad resource-validator limitation for legacy barbed-wire loot IDs
is unchanged. No rule is weakened to hide it; this release's affected resources
pass the dedicated validators.

No graphical Minecraft client or live dedicated server was launched. In-game OSD
appearance, framebuffer copies, shader compatibility, decal lighting/occlusion,
multiplayer snapshots and perceived combat/drop balance require the checklist in
UPDATE_1_8_0_RU.md. The supplied reference informs visual styling; the game tuning
is not a claim about measured real radio links or weapon performance.
