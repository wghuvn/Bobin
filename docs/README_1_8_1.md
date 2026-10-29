# Bobin Craft — camera OSD, distinct bomber drops and persistent marks, version 1.8.1

Minecraft **1.20.1**, **Java 17**, Fabric Loader **0.15.11 or newer**, and matching
Fabric API are required. Install the production `bobin_craft-1.8.1.jar` on every
client and server, with one Bobin Craft version in the mods folder.

## Camera, signal and surface marks (1.8.1)

Both aircraft use a sparse camera OSD inspired by the supplied reference: a small
zoom field and heading, actual camera/motor state, altitude, operator distance,
speed, battery, signal bars and remaining payloads. Automatic NPC target boxes
and their entity scanning are removed. There are no automatic help panels or
persistent control tips.
**H** opens help explicitly; **Alt** reveals the unlink button. **Mouse wheel**
zooms the bomber from 1× to 4× and FPV from 1× to 2×. Optical mounting positions,
the FPV contact probe and the visible operator model are retained.

The camera has mild receiver pixelation from the start: a healthy Full HD display
uses a 1280 × 720 world-video frame, enlarged with nearest-neighbour sampling.
HUD text stays sharp. Capture detail drops further with weak reception, alongside
held frames, brief stalls and blackouts. Black/grey interference fragments are
smaller and about four times as numerous at comparable weak signal; broad thick
bands are replaced by several thin, short streaks.
The first 35% of the available distance has full clear-line reception; attenuation
then increases smoothly. Nearby leaves and a thin wall no longer spoil a 15-block
link. Thick terrain still obstructs it. Existing 96-block bomber / 112-block FPV
limits, server view-distance bounds and the two-second continuous-loss grace remain.

| Bomber | Capacity | Physical drop and detonation |
| --- | --- | --- |
| General purpose | 3 | Grenades inherit motion, bounce and detonate after 3 seconds. |
| High explosive | 2 | Vertical finned bombs fall faster, inherit less horizontal motion, and detonate on contact after 6 ticks of arming; 8-second fallback. |
| Fragmentation | 3 | Segmented charges use alternating release bays and detonate within 1.8 blocks of a solid surface after 12 ticks of arming; contact / 3-second fallback. |

Every charge spawns entirely below the carrier. An obstructed bay spends nothing;
carrier separation protection, server authority and inert reloaded charges remain.
Each payload family now has its own dropped model and ballistics. Health damage
and injury probabilities retain the 1.7 balance.

Blood sprays contain up to three times as many droplets, with larger particles
and temporary ground splashes. Additional pixel stains and small grey fragment
dents are attached to actual floor/wall/slab/stair collision faces. These cosmetic
marks persist across saves and reconnects without replacing blocks. Breaking or
changing their supporting surface removes them; scorching the same soil keeps
them. There are at most 4,096 marks per dimension, with oldest marks replaced,
and at most 512 nearby marks synchronized to each viewer, including drone viewers.

See [the Russian 1.8.1 guide](docs/UPDATE_1_8_1_RU.md) and
[validation](docs/VALIDATION_1_8_1.md). The [1.8.0 guide](docs/UPDATE_1_8_0_RU.md)
records the retained drop and surface-mark mechanics; its target boxes and native
video presentation are superseded by 1.8.1. Existing worlds remain supported;
update every client and the server together.

## Retained variants and damage balance (1.7.0)

Two creative inventory tabs, **Kamikaze Drones** and **Bomber Drones**, contain
three aircraft each. The original general-purpose kits keep their registry IDs;
new kits add infantry fragmentation, light-cover breaching and blast-focused
payload roles. Their recipes, icons, payload colours and tooltips are distinct.
Variant identity survives saves, recovery and relaunch, and follows each dropped
charge. A native **Bobin Craft Soldier Spawn Egg** creates the actual armed guard
with its AI, independent of generated encounter rosters. Egg-spawned soldiers
remain present in Peaceful mode and do not attack there.

