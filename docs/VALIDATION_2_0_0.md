# 2.0.0 validation

Target: Minecraft 1.20.1, Yarn 1.20.1+build.10, Java 17, Fabric Loader 0.15.11,
Fabric API 0.92.2+1.20.1. The supplied cached dependencies were used for a real
production compilation, Fabric access widening and TinyRemapper/MixinExtension
remapping. The release JAR is an intermediary production mod, not a development JAR.

## Results

- **271 JUnit tests passed; 0 failed; 0 skipped.** The complete current suite covers
  existing drones, camera geometry, controls, weapons, wounds, raids, NPC behavior,
  saved terrain compatibility and the authored world.
- `AuthoredZoneTest` checks the fixed seed/size, nine distinct areas, 31 buildings,
  dry/separated pads, roads without deep-water fords or unwalkable steps, one connected
  road network, bridge decks, descending buried collectors, working hatch controls,
  complete doors/beds/loot, unfinished-building stairs, all material/property references,
  absence of the removed builders, and complete forward/reverse blueprint slicing.
- `AuthoredChunkTest` registers the actual mod blocks in a standalone test lifecycle
  and generates **33 real Minecraft ProtoChunk positions in both orders**. It checks
  every block-state fingerprint, positive/negative seams, buildings, bridge decks,
  maintenance shafts, return point and finite-world edges. It also checks all custom
  block IDs and state properties and the final generated manholes and walking decks.
- Exterior resident assignments remain reachable on the actual graded streets.
- The broad resource validator passed: **1,082 JSON files, 300 loot tables, 99 reviewed
  item IDs**, source art/models, vehicle geometry, camp/NPC/ambient textures and audio,
  forest blocks and matching locale keys/placeholders.
- Dedicated drone, combat and faction validators passed. **8 vehicle geometry tests**
  passed; all 237 vehicle cells and their bounded models/colliders are retained.
- The Mixin remapping audit passed: **14 Mixins, 22 selectors, 4 shadows,
  3 invocation targets, 1 constant target**.
- The production JAR passed CRC, duplicate-entry, current-resource equality and version
  metadata checks. It contains all four new world classes and none of the 16 removed
  renderer/module classes. Test classes are excluded. Both world-definition JSON files
  select 2048/profile six.

## Resource cleanup

Thirteen self-drop loot tables referenced imported prop items that were never
registered in the supplied project. Those invalid tables were removed. The existing
equipment-panel art is now registered as a real block and used in the pump hall.
The reviewed vanilla loot palette now includes dirt, which the existing scorch tables
already return. The broad validator follows the new direct thematic-loot consumers.

The old strike-bomb generator's byte comparison also treated PNG compression changes
and later additive damage-tag entries as artwork errors. Its check now compares
actual decoded pixels and verifies required tag membership, preserving all existing
image pixels and additional drone/injury damage entries. No existing texture or
shared damage tag was overwritten to make the check pass.

## Build artifact

```text
bobin_craft-2.0.0.jar
Bytes: 3890715
SHA-256: 7d571ea68492dd52918f07d636a8002493e4ca7f373c1a2c725ede7d118cae2a
```

The project ZIP contains the complete current sources, resources, tests, tools,
Gradle wrapper, docs, exact diagnostic previews and this compiled JAR. Runtime worlds,
logs, IDE metadata, cached dependencies, stale build output and older release JARs
are excluded. Its embedded JAR is checked against the audited local build.

## Practical limits

No graphical Minecraft client or live server was launched for this release. The
native checks use real in-memory Minecraft chunks and do not constitute a client
playtest. The three included images show actual plan/blueprint geometry in generic
diagnostic colors; they are not game screenshots. The isometric views omit the
surrounding forest and represent vehicle cells schematically.

Use a new world and inspect the nine areas, bridges, hatches and stairs in game.
Existing saved chunks are not rewritten, and old-profile terrain decoding is kept
only for reading existing worlds; retired structures do not generate in new chunks
of those old profiles. Normal Minecraft water updates apply to the static collector
geometry; there is no custom hydrodynamics or vehicle driving simulation.
