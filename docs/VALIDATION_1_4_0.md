# Bobin Craft 1.4.0 — validation

## Completed checks

- All production sources compiled for Java 17 against Minecraft 1.20.1 / Yarn 1.20.1+build.10 and Fabric API 0.92.2+1.20.1.
- Production bytecode remapped with the existing cached-build tool's official TinyRemapper MixinExtension.
- Compatibility audit: `MIXIN_REMAP_PASS mixins=12 selectors=16 shadows=4 invocationTargets=3`.
- JUnit: **163 passed, 0 failed, 0 skipped**, including 13 new drone tests and the existing 150 tests.
- Drone tests cover gravity/hover, pitch relative to heading, tilt/lift loss, self-level and acro modes, inversion, motor disarm, speed limits, bomber handling, malformed input, replay rejection, heartbeat timeout, soft landing collision speed and signal attenuation.
- Targeted drone resources: 3 registered items, recipes, models and icons; shared entity atlas; all 41 Java translation references and 6 registry names resolve in English, Russian and Ukrainian. Duplicate JSON keys are rejected.
- Deterministic drone asset generator check passed. Item icons were visually inspected.
- Existing faction-loadout validator passed: 10 profiles / 30 textures. Existing vehicle geometry suite: 8 passed.
- The packaged JAR and source ZIP passed archive integrity checks.

The offline test runtime initializes vanilla registries and applies Fabric Loader's normal development package-access transformation to a separate named game JAR. That test runtime and all test classes are excluded from the release JAR.

## Existing archive issue

The full-project `tools/validate_resources.py` fails on 14 legacy loot tables referring to unregistered items already present in the supplied archive: `barbed_wire`, `bunker_wall`, `burlap_sandbags`, `burning_barrel`, `equipment_panel`, `faded_bus_panel`, `hazard_sign`, `metal_floor`, `radiation_sign`, `road_barrier`, `scav_crate`, `scav_locker`, `scav_rack`, and `scav_radio`.

Those legacy tables and registrations were preserved. They were not allowlisted to conceal the failure. The separate drone resource check passes.

## Playtesting still required

No live Minecraft client or dedicated-server play session was performed. Compilation, compatibility auditing and automated checks verify code and data contracts; they do not verify perceived flight handling, camera comfort, multiplayer latency, audio balance or combat balance. Test those in a disposable world before using the update in a main world.

Drone explosions damage entities while preserving terrain and creating no fire. Flight uses bounded game physics and keyboard controls, rather than a real-world aircraft simulator.