Wave damage is capped independently of radius. The ordinary grenade has a maximum
of **10 pressure + 6 fragment damage**; the ordinary kamikaze has **16 + 8**.
Fragmentation FPV uses **6 + 8**, breacher FPV **14 + 4**, high-explosive bomber
**12 + 3**, and fragmentation bomber **4 + 8**, before armour and falloff. Injury
energy and limb-loss chances are independent of these health caps. The breacher
can make a small opening in nearby wooden cover, subject to terrain-damage rules
and world protections. Camera presentation and bomber drop policies in the
[historical 1.7.0 guide](docs/UPDATE_1_7_0_RU.md) are superseded by 1.8.0.

## Retained NPC damage feedback (1.6.1)

The bomber has its original unobstructed forward/downward video feed again.
The FPV viewpoint sits at the cyan lens on its physical model; the visible tip
below the view is the underslung warhead's contact probe. A shorter near clip
applies only while using FPV optics, allowing this close probe to render.
Both views start level, and the operator's world model remains visible.

Missing arms have vertical cuts at the sides of the torso; missing legs retain
horizontal cuts. Combat NPCs receive normal health/armour damage and retain
their hurt cooldown, but no longer flash red, kick their limbs or jerk backwards
from ordinary hits. Walking, weapon actions, injury falls/crawling, death poses
and explosion pressure still work. Hit droplets now also appear on normal NPC
damage; wound sprays and temporary ground marks are modestly fuller.

See [the Russian 1.6.1 guide](docs/UPDATE_1_6_1_RU.md) and
[the validation record](docs/VALIDATION_1_6_1.md).

## Retained contact blasts and injured poses (1.6.0)

Armed kamikazes now also detect flush physical contacts at zero/tiny speed using
actual block collision shapes. Only the initial launch floor is exempt; walls,
ceilings, entities and subsequent ground contact trigger detonation.

Camera perspective restores on exit. Mouse steering, arming and the bomber's
downward camera remain available.

Losing either leg now produces a short fall into a prone crawl, with low eye height
and a horizontal collision box. Missing arms/legs are hidden individually and have
small red wound caps. Arm injuries clear both hands, prevent pickups and suppress
held-item rendering. Limited red droplets leave short-lived ground marks. Injured
corpses remain visible for eight seconds before native removal.

The new explosion is a textured, six-face voxel cloud: a bright core and lobed
orange fireball cool into dark smoke. These are actual 3D particle meshes, rather
than camera-facing explosion pictures. Graphics settings reduce the particle
budget. Underwater effects remain bubble/splash based.

The [Russian 1.6.0 guide](docs/UPDATE_1_6_0_RU.md) records the earlier release;
its camera arrangement is superseded by later releases. Install only the 1.8.1 production
JAR, removing the previous Bobin Craft JAR from each mods folder.

## FPV and bomber drones

Six craftable drone kits add remotely piloted flight to existing and new worlds.
The **FPV Kamikaze Drone** has a banked forward camera and an impact payload;
the slower **Bomber Drone** carries two or three independently released charges and can
switch to a downward camera. **Mouse steering and assisted flight are now the
default.** Release movement keys to brake and hold position and height; the
bomber remains level while its camera looks around. The FPV also retains an
optional advanced acro mode. Battery, signal, flight telemetry and payload
status appear in the live video HUD. The operator remains behind and can still
be attacked.

Use a drone kit outside the safe camp to launch, then press **R** to start the
motors. **Mouse** turns and aims the camera; **W/S** moves forward/back, **A/D**
strafes, **Space** climbs, and **Shift** descends. Release movement keys to hover.
**C** switches the bomber camera and **Left click** releases one physical charge
using the selected variant’s detonation policy, with no minimum altitude. The charge spawns fully below the carrier collision box; an
obstructed bay is rejected without spending a charge. **M** switches FPV assisted/advanced flight,
**H** shows help, and **Esc** returns to the operator and stops the motors.

**U** unlinks the current aircraft and starts a gentle landing, freeing the
controller to deploy another drone. The **Unlink and land** button appears while **Alt** is held; use its pointer
to perform the same action. The old aircraft remains recoverable.
Esc keeps the current link for reconnecting.

