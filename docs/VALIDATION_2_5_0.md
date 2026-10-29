# Bobin Craft 2.5.0 validation

Recorded 2026-10-23 for Minecraft 1.20.1 / Java 17 / Fabric.

- **336 JUnit tests passed**, zero failures, zero skipped, 67 selected source classes.
- **102 native chunk positions**, generated in both orders with equal fingerprints; **11 checkpoint trench walks** retained.
- Vehicle fixtures verify full-block traversal with ascent at most **0.055 block/tick**, solid two-block barriers, low-roof clearance, internal seats, manual loading and fixed emplacement behavior. The revised solver resolves pitched track sections with Minecraft voxel collisions; it does not rely on vanilla's full-AABB step teleport.
- Seat tests verify one NPC reservation, expiry/release and supported boarding stations outside hulls at four rotations, both mobile and buried. These are reservation/station fixtures, not a live NPC pathfinding session.
- Production-renderer UV capture verifies that every hull face samples a two-dimensional texture area. The model has 1,381 articulated cuboids, weathered material variants, field stowage and moving tread groups.
- Injury model checks cover all limb masks, fixed missing-shoulder pivots, alternating crawl pulls, static rest poses and above-ground mesh bounds over four crawl phases.
- Connected-surface tests use unchanged stored states across a chunk boundary, neighbor removal, different roof variants and exactly one label anchor on a rectangular container face.
- Shockwave traces verify reduced pressure through the exposed breach, reachable internal windows, an intact second wall, open trench passages, earth banks, disabled terrain mutation and unloaded-chunk rejection. The headless test passes the terrain-rule value explicitly because Fabric's gamerule accessor mixin is not active in this test JVM; production reads the registered gamerule.
- Seven resource-validation commands and eight static vehicle tests passed. The zone asset generator also reproduces the current sculpted sandbags and omits retired, unregistered loot. The checks decode every mobile material, validate connected model references, locales, five engine/weapon sounds and the additional original concussion tone.
- **MIXIN_REMAP_PASS mixins=22 selectors=36 shadows=4 invocationTargets=3 constantTargets=1**. This checks production mapping targets and descriptors; it is not a client mixin-application test.

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
python3 tools/package_release.py /path/to/Bobin_craft.zip
```

Packaging requires a completed passing test record newer than the JAR, covering every test class and matching the JAR digest in the validation receipt. Client initialization can redirect stdout, so the machine-written JUnit completion record is authoritative. It checks the release/profile, production-source timestamps, archive CRC, embedded JAR digest and complete PNG decoding.

## Limits

The fixtures use real Minecraft block shapes, chunks and production render/model methods in a lightweight World. No graphical Minecraft client, live dedicated server, multiplayer encounter, GPU/shader compatibility session or human listening pass was run. Input/camera feel, continuous crew navigation, sound balance, particle appearance and visual suspension motion remain in-game acceptance checks. The blast model is a bounded gameplay approximation rather than structural or fluid simulation.

Existing profile-10 worlds are supported. No terrain-generation version was changed.

JAR: `build/libs/bobin_craft-2.5.0.jar` (5,353,147 bytes)

SHA-256: `b77f7e74e5e02f523189d9e95beb8a0f91a33e1470ef886fd285c1514b008d48`

Machine-readable record: [verification-2.5.0.json](verification-2.5.0.json).
