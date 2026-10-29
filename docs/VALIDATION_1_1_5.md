# Bobin Craft 1.1.5 validation

- Confirmed the installed 1.1.4 JAR matched the delivered release; this was a code bug.
- Read-only inspection found a design-4 initialized hub, survival player outside the wall and no initialized player raid record. No game files were changed.
- The old hostile query included friendly pillager-based sentries around every one of the 405 candidate landing positions near hub spawn. This prevented onboarding, and the old countdown required that onboarding.
- Fixed landing threat classification and removed raid initialization from the countdown eligibility API.
- 31 focused JUnit tests passed, including the reported outside position, all 405 blocked landing candidates, living enemy rejection, map exemptions, countdown, cancellation, persistence, raid state, dialogue sessions and conditional repairs.
- Existing hub collision, structure and circulation checks passed.
- Resource checks passed: 1,037 JSON files, 289 loot tables, original models, textures, sounds and translations.
- Production compilation and mixin remap audit passed: 12 mixins, 14 selectors, 4 shadows, 2 invocation targets.
- Packaged version, four-argument eligibility API, landing threat helper and all committed resources verified.
- A live Minecraft client/server play test was not launched for this patch.
