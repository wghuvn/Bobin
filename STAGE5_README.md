# Stage 5 source checkpoint

Based on the Minecraft-approved stabilization R2 full source, SHA-256 `bc6471e819a4bd1bbde0cd0958cbfcf8272748e3f48dd96efe51dc36f695344f`.

Read `docs/Stage5/REPORT.md`, `execution-status.json` and actual logs. Source implementation is submitted for review; Fabric compilation/GameTests/client/server and visual acceptance are not verified because Gradle bootstrap was blocked. There is no built JAR in this delivery.

Run Java 17 checks from this project root:

```sh
java docs/Stabilization/Verify.java
java docs/Stage5/Verify.java
python docs/Stage5/check_scope.py PATH/TO/R2_FULL_SOURCE.zip
```

When dependencies are available, run `gradlew.bat clean build -x test`, `gradlew.bat test` and `gradlew.bat runGametest` separately (or `bash gradlew` on Linux), then the report's in-game matrix on copied saves. Diagnostic: `/bobin destruction debris`.

The incremental patch is against R2, not R1 or the old original archive. Stages 6 and 7 have not started.
