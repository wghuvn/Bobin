# Bobin Craft 2.6.0 validation

Recorded 2026-10-23 for Minecraft 1.20.1 / Java 17 / Fabric.

- **345 JUnit tests passed**, zero failed, zero skipped, across **68 source classes**.
- **102 native chunk positions**, generated in two orders with identical fingerprints. The curated seed, 2048-block arena and profile 10 remain unchanged.
- **Nine tank-access regressions** exercise real Minecraft movement and production geometry: climbing both sides at 0°, 45° and 90°; walking without mounting; hull transport; physical grenade passage and a closed cover; player/NPC targeting through the opening; side armor; internal pressure exposure; persisted state; and rejecting remote hatch controls.
- NPC bounding boxes remain below the closed roof. A nearest-hit trace selects the actual NPC through the open hatch and the tank through the closed hatch. Existing vehicle physics, crew reservations, fixed emplacements and loading tests also pass.
- Seven resource validation commands passed, including eight static-vehicle geometry tests. The reworked articulated mesh has **1,491 cuboids**, four groups, separate walking volumes, four inventory items and three locales. All 1,412 resource JSON files and 311 loot tables pass the reviewed-resource check.
- **MIXIN_REMAP_PASS mixins=23 selectors=37 shadows=4 invocationTargets=3 constantTargets=1**.
- **ARMOR_COLLISION_MIXIN_APPLIED net.minecraft.class_1924 parts=1491**. This additional check launches real Fabric/Knot against the production intermediary JAR, verifies the injected EntityView handler, initializes the production geometry, then exits in preLaunch. It does not start Minecraft main, accept an EULA, create a world or launch a playable server.

## Source-archive cleanup

The input contained 13 retired generation builders, three obsolete test classes and 12 loot files referring to unregistered retired items. The current authored-zone regression explicitly requires those builders to be absent. They are preserved under `tools/retired/pre-authored-zone/`, outside compilation and runtime resource paths. Current map-generation implementations were not replaced.

## Reproduce

```sh
python3 tools/build_offline.py --gradle-cache /path/to/gradle-user-home
python3 tools/test_offline.py
python3 tools/validate_resources.py
python3 tools/validate_faction_loadouts.py
python3 tools/validate_drone_resources.py
python3 tools/validate_combat_resources.py
python3 tools/test_vehicle_geometry.py
python3 tools/check_occupation_assets.py
python3 tools/generate_zone_assets.py --check
python3 tools/mixin-smoke/check.py --gradle-cache /path/to/gradle-user-home
python3 tools/package_release.py /path/to/Bobin_craft_2.6.0.zip
```

## Limits

No graphical Minecraft client or live combat encounter was run. The tests validate native collision/movement, shared model-space ray tests and armor exposure factors; they are not an end-to-end multiplayer shooting or grenade-damage playtest. Tank art is inspected in a textured orthographic renderer using production mesh coordinates and UV formulas. It is a diagnostic, not a game screenshot. Shader presentation, close-range aiming feel and client/server transport while standing on a moving hull need in-game acceptance testing.

Walking uses short conservative boxes around rotated plates; bullet intersections use exact model-space cuboids. The existing bounded gameplay blast pipeline is retained. Saved old vehicles default to an open hatch, then persist the chosen state. Decorative block-built wrecks are separate from the functional vehicle.

JAR: `build/libs/bobin_craft-2.6.0.jar` (5,355,090 bytes).

SHA-256: `32208e905c5f52909b72142afad8fdb409fa8989dc6a00b23c30bd192943efa0`.
