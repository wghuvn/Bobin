# Validation — Bobin Craft 2.3.0

- Java 17 / Minecraft 1.20.1 / Fabric production build and intermediary remapping succeeded.
- Full production regression suite: **308 passed, 0 failed, 0 skipped**.
- Native in-memory chunk generation: **102 positions in both generation orders**, covering checkpoint, armor-footprint, trench, discovery, bridge, utility and boundary samples. Expanded native test class: four passed, zero failed.
- Seventeen remapped mixin classes, 25 selectors, four shadows, three invocation targets and one constant target resolve against the game JAR.
- Resource, faction, drone, combat, static vehicle and articulated armor validators passed. The articulated model contains 1372 bounded cuboids in three groups.
- Native shape checks cover the closed manhole side faces, round player aperture, all three net orientations, each shaft's open collector connection and repaired fitting support.
- State checks cover invalid vehicle input, buried immobilization, braking, turret alignment, finite ammunition/cooldowns, unique persistent vehicle reservations and destroyed/future saves.
- Drone checks cover fast and stationary utility-wire impact and diagonal near misses. Guard policy checks cover hearing versus sight, close approaches, ownership and temporary versus permanent mobility penalties.
- Baked layout compatibility and cold deployment-map preparation regressions pass.
- All PNGs are decoded and both archive and embedded JAR CRCs checked before delivery. Metadata identifies release 2.3.0 and generation profile 9.

No graphical Minecraft or live multiplayer playtest was performed. Native chunk checks do not execute a live vehicle encounter; source compilation and automated control/persistence checks do not establish final handling quality. The included diagnostic previews are not game screenshots.

Recorded results: `TEST_RESULTS_2_3_0.txt`, `CHUNK_TEST_RESULTS_2_3_0.txt`, `RESOURCE_CHECKS_2_3_0.txt`, `MIXIN_AUDIT_2_3_0.txt`, and `RELEASE_CHECKSUMS_2_3_0.txt`.
