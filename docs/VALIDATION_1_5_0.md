# 1.5.0 validation record

## Executed checks

- Compiled every production Java source with Java 17 against Minecraft 1.20.1,
  Yarn 1.20.1+build.10, Fabric Loader 0.15.11 and Fabric API 0.92.2+1.20.1.
- Built the intermediary production JAR using the existing cached build workflow
  (`python tools/build_offline.py`) and official TinyRemapper/MixinExtension.
- Mixin audit passed: **13 Mixins, 19 selectors, 4 shadows, 3 invocation targets**,
  including all three new `PlayerInjuryMixin` persistence/tick injections.
- Full JUnit suite: **214 passed, 0 failed, 0 skipped** through
  `python tools/test_offline.py`.
- Focused injury, native cover-shape and drone regression run: **43 passed,
  0 failed, 0 skipped**.
- `python tools/validate_combat_resources.py`: passed. Checks five 16×16 textures,
  block/item models, two burn blocks, the tourniquet recipe, damage definitions,
  terrain tags and **17 translation keys in English, Russian and Ukrainian**.
- `python tools/generate_combat_assets.py --check`: passed. The five generated
  assets match their deterministic source exactly. A magnified texture contact
  sheet was visually inspected.
- Existing `python tools/validate_drone_resources.py`: passed. Three drone items,
  models/icons/recipes and 54 Java translation references remain valid.
- Compared modified vanilla-namespace tags with the supplied archive: every
  existing value/option in **14 existing tags** is retained. New soil/damage
  entries are appended. Existing language values are also retained unchanged.
- Checked the production JAR for the required player Mixin, current resources and
  1.5.0 metadata, and absence of test classes or the development smoke harness.

## New regression coverage

The new tests exercise exact untreated 12,000/6,000/1,200-tick deadlines; continued
fatal state after expiration; worsening wounds without resetting the deadline;
treatment before/after expiration; subsequent new wounds; persistent limb loss;
NBT round trips preserving remaining time; older/invalid save fields; body and
head orientation; frontal versus rear head hits; distance and cover falloff;
solid walls, two layers of glass, thin panes, open doorways, glass immediately
beside a solid wall, and rejection of unloaded chunks before collision reads.

The offline runner already applies Fabric Loader's package-access transform to
its Minecraft test runtime. A legacy test helper previously attempted to define
`LivingEntity` again after Bootstrap, causing duplicate-class failures. The
runner now explicitly marks its prepared runtime, and that helper is idempotent
and skips its redundant bytecode injection in this mode. No test-runtime
transformations or test classes are included in the release JAR.

## Host limitations and remaining checks

The normal Gradle/Loom entry point was attempted. Loom 1.7.4's Unix-domain socket
capability probe fails with `SocketException: Operation not permitted` on this
host before source compilation. The project-supplied cached build path completed
successfully instead. Required dependencies came from their official repositories;
downloaded artifacts were checked against published SHA-1/SHA-256 hashes. The
supplied named game JAR and its matching official Yarn mappings supplied the
named/intermediary compile and remapping inputs.

The broad legacy `tools/validate_resources.py` still stops at the supplied
`bobin_craft:barbed_wire` loot-table entry as an unreviewed/unknown item. That
loot table is byte-identical to the uploaded archive. New combat resources pass
their dedicated validator; the legacy validator was not weakened or allowlisted.

No Minecraft client or live dedicated-server session was run. This record does
not claim observed explosion appearance, live NPC behavior, synchronized two-client
combat, native item-use consumption or a live ten-minute death/treatment test.
Those are listed in the gameplay guide's manual verification checklist. The
automated tests validate the timing/state and native collision-shape calculations;
client appearance and combat balance still need playtesting.

## Deliverables

`build/libs/bobin_craft-1.5.0.jar` is the installable Fabric release. The updated
project ZIP includes the release, all changed sources/resources, deterministic
asset generator, validators, tests and this documentation. Original unrelated
project files from the uploaded ZIP are retained.
