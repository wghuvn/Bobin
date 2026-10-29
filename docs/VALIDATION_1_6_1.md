# 1.6.1 validation

The release targets Minecraft 1.20.1, Yarn 1.20.1+build.10, Java 17, Fabric
Loader 0.15.11 and Fabric API 0.92.2+1.20.1. All production sources compiled
through the cached build pipeline with Fabric access widening and official
TinyRemapper/MixinExtension. The normal Gradle build remains supported; this
host uses the existing fallback because of its recorded Loom socket restriction.

## Results

- Full JUnit suite: **237 passed, 0 failed, 0 skipped**.
- Focused camera/guard geometry/damage-feedback suite: **11 passed, 0 failed,
  0 skipped**, also included in the full run.
- Production Mixin audit: **14 Mixins, 21 selectors, 4 shadows, 3 invocation
  targets and 1 constant target**, passed.
- Combat resources: 17 keys in 3 languages, 8 textures, 2 particle-atlas
  definitions, recipe, damage types and terrain tags passed.
- Drone resources: 3 items, models/icons/recipes, 54 Java translation references
  and 6 registry names in 3 languages passed.
- Combat, drone and faction deterministic asset checks passed; 10 faction
  loadouts and 30 faction texture paths passed validation.
- The existing Python vehicle-geometry checks also passed: **8 tests**.

## New regression coverage

Native Minecraft mesh transforms measure the FPV viewpoint against the actual
cyan lens in 75 combinations of heading, pitch and bank. The viewpoint remains
1 mm in front of the optical surface; battery, camera housing and frame are
behind the forward viewpoint. A real payload mesh point is projected through
Minecraft's 95-degree perspective: its probe is clipped by the original 5 cm
near plane and visible in the bottom of the image with the FPV-only 1 cm plane.
Bomber checks preserve its former forward offset and downward position.

Native shoulder-cap geometry is checked for vertical cuts at both torso sides,
horizontal leg cuts and a stable shoulder orientation when the missing arm's
previous yaw differs. Existing mesh checks cover all sixteen missing-limb
combinations and prone body clearance.

Damage-feedback checks call the production overlay and animation handlers
directly. Hurt and death states both select the neutral overlay for guard
models, while a vanilla living entity retains its normal overlay path. Ordinary
knockback and animation packets preserve the guard's existing motion and
locomotion speed. These checks use a worldless fixture for the relevant state;
they do not simulate a live network session or server health/armour calculations.
The production damage method still delegates to native damage and retains the
native cooldown, then restores the existing limb animation speed.

The remapped audit validates every Mixin selector/shadow/invocation against the
intermediary game JAR. It now also verifies that the projection method contains
exactly one matching 0.05f constant before accepting the near-plane injection.

## Release and preservation checks

The production JAR passed ZIP CRC validation and was checked against current
resources, version metadata and remapped production classes. Test and optional
server-smoke classes are excluded. Its bytes are also the JAR embedded in the
project archive.

```text
bobin_craft-1.6.1.jar
Size: 3,910,714 bytes
SHA-256: fc66cd3693d7deebc6f5be7a8e88d599bf2b3a8c0a763fcaa285a256a409642c
```

The project ZIP overlays changed sources, tests, tools and documentation onto
the prior 1.6.0 project. All unrelated baseline entries are retained byte for
byte; only the superseded 1.6.0 production JAR is replaced. Damage tags,
translations, textures and generation files are unchanged by this patch.

The broad resource validator's previously recorded legacy `barbed_wire` loot
registration limitation remains unchanged. Its rules were not weakened or
allowlisted; the dedicated combat/drone validators passed for this release.

No live Minecraft client or dedicated server was launched. Automated geometry
and handler checks do not establish observed in-game composition, shader-pack
compatibility, multiplayer synchronization, final particle density or performance.
The Russian update guide contains the remaining client checklist.
