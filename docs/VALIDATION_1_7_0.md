# 1.7.0 validation

Target: Minecraft 1.20.1, Yarn 1.20.1+build.10, Java 17, Fabric Loader 0.15.11,
Fabric API 0.92.2+1.20.1. All production sources compiled through the cached
build pipeline, with actual Fabric access wideners and official TinyRemapper /
MixinExtension. The normal Gradle build remains supported. The existing fallback
avoids this host's recorded Loom socket-probe restriction.

## Results

- Full JUnit suite: **252 passed, 0 failed, 0 skipped**.
- Focused new/baseline drone checks: **28 passed, 0 failed, 0 skipped**, included
  in the final full run.
- Remapped production audit: **14 Mixins, 22 selectors, 4 shadows, 3 invocation
  targets, 1 constant target**, passed.
- Drone resource validator passed for 7 registered drone/controller items,
  native NPC egg, 2 creative tabs, 7 shaped recipes, icons, shared model atlas,
  the reviewed breachable block tag, 55 Java translation references and 19
  additional registry/variant labels in English, Russian and Ukrainian.
- Deterministic drone asset regeneration check passed. New icons were visually
  inspected in a nearest-neighbour contact sheet.
- Combat resource validator and deterministic texture check passed: 17 keys in
  3 languages, 8 textures, 2 particle atlases, recipe, damage types and terrain tags.

## New checks

Damage checks apply all fragment directions repeatedly to verify per-target caps,
positive health remaining for a full-health 20 HP NPC hit by a general grenade or
specialist charge, and the retained lethal close-range general-purpose FPV role.
They check pressure falloff, blocked exposure and invalid input. Fragment injury
energy remains above the limb threshold despite the smaller health-damage cap.
An ASM wiring check verifies exactly one pressure stage and one fragment stage,
with no extra native `createExplosion` call that could add uncapped damage.
These are damage-policy and bytecode checks, not a live server combat simulation.

Signal checks cover monotonic loss with distance and accumulated obstruction,
range limits, a short recoverable dropout and disconnection after 40 lost ticks.
Video-policy checks cover actual frame selection by timestamp, bounded corruption,
resolution tiers, longer frame intervals/delay on weak links, blackouts, recovery
and maximum stalls. They do not invoke OpenGL.

Variant tests use real tracked drone/payload entities and NBT round trips for all
six variants, correct recovery items, legacy defaults and rejected family changes.
The native spawn-egg object resolves to the mod's actual guard entity. The egg's
AI initialization, peaceful persistence and firing behaviour require a live world
check; no generated encounter roster is claimed to be exercised by the egg test.

Native Minecraft model transforms verify every FPV payload remains inside the
existing collider under assisted pitch/bank and multiple headings. Existing
optical-surface, near-field probe projection, open-door flight, collision,
screen-input, limb-pose, neutral NPC damage-overlay and corpse tests also pass.
The full suite retains world-generation, weapon, Hub, roster, raid, bleeding,
treatment and persistence regressions.

## Release checks

The final production JAR passed ZIP CRC, version metadata, current-resource and
remapped-class byte checks. It contains the client video classes, all variant and
pressure classes, the egg model and seven item recipes. Test and optional server
smoke-harness classes are excluded. The JAR inside the project ZIP is byte-identical
to the separately delivered production JAR.

```text
bobin_craft-1.7.0.jar
Size: 3,933,871 bytes
SHA-256: 1024521eac47ae7e435c247cb1ec9f401bad27bab731100caa2bffdeb66b66bf
```

The project archive overlays current sources/resources/tests/tools/docs onto the
1.6.1 project. Unrelated baseline entries are retained byte for byte, and only
the superseded 1.6.1 production JAR is removed. Original item/entity registry IDs,
translations and generation resources remain compatible. The cached builder now
stages, flushes and CRC-checks the Fabric API aggregate as well as the final JAR,
avoiding publication of an incomplete ZIP during this host's local build flow.

The broad resource validator's previously recorded legacy `barbed_wire` loot
registration limitation is unchanged. Its rules were not weakened or allowlisted.
This update's newly added resources pass the dedicated validators.

No Minecraft client or live dedicated server was launched. In particular, real
framebuffer blits, perceived delay/blackouts, graphics-driver/shader compatibility,
creative-tab rendering, live egg/dispenser spawning, wooden-cover destruction,
multiplayer synchronization and final combat balance need the client checklist
in `UPDATE_1_7_0_RU.md`. The downloaded reference sources inform the presentation,
not measured real-world radio or weapon behaviour.
