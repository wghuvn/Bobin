# Bobin Craft 2.4.0 validation

Recorded 2026-10-23 against the included Java 17 / Minecraft 1.20.1 / Fabric JAR.

- **325 JUnit tests passed**, zero failures, zero skipped; 64 selected source classes.
- **102 native Minecraft chunk positions**, generated in both forward and reverse order with identical fingerprints.
- **11 checkpoint trenches walked** using Minecraft's native player/entity movement and block collision shapes. The walk includes approach landings, turns and the opposite end. Trench footprints avoid buildings, crop fields, armor bays and site bounds.
- Native vehicle tests traverse a full block with upward increments at most 0.085 block/tick; two-block walls and low ceilings prevent ascent. Other checks cover internal seating, authorized chamber loading, fixed movement, shell orientation, unclamped fast motion, exposed-material craters and cover protection.
- Native player tests cover hand restrictions, crawl dimensions, movement attributes and persistent limb loss after treatment. Encounter readiness tests exercise a loaded checkpoint with unloaded remote district chunks, post-based distance and spectator exclusion. Resident roster tests cover rejected insertion, unloading, death and save round trips.
- Authored navigation and furnishing checks verify every NPC task's connection to the street, complete doors/beds and matching supported loot blocks. The full run also retains deployment/map preparation, older generation profiles, sewers, utilities, tree crowns, weapons, drones, damage and persistence checks.
- Six resource-validation commands pass. This includes eight static vehicle geometry tests, all resource JSON, reviewed loot IDs, three locales, five distinct mono 24 kHz vehicle sound files, the articulated exterior and roof/net assets.
- Mixin remapping audit: **21 classes, 34 selectors, 4 shadows, 3 invocation targets and 1 constant target** resolve in the production mappings. This is target auditing; it does not claim a running client transformation test.
- The cabin inspection captures **552 production-renderer vertices**. Perspective rasterization from the seated eye verifies an open forward aperture. The roof/cable, tank and checkpoint images use production data with simplified inspection colors.

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
python3 tools/package_release.py /path/to/Bobin_craft.zip
```

The test runner writes a completion record. Packaging requires that complete passing run to postdate the JAR; partial logs are insufficient. It checks the profile/version, source timestamps, embedded JAR digest, archive CRC and full decoding of every PNG.

## Limits

The movement fixtures use Minecraft's actual entity movement, voxel shapes and generated chunks. Their lightweight `World` provides terrain/entity lookup; it is not a live `ServerWorld`, and landing particles are disabled in the walking fixture. No graphical Minecraft client, dedicated multiplayer playtest, graphics-driver/shader test, live NPC encounter session or human listening pass was performed. Input feel, live vehicle navigation and GPU presentation remain in-game acceptance checks. New procedural geometry requires a fresh world; the update preserves existing world chunks and finite encounter rosters.

JAR: `build/libs/bobin_craft-2.4.0.jar` (4,399,383 bytes)

SHA-256: `ad57280d0b8276322b9a24669dd7f3db587af2e18dd36e35bb364386b84d489d`

Machine-readable record: [verification-2.4.0.json](verification-2.4.0.json).