Land and stop the motors before interacting with an owned drone to recover it;
remaining battery and bomb count are preserved. Survival deployment replaces the kit with a
**Drone Controller**; use it to reconnect after leaving the aircraft's camera.
These controls take priority over the field map and top-down camera while piloting.

See the [1.4.6 drone gameplay and control guide](docs/DRONES_1_4_6.md) for recipes,
advanced FPV controls, link limits and recovery. The compact FPV uses a 0.48 × 0.26 block collision box and a matching low-profile
model, fitting an open door and half-block gaps. Its assisted flight has gentler
acceleration, shorter mouse-turn lag and controlled braking. Armed FPV contact
still triggers its impact payload. Ordinary bomber grenades fall, bounce and settle, then explode three seconds
after release. HE and fragmentation drops use their own policies shown above. Drone explosions now shatter
exposed windows, leave scorched grass, and calculate invisible shrapnel behind cover.
They do not carve TNT craters or start spreading fires.

## Drone explosions and injuries (1.5.0)

Dropped grenades and kamikaze drones have separate blast profiles with a brief flash,
low pressure dust, soil jets and lingering smoke. Exposed glass blocks and panes
shatter; grass and forest soil gain fully or partially scorched textures. Solid
cover blocks fragmentation. Underwater blasts use bubbles and shorter fragment range.

Surviving players and combat NPCs can bleed. Light, severe and critical wounds have
**10-minute, 5-minute and 60-second** deadlines at 20 ticks per second. Facial wounds
turn the player's view red; the HUD shows the remaining time. Hold use with a
**Field Tourniquet** for **two seconds** to stop bleeding, or use it on an injured
person to help them. Craft two from two string, two leather and one iron ingot.
This item treats all mod bleeding, including the requested facial-wound mechanic.

Combat NPCs can lose arms or legs: the missing model parts synchronize to clients,
leg loss slows movement, and arm loss drops the weapon and prevents firearm use.
Tourniquets stop bleeding without restoring limbs. Injuries persist across saves;
old saves start healthy, and death/respawn clears player wounds. Timers pause while
unloaded, offline, in the safe Hub, or while the affected player is creative/spectating.

See [the 1.5.0 gameplay and implementation guide](docs/DRONE_BLASTS_1_5_0.md).
Use `/gamerule bobinDroneTerrainDamage false` to disable window and soil changes.

## The forest combat zone

New Bobin Craft / Warzone worlds use generation profile **5** and the existing
2048 × 2048 forest landscape. Military compounds are replaced with small,
scattered residential settlements: weathered homes, isolated homesteads, roadside
houses and abandoned hamlets. Individual fenced gardens, furnished rooms,
outbuildings, porches, domestic work stations, dirt lanes, utility poles and worn
civilian vehicles leave woodland and uneven terrain between properties.

The occupants are **marauders, bandits and unidentified armed people**. They wear
patched civilian jackets, caps or hoods rather than military uniforms. Each
settlement has a small, finite roster. Residents patrol authored lanes and
accessible yards, keep watch, inspect supplies, repair equipment, tend a radio
station and take breaks. Alerts interrupt their routines; confirmed threats lead
to bounded pursuit, searching and cover seeking. Perception and each shot check
solid obstructions. Conventional guns use finite magazines, reloads and reserves;
a loaded or defeated resident is not replaced by endless reinforcements.

River bridges and the styled radio return shelter remain. The return area is
kept clear of settlement encounters. The safe forest Hub, deployment, loot and
extraction systems remain available.

See [the 1.3.0 implementation guide](docs/FOREST_SETTLEMENTS_1_3_0.md) and
[vehicle texture and geometry repairs](docs/VEHICLE_TEXTURE_REPAIRS.md).

## Installation and saved worlds

Create a **new Bobin Craft / Warzone world** to receive the residential layout.
For a new dedicated world set `level-type=bobin_craft:warzone` in
`server.properties`. Previously saved generator profiles **1–4 are preserved**;
updating the JAR does not delete their structures or replace their residents.
Do not manually change the generation version of a populated world.

The drone update works in saved worlds without changing their generation
profile. Install 1.7.0 on all connected clients and the server.

