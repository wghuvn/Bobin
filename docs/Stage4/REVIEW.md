# Stage 4 checkpoint — refinery geometry and resumable transitions

Baseline: reduced Stage 3. No Stage 5 work implemented.

## Authoritative geometry

StructureGeometryAccess reads ResidentialStructures' actual final cached siteBlueprint. StructureGeometrySnapshot copies only the tank's coordinate mask, preserving actual block specifications/properties, explicit hollow air, original dimensions, roof details, ladder/pipe/panel/hopper. It never builds an alternative intact cylinder or fills the interior with terracotta. Damage transformations are applied to copied original occupied cells. Final shell limits are below the original upper band for all four sizes and four variants. No unrelated site cells are selected. No generator placement/source files were edited; the new access helper is read-only.

StructureCollapseManager's normal and public tank-builder paths now use that reference. Template/diff keys include seed and variant (and template geometry revision), and diff bounds cover both endpoints. The previous hardcoded iron-bars/mossy-wall ring cleanup is removed; it could target geometry the current generator does not create.

## Queue and ownership

Tank transitions use a separate persistent target ledger. Every new transition supplies the complete desired cell map, so replacing an unfinished transition cannot forget a roof already logically air in an intermediate stage. Expected current states, pending target states, protected cells/conflicts, stable journal UUID, revision and variant are persisted using registry names/properties, never raw numeric block IDs. Cell writes reconcile saved-world results idempotently after a save-order mismatch. Missing/unloaded chunks do not force loading; pending targets are retained, including cells awaiting generation provenance. Bounded rotated candidate work prevents an unavailable chunk from permanently stopping all loaded work. Geometry revision mismatches freeze reconciliation rather than reinterpreting an old cursor. Old aggregate HP/stage remain separate from geometry progress.

Three narrowly scoped observational mixins were added: completed refinery-blueprint placement scope; successful ChunkWriter cell writes; and external WorldChunk block changes in registered tank masks. Existing mixin entries/classes are preserved. They record newly generated provenance and protect later edits, including remove-and-replace with the same material. Connection-tracking finally restoration also clears the observation scope after exceptional blits. Full mixin validation is NOT EXECUTED and is a release gate.

The server checks exact expected state and block-entity safety before writing. Player-filled containers are preserved; an originally tracked empty roof hopper can be removed. Block removal/placement is server-authoritative. There is no persistent debris or support simulation in this stage.

## Compatibility and explicit fallback

Existing structure IDs, origins and saved variant values are unchanged. Structure save schema is advanced to 2, adds explicit known component HP, preserves legacy 2.7.7 entry data under Legacy277, and preserves unknown structure entries. Unknown future schemas are retained read-only. Missing legacy partial component HP stays unknown: it uses preserved aggregate HP and does not invent a healed component map. A backup is required for reverting to older code, whose reader does not understand schema 2.

IMPORTANT: 2.7.7 did not record block ownership. Already-generated cells without trustworthy provenance are LEGACY_UNRESOLVED; the journal retains their pending targets/conflicts and does not delete/overwrite them. This means an old save's upper row can remain until its ambiguous ownership is independently resolved. This fallback is intentional and follows approved Revision 1.1. It is not claimed that every old-world visual defect is automatically repaired. Same-material historical player edits cannot be reconstructed. Existing legacy partially destroyed blocks and holes are not regenerated. Geometry progress remains pending instead of being reported complete. `/bobin destruction inspect` reports ledger ownership/pending/conflict status.

A previously lost delayed FP-1 damage schedule cannot be reconstructed from 2.7.7's saved data; no speculative extra damage is invented. Existing fire/weapon gameplay logic is otherwise unchanged. The new journal addresses block updates, not a redesign of delayed damage mechanics.

## Verification

The initial idempotence regression failed (log retained) and was fixed before packaging. Final Java contracts pass for all 4 tank sizes x 4 variants, upper-bound removal decisions, unfinished retargets, replay after a simulated save-order mismatch, player conflicts, same-material replacement protection, and deferred target retention until provenance becomes available. These test actual dependency-free production geometry rules/ledger, not Minecraft/NBT integration.

Additional source scope checks confirm original world-generation, drones, weapons, vehicle movement, raids, Fabric metadata and original mixin classes are unchanged from Stage 2. New observational mixin registrations are explicit in the file manifest. No claim is made that full Fabric compilation, actual generator/snapshot equivalence, real-save migration, or mixin loading passed. Those require Minecraft and dependencies.

Build verification: BLOCKED BEFORE COMPILATION. The actual Java 17 Gradle wrapper attempts for `clean build -x test` and separately `test` exited 1 because services.gradle.org could not be resolved to download Gradle 8.8. This is not a successful Fabric build. Dependency-free production Java kernels/contracts were compiled and executed with the Java 17 compiler module. Java syntax parsing is separate and does not validate Minecraft/Fabric symbols or mixin targets. No new libraries.

NOT EXECUTED: Minecraft client/server, Fabric GameTest, actual JUnit through Gradle, mixin initialization, NBT save/load round trips, real-world recovery, multiplayer/drone observation, shader rendering, FPS/TPS and visual acceptance. There is no screenshot/render evidence from this environment. Reports use source inspection and numerical tests, not a claim of reproduced visuals.

Existing-world regression gate: use copies of 2.7.7/Stage 2 saves, never the only original. Cover intact/damaged/critical/destroyed tanks, all sizes/variants, active fires, partial transitions, unloaded boundaries, containers, same-material player replacements, multiple reloads and recovery interruption. Check HP/fire/stage/IDs and world preset before and after. Verify all 12 weapon types, GROM-7 activation, tank movement, NPC injuries, hub/raid deployment and drone cameras/streaming. These runtime checks remain NOT EXECUTED.

## Reproduction

Run `python scripts/verify_revisions.py` for Java 17 contracts; append `--gradle` when the Fabric/Gradle dependencies are available. Run `gradlew.bat clean build -x test` and separately `gradlew.bat test` on Windows.

## Checkpoint gate

Stages 2.1, 3 and 4 were authorized as one sequence. Stop after Stage 4 for independent review. Stage 5 is not authorized or implemented.
