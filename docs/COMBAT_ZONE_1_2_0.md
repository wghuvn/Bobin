# Occupied combat zone — 1.2.0

Historical profile-four guide. New worlds in 1.3.0 use the residential
[forest-settlement layout](FOREST_SETTLEMENTS_1_3_0.md); saved profile-four worlds
retain the structures and residents described below.

Generation profile **4** adds original post-apocalyptic settlements to the natural
Bobin Craft combat dimension. Existing forest, waterways and Hub behavior are retained.
No assets from the referenced commercial games are included.

## Locations and travel

- Military compounds have barracks, command/armory buildings, maintenance sheds,
  watchtowers, wire, sandbags, cargo stores and detailed static military vehicles.
- Bandit settlements combine timber homes, a gathering building, ruins, salvaged
  containers and stolen vehicles. Industrial sites have workshops, tanks and chimneys;
  smaller forest camps and checkpoints fill suitable dry opportunities.
- Structures are generated as immutable blueprints clipped into each chunk.
  Courtyards have coherent worn-earth/moss patches and connected walking lanes.
  Local earthworks blend into the surrounding terrain; garrisons are never placed at
  the return landing. Tree clearance applies to built footprints and bridge approaches.
- Up to three sparse, intact timber/stone bridges cross actual wet channels. Dry-bank
  approaches, five walking lanes, gradual ramps, piles and railings are part of the plan.
- The existing extraction coordinates are now a radio checkpoint with a canvas
  shelter, antenna, signal fires, supplies, sandbags and the clear marked return area.
  The raid's existing five-second, uninterrupted extraction remains functional.
- Nearby settlements appear through the existing map discovery system. Deployment
  danger considers their faction and distance. Operator reconnaissance uses
  `/bobin warzone bases`, `/bobin warzone tp <id>` and `/bobin warzone encounters`.

The standard map is 2048×2048. Seed 42 has eight occupied sites, 56 assigned
residents and three bridges, including 105×105 military compounds and an 85×85
bandit settlement. Counts and positions vary by seed. Very small custom maps
may have fewer suitable dry locations; rivers and the safe landing take priority.

## Armed residents

Each resident has a persistent site, assignment, post and route. Guards watch fixed
sectors, patrols walk authored circuits and pause at waypoints, and specialists
inspect their supply station, radio or maintenance area before taking breaks.
Work animations and inspection schedules represent those duties; this is not an
autonomous crafting or resource-production economy.

Survival/adventure players are hostile to the military and bandits. Opposing
factions can fight each other, while allies are protected from NPC fire. Creative
and spectator players are not attack targets; Peaceful allows calm inspection.
Perception uses range, view direction and line of sight. Confirmed threats interrupt
duties, nearby allies react to alarms/gunfire, mobile defenders seek local cover,
and loss of contact leads to a bounded search and return to the post. Residents
stay within their compound instead of chasing a player across the map.

Weapons use the existing server-controlled firearm damage and finite magazines.
After all carried ammunition is exhausted, a calm resident can visit the depot and
spend five seconds obtaining **one** extra magazine. Both this finite reserve and
the weapon state persist across saves. Unloading does not reset a work shift,
patrol progress, issued resident UUID, or recorded death. Dead residents do not
respawn automatically.

Population runs on server ticks only when a player is nearby and the site's
chunks are loaded. It never forces distant chunks and creates at most four new
residents per second. An interrupted insertion can conservatively leave a slot
empty rather than risk duplicating saved NPCs.

## Installation and compatibility

Minecraft 1.20.1, Java 17, Fabric Loader 0.15.11+ and matching Fabric API are required.
Replace the earlier JAR; keep only one Bobin Craft version in `mods`.

Create a **new world with the Bobin Craft / Warzone preset** to receive generation
profile 4. Dedicated servers use `level-type=bobin_craft:warzone`. Previously saved
generators keep profiles 1–3, including their terrain, existing builds and items;
the update does not overwrite an existing player's combat dimension. Replacing the
JAR alone will therefore not populate an old profile-3 world with these structures.

## Validation

JUnit covers generation compatibility, dry separated sites, all five architecture
families at four footprint sizes, connected entrances and duty routes, support,
chunk-slice order independence, real-water bridges/ramps, return-area clearance,
guard work shifts, faction relations, finite depot resupply and resident NBT state.
A fixed profile-3 checksum protects previously saved natural terrain.

Additional isolated checks generate real in-memory Minecraft chunks and compare
them with the production blueprints, including forestry and bridge composition.
They do not start a game server, accept an EULA or modify a save. The release JAR
also receives independent Mixin remapping and resource-reference checks.

Preview images show actual generated blueprints and model resources, translated
to local coordinates for inspection. They omit surrounding terrain/forest and
NPCs; lighting and block-entity appearance are approximate. A Minecraft client,
live save/restart cycle, combat playthrough and multiplayer load test have not
been run in this environment.