Vehicle texture, model and collision repairs apply automatically to existing
placed vehicles after updating the mod. Their reserved footprints and registry
IDs remain compatible. A new world is only required for the new settlement
layout, not for these asset repairs.

The standard world is 2048 blocks wide. Custom **256-block** arenas can have no
suitable settlement location after river, border and return-area clearances are
applied. Larger maps have more room for scattered homes.

## Play

- Use the Hub's planning table to choose a deployment and enter the combat zone.
- **M** opens the tactical field map during an active raid. Pan by dragging or with
  **WASD**, zoom with the wheel or **+/−**, and press **R** for the overview. It
  shows terrain and known landmarks without live player or enemy tracking.
- **G** toggles the existing top-down camera. **H** controls the existing roof and
  nearby foliage rendering modes.
- Reach the briefing's return point and remain on the ground within five blocks
  for five seconds. Damage, leaving the area or nearby hostiles interrupts return.
- Shared Hub storage, service NPCs and the one-time starter supplies retain their
  existing persistence. Updating the mod does not refill loot or trade stock.

## Factions, firearms and persistence

Profile-five settlement identities are `MARAUDERS`, `BANDITS` and `UNIDENTIFIED`.
The seven older faction identities and their saved ordinals remain available for
older generation profiles. Ten faction profiles have three original 64×64
textures each. Loadouts are bundled in
`src/main/resources/data/bobin_craft/warzone/faction_loadouts.json`; edit and rebuild
to change them. They are read at startup, not through live datapack reloads.

New residents use a separate persistent roster and UUID namespace from the old
military garrisons. Reserving a slot before spawning prevents duplicates across
chunk unloads and restarts; deaths stay recorded. Work schedules and finite
firearm state also survive saves. There are no automatic encounter, loot or
shared-storage resets. Friendly Hub personnel remain passive.

## Build and verification

```sh
./gradlew --no-daemon --max-workers=4 build
python tools/validate_resources.py
python tools/validate_faction_loadouts.py
python tools/generate_faction_textures.py --check
python tools/generate_drone_assets.py --check
python tools/validate_drone_resources.py
python tools/generate_combat_assets.py --check
python tools/validate_combat_resources.py
python tools/test_vehicle_geometry.py
```

The targeted drone resource validator passes. The full `validate_resources.py`
check is blocked by 14 unregistered legacy loot item IDs already present in the
supplied archive. Those legacy registrations and loot tables have not been
changed or allowlisted by the drone update.

Use a JDK 17. If the host restricts Java ZIP filesystem writes and all dependencies
are already cached, use the [cached build fallback](tools/offline-build/README.md):
`python tools/build_offline.py`. It writes production JARs into `build/libs` and
does not start a game server.

Version 1.7.0 passed the full **252-test** JUnit suite, including existing flight,
collision, wounds, world generation, weapons and raid-state checks. The focused
**28-test** drone suite covers damage budgets, injury energy, video frame timing,
reception loss/recovery, six saved variants, payload profiles, the NPC egg and
native camera/payload geometry. All production Java compiled and the intermediary
Mixin audit passed, including 22 selectors and the near-plane constant target.
Combat/drone resource validators and deterministic asset checks passed. The drone
validator covers seven items, the egg, two tabs, seven recipes, 55 Java translation
references and 19 additional registry/variant labels in three languages.
See [1.7.0 validation](docs/VALIDATION_1_7_0.md).

The [optional development server smoke harness](tools/server-smoke/README.md)
checks runtime registries, dimension precedence and Hub persistence in a separate
disposable server. It is not included in the production JAR. Compilation,
offline geometry previews and automated checks do not replace Minecraft client
verification of appearance, pathfinding, combat balance or multiplayer play.

## Earlier implementation notes

The [1.2.0 combat-zone guide](docs/COMBAT_ZONE_1_2_0.md) describes preserved
profile-four worlds. The [1.1.5 Hub repair guide](docs/UPDATE_1_1_5.md),
[forest Hub design](docs/FOREST_HUB.md), and [asset inventory](docs/OUTPOST_ASSETS.md)
describe the retained Hub and its earlier updates.
