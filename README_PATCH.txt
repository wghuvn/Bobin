BOBIN CRAFT — STAGE 5 SOURCE CANDIDATE ONLY

BASELINE: Minecraft-approved stabilization R2 full-source ZIP
SHA-256: bc6471e819a4bd1bbde0cd0958cbfcf8272748e3f48dd96efe51dc36f695344f
PROJECT FILES: 2325

This incremental patch applies to R2, not R1 or the original old archive.
Extract it into a SEPARATE folder, then run:
  python apply_stage5_patch.py "PATH/TO/R2/Bobin_craft"

The installer validates every baseline and payload hash before mutation,
backs up original modified files and verifies every final project hash.
It rejects locally modified baselines and rolls back on application failure.
No original project files are deleted. No world save is edited by this installer.
Use the complete Stage 5 source ZIP if the local source tree differs.

Read Bobin_craft/STAGE5_README.md and docs/Stage5/REPORT.md.
Fabric build, Gradle JUnit, GameTest and Minecraft launches were blocked
before compilation by unavailable Gradle distribution DNS. No built JAR
or in-game visual acceptance is claimed. The exact red-circled refinery
screenshots were not available for object-level visual matching.

Stage 5 is submitted for review. Stages 6 and 7 NOT STARTED.
