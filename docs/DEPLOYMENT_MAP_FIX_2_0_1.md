# 2.0.1 — deployment-map loading fix

Replace `bobin_craft-2.0.0.jar` with **`bobin_craft-2.0.1.jar`** on the client and
server, then restart Minecraft. Keep the existing 2.0.0 world and raid progress.
Generation profile six, seed 19860426, area locations, structures and roads are
unchanged. This is a loading fix; no world reset is required.

## Cause and correction

The first map-table request in 2.0.0 entered `DeploymentPlanner.plan`, which first
initialized the zone's complete layout. Although the world is fixed, this initialization
repeated terrain/site searches, bridge probing, every road path search and many
duplicate road-column samples. The screenshot's “Preparing a safe deployment point…”
message covered this whole operation, making the map appear unresponsive. Moving away
from the table while waiting could also discard the late result through the existing
range validation.

A direct reproduction measured roughly **30.3 seconds** before the map was ready;
about **28.8 seconds** were spent rebuilding the fixed layout. Preparing another set
of landing choices after the layout existed took 244–363 ms.

The original layout is now bundled as immutable metadata in
`data/bobin_craft/warzone/black_alder_layout.json`. Runtime validates its format,
seed, size and generation profile, loads it, and skips duplicate road-column work.
The original deterministic planner remains available to offline tooling through
`ZonePlan.recompute`. The server primes the immutable map metadata while entering
the hub, without loading chunks or touching world/player/save state from a worker.

The table request now says “Loading the deployment map…” and has a 20-second timeout
so a failed task releases the pending request instead of leaving it pending indefinitely.
Actual deployment keeps its separate safe-landing preparation message and checks.
Server-side range, session, rotation, choice validation and final geometry safety
remain active. Existing valid offered choices are retained when reopening the table.

## Verification

- **275 JUnit tests passed, 0 failed, 0 skipped**, including all previous tests and
  four new regressions for this failure.
- A cold table-request test invokes the exact `RaidManager.prepareMapData` path and
  requires completion within five seconds. It validates the three safe choices,
  complete map encoding and client-side packet decoding, rotation, extraction,
  roads and pixel data. Further tests cover reopening the table and subsequent cycles.
- The same standalone reproduction now prepares the map in **2.99 seconds**, including
  creation of its terrain object; the complete packet round trip took 3.49 seconds.
  The payload remains 64,061 bytes and produces the same rotation-zero choices.
  These are measurements on the development host, not a promised client frame rate.
- The stored layout equals the original deterministic planner's sites, bridges,
  complete road point lists, drains, resident assignments and every indexed road column.
- Real Minecraft chunk checks still pass at **33 positions in both generation orders**,
  covering buildings, bridges, shafts, extraction and finite-world edges.
- Broad resource validation passes for **1,083 JSON files**, 300 loot tables and 99
  reviewed item IDs. Drone/combat resource checks and the Mixin remapping audit pass.
- The release JAR passed CRC, duplicate-entry, resource-equality and version checks;
  it includes the layout metadata and excludes all test classes.

No graphical Minecraft client or live server was launched for this patch. The
regression covers the actual server map-preparation pipeline and its client-bound
packet, plus real in-memory chunk generation.

```text
bobin_craft-2.0.1.jar
Bytes: 3933117
SHA-256: 1299cd31e4384673ca437f0948166933c0d4ca371873db6c7daeb50db388ce97
```

The project archive contains this JAR, updated sources, regression tests, the fixed
layout metadata, map/architecture previews and installation instructions. The 2.0.0
notes remain as historical documentation; this file records the current patch.
