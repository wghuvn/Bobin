# Stage 5 correction source candidate — review required

This correction targets the exact supplied Stage 5 project, not R2. Do not apply the old cumulative Stage 5 patch again. The original reconstructed archive SHA-256 is `78f5c3a35ede0c3522d758dda776702cce927bd3d734dfc6e0b66ee8e34936e4` (2,356 baseline project files).

The correction changes contact geometry, bounded overlap recovery and scheduling; aligns target zones/stage masks with real generator cells; extends existing provenance reconciliation to bridge/substation cells; fixes explicit world-buffer submission; and corrects the weapon-only opaque-square sprite. Existing source damage values, weapon exclusions and industrial smoke allocation are retained. The old blanket cross-section CRITICAL cascade is retired; no Stage 6 graph is supplied.

Source implementation is provided for independent review. Minecraft compilation, JUnit/GameTest execution, launches and visual acceptance remain blocked by unavailable Gradle bootstrap/dependencies. There is no built mod JAR and no newly recorded Minecraft after-video. Stage 5 remains unaccepted.

Run from project root with Java 17 and network-accessible dependencies:

```
bash gradlew clean build
bash gradlew test
bash gradlew runGametest
bash gradlew runClient
bash gradlew runServer
```

On Windows use `gradlew.bat`. Review the packaged genuine logs. Standalone JDK production-kernel commands are `java docs/Stage5/Verify.java` and `java docs/Stabilization/Verify.java`; these are not Minecraft tests.

Diagnostics:

- `/bobin destruction trace on`: start bounded server damage/commit tracing.
- `/bobin destruction trace`: show the last 16 rows (maximum 256 retained).
- `/bobin destruction trace off`: disable and clear.
- `/bobin destruction inspect`: HP/component/stage and provenance status.
- `/bobin destruction debris [offset]`: totals, pause counts and up to 8 detailed records/page, including operation, identity, pose, velocities, supports, required chunks, retry count and last successful simulation tick.
- Client VM option `-Dbobin.destruction.trace=true`: additionally identify weapon visual event/profile owner in the client console. The server chat toggle does not change the client JVM option.

Use a copied world for independent acceptance. Previously generated bridge/substation cells have no R2 provenance journal. They are intentionally unresolved, not automatically adopted from matching material; investigate their `inspect` result before interpreting queued HP changes as a fresh damage bug. Existing committed fragments are preserved. No speculative old-world rubble cleanup is run.

Localized physical wall openings and breach-origin fire/smoke are not implemented. Their required design and approval boundary are in LOCALIZED_BREACH_DESIGN.md. Stages 6/7 are not implemented or authorized by this candidate.
