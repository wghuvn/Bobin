# 1.6.0 validation

All production sources compiled with Java 17 for Minecraft 1.20.1, Yarn
1.20.1+build.10, Fabric Loader 0.15.11 and Fabric API 0.92.2+1.20.1 through the
project's existing cached build pipeline and official TinyRemapper.

- Full JUnit regression suite: **231 passed, 0 failed, 0 skipped**.
- Focused collision/camera/pose/voxel/input suite: **28 passed, 0 failed, 0 skipped**.
- Production Mixin remapping audit: **13 Mixins, 19 selectors, 4 shadows,
  3 invocation targets**, passed.
- Combat resource validator: **17 translation keys in 3 languages, 8 textures,
  2 particle-atlas definitions**, recipe, damage types and terrain tags passed.
- Deterministic combat asset regeneration check passed.
- Existing drone resource validator passed: 3 registered items, model/recipe/icon
  links, 54 Java translation references and 6 localized registry names.

The new tests exercise stationary flush wall/entity contact; microscopic motion;
separation from the launch floor; airborne landing; a real gap; empty-air hover;
camera position behind/above the frame at multiple headings; a rigid mount under
pitch/bank; bomber down-camera position; either/both missing legs; hand-use
restrictions; the eight-tick fall transition; rotating horizontal bounds; and
horizontal fragment hit classification.

Native Minecraft model tests check all sixteen limb-visibility combinations and
reset between different NPCs. They transform the actual prone body meshes for four
injury masks and four crawl phases, confirming that the visible geometry stays
above ground and within the low collider's height tolerance.

The voxel particle test captures its actual vertex output: **six outward-facing
quads, 24 vertices and eight distinct 3D corners**. Changing camera orientation
does not turn the mesh into a billboard. Cloud-layout tests check determinism,
particle budgets, bounded lifetimes, spatial depth and expansion. No OpenGL
context is needed for these geometry tests.

The existing tests for bleeding deadlines, treatment, persistence, flight,
payload safety, world generation, NPC scheduling, weapons and raid state all
remain in the passing full suite.

The release JAR's ZIP integrity, current resources, version metadata, particle
classes, wound feature and absence of test/smoke-harness classes are checked
before delivery. The package uses the build's remapped production bytes:

```text
bobin_craft-1.6.0.jar
Size: 3,908,842 bytes
SHA-256: e129713e594bf22f3c34ca3b0ef65f5c00733090d97e02dedb81307faec6dfe5
```

The updated project retains unrelated files from the previous supplied project,
replaces its previous release JAR with 1.6.0, and includes changed source, tests,
resources, asset generator and the Russian gameplay guide. Existing damage/block
tags and language values are retained.

The broad legacy resource validator's previously recorded `barbed_wire` loot
entry limitation is unchanged; it was not allowlisted or weakened. The dedicated
combat and drone validators cover this update's resources.

No Minecraft client or live dedicated server was launched. The tests do not claim
observed in-game camera composition, live NPC crawling/pickup/death timing,
multiplayer visual synchronization, particle performance or final combat balance.
Use the checklist in `UPDATE_1_6_0_RU.md` for those live checks. The source retains
the normal Gradle build as well as the cached build fallback for this host's
known Loom socket-probe restriction.
