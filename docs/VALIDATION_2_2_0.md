# Validation — Bobin Craft 2.2.0

- Production build: Java 17 / Minecraft 1.20.1 / Fabric, intermediary remapping successful.
- Production suite: **294 passed, 0 failed, 0 skipped**.
- Native in-memory chunk generation: **69 positions in both generation orders**, including all 24 off-road discoveries.
- Remapped mixins: **17 classes, 25 selectors, 4 shadows, 3 invocation targets and 1 constant target** resolved against the real game JAR.
- Resource, faction loadout, drone, combat and vehicle geometry validators passed. Detailed output is in `RESOURCE_CHECKS_2_2_0.txt`.
- Every updated PNG was decoded before packaging; the archive and embedded JAR passed their CRC checks.
- JAR metadata, dimension and selected world preset all identify release 2.2.0 / generation profile 8.
- Cold deployment-map preparation regression passed; runtime reads the baked layout.

New behavior checked: independent continuous cable phases and native serialization, manhole slide timing and clear circular collision aperture, mixed slab persistence and collision, fence/pane joins across chunk boundaries, pallet and well support, faction entrance clearance, discovery separation and outfalls that preserve receiving water.

Geometry previews use actual production plans and renderer vertices. No graphical Minecraft client or live multiplayer playtest was performed. Shader-specific water artifacts remain unverified.

See `TEST_RESULTS_2_2_0.txt`, `MIXIN_AUDIT_2_2_0.txt`, and `RELEASE_CHECKSUMS_2_2_0.txt` for the recorded results.
