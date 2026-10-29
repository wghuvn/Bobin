# Bobin Craft 2.7.0 validation

Recorded 2026-10-23 for Minecraft 1.20.1 / Java 17 / Fabric.

- **351 JUnit tests passed**, zero failed, zero skipped, across **70 source classes**.
- **102 native chunk positions**, generated in two orders with identical fingerprints. The curated seed 19860426, 2048-block arena and generation profile 10 are unchanged.
- **Nine tank-access regressions** exercise native walking and the production articulated geometry: climbing from both sides at several headings; transport without mounting; shots selecting the actual NPC through an open hatch; closed-cover interception; side armor; internal exposure; saved hatch state; and remote-control rejection.
- **Four grenade-entry regressions** use the production movement solver and actual small-charge dimensions. A charge passes an occupied open hatch at four hull headings with aligned and independently rotated turrets, reaches the basket below the seated NPC's eyes, and receives full internal crew exposure. A closed cover stops the same charge. Both hatch openings and the driver's forward view have clear production ray paths. The hand fuse is 120 ticks; the bomber grenade retains its original 60 ticks.
- **Two grenade-animation regressions** check pin/windup/release ordering and native left/right player-arm poses, including return to the original pose. These validate model transforms and timing, not graphical presentation.
- Existing tank physics, crew reservation, loading, firearm, drone, terrain and persistence tests pass. Fixtures for the enlarged hull use a sufficiently long ascent plateau and a low ceiling that does not already intersect the initial vehicle.
- All seven resource validation commands passed, including eight static-vehicle geometry tests. The articulated tank has **3,236 cuboids**, five groups and two hatches. Resource checks cover **1,413 JSON files**, 311 loot tables, four occupation inventory items and three locales.
- **MIXIN_REMAP_PASS mixins=25 selectors=38 shadows=4 invocationTargets=3 constantTargets=1**.
- **ARMOR_COLLISION_MIXIN_APPLIED net.minecraft.class_1924 parts=3236**.
- **ARMOR_CLIENT_MIXINS_APPLIED net.minecraft.class_759 parts=3236**.

The last two checks launch real Fabric/Knot against the production intermediary JAR, verify the collision injector or the first-person animation injector/native arm invoker, initialize the production geometry, then exit in preLaunch. They do not start Minecraft main, create a world, open a game window or run a playable server.

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
python3 tools/mixin-smoke/check.py --client --gradle-cache /path/to/gradle-user-home
python3 tools/preview/render_tank_access.py
python3 tools/package_release.py /path/to/Bobin_craft_2.7.0.zip
```

## Evidence and limits

[Complete JUnit log](TEST_RESULTS_2_7_0.txt), [resource-check log](RESOURCE_RESULTS_2_7_0.txt), [build and runtime mixin audit](MIXIN_AUDIT_2_7_0.txt), [machine-readable receipt](verification-2.7.0.json).

No graphical Minecraft client or live multiplayer combat encounter was run. Native collision and exposure tests are not an end-to-end grenade-damage playtest. The first-person animation's visual feel, shader presentation, camera comfort and multiplayer transport while standing on the hull require in-game acceptance testing. The client preLaunch check validates injection, not rendered frames.

The [tank image](tank-rework-2.7.0.png) is a diagnostic rendered from production mesh coordinates, textures and UV formulas. The compatibility entry point `render_armor_mesh.py` produces the same image. It is not a Minecraft screenshot. Walking and grenade obstacles conservatively approximate rotated plates with short boxes; bullets use exact model-space intersections.

The T-72M1-inspired mesh is an original, enlarged game adaptation. It retains one functional operator, with three modeled seating locations. Both hatches share one control. Existing block-built wrecks remain separate scenery. Reloaded physical charges retain the pre-existing inert-on-reload policy; this update does not add persistent ticking explosions in unloaded chunks.

JAR: `build/libs/bobin_craft-2.7.0.jar` (5,737,831 bytes).

SHA-256: `716f821c1626c72cd642d1809fc717bc27517a037a7192bb34ebe48f86e4b875`.
