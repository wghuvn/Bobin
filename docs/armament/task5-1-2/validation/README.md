# Task 5.1.2 verification evidence

`cpu-final.log`, `junit-summary.json`, and `junit/` describe the final Java 17
CPU run: 552 tests, 16 suites, zero failures/errors/skips. This isolated target
excludes Minecraft/Fabric-dependent bridge/render classes. `api-only.log`
contains 164 common/API tests included in that total.

`build.log` and `runGlbDebugClient.log` record failed full-project attempts:
Fabric Loom 1.7.4 could not be obtained because Fabric Maven returned HTTP403.
Both exit codes are 1. There is no successful Minecraft launch or new mod JAR.
`source-audit.json` checks Java syntax and local references, not Minecraft types.

The two `*.validator.json` files validate the actual exported GLBs and record
their hashes: each has 0 errors, 4 non-root-skinned-mesh warnings, 11 infos.
`verified-asset-hashes.json` covers final source scenes, exports, sidecars and
previews. `pose-metrics.json` records bind angles/depths and sampled geometry
checks; its results are Blender/CPU evidence, not in-game screenshots.
`grip-diagnostics.log` verifies diagnostic rendering did not modify 15 protected
Blender/GLB/JSON assets. Blender renders use diagnostic skin colors; live player
skin binding remains an existing Minecraft path and needs an actual client test.

`scope-audit.json` compares original tracked files with baseline df90025 and
confirms preservation of non-armament source, common API, renderer, RigFrame,
original technical GLB, dependencies and build.gradle. Scope checks do not
prove game behavior. Logs and XML are deliberately published verification
reports; build directories, caches and temporary files are excluded.
