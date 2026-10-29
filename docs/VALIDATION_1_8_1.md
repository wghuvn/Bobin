# 1.8.1 validation

Target: Minecraft 1.20.1, Yarn 1.20.1+build.10, Java 17, Fabric Loader 0.15.11,
Fabric API 0.92.2+1.20.1. Cached production build passed with actual Fabric access
wideners, TinyRemapper and MixinExtension.

- Focused tests: **25 passed, 0 failed, 0 skipped**. They cover video sizing,
  progressive degradation, timestamped frame selection, cadence/stalls, screen
  controls, link quality and existing FPV/bomber optical geometry.
- Receiver sizes fit the healthy-link framebuffers across Full HD, 4K, ultrawide,
  small and 1×1 viewports and all quality tiers, retaining aspect within integer
  rounding. Full HD starts at 1280×720; weak links add bounded fine corruption.
- Remapping audit passed: 14 Mixins, 22 selectors, 4 shadows, 3 invocation targets,
  1 constant target.
- Drone resources and deterministic assets passed: 7 items/recipes, native soldier
  egg, 2 tabs, 33 Java translation references and 19 registry labels in 3 languages.
- Combat resources and deterministic assets passed: 17 keys in 3 languages,
  10 textures, 2 particle atlases, recipe, damage types and terrain tags.
- Production JAR CRC, duplicate paths and version metadata passed. All **1,676**
  remapped production files and current resources match the JAR. Tests are excluded.
  The compiled DroneHud contains no NPC query, raycast, target or corner-box methods.

```text
bobin_craft-1.8.1.jar
Size: 3,964,114 bytes
SHA-256: d9d6e91edc6a31a25ffa68321185d786472c1a0fb26b6ca95f281e2898ced304
```

The project ZIP overlays current sources/resources/tests/tools/docs, README and
Gradle properties on the 1.8.0 archive. Unrelated entries remain byte-identical;
only the superseded production JAR is removed. CRC, baseline preservation and
source overlays are checked before saving. The embedded and separate 1.8.1 JARs
are byte-identical.

This small camera-only update uses focused verification. The 269-test full run
and 48-test final drone/marks run recorded for 1.8.0 were not repeated or relabelled
as 1.8.1 runs. The earlier broad validator's legacy barbed-wire loot limitation is
unchanged; affected resources pass their dedicated validators.

No graphical Minecraft client or live server was launched. Framebuffer blits,
perceived pixelation/interference and shader compatibility still require the
short in-game checklist in UPDATE_1_8_1_RU.md. Tests do not invoke OpenGL.
