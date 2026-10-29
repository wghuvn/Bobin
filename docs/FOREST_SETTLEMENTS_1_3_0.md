# Forest settlements — 1.3.0

New Warzone worlds select generation profile **5**. The previous military-base
layout is replaced by scattered forest homes: hamlets, isolated homesteads,
roadside houses and abandoned residential clusters. The default arena remains
2048 × 2048; existing natural forest, hills, rivers and route systems remain.

## Architecture and landscape

`ResidentialPlan` samples the natural landscape before assigning individual home
plots, a curved dirt lane, yard entrances and exterior NPC posts. Only each
property and narrow connecting paths reserve or level the ground. Woodland and
uneven terrain between houses remain available to the forest generator. This
avoids a large flat parade ground or perimeter compound around the settlement.

`ResidentialStructures` builds rotated homes with connected corrugated roofs,
doors, windows, porches, beds, household storage, furnaces, tables, gardens,
sheds and work stations. Individual garden fences have open entrances. Utility
poles, overhead cables, fire spots, clutter and worn civilian vehicles establish
the occupied residential setting. Cached blueprints are sliced into chunks so
generation order cannot split a house, vehicle or doorway.

The existing river bridges and radio return shelter are retained. Occupied
settlements avoid the return area and preserve waterways. Small custom 256-block
arenas can lack a viable settlement after required border, river and return-area
clearances; the normal 2048-block arena has more placement opportunities.

## Armed occupants

The new settlement groups map exclusively to `MARAUDERS`, `BANDITS` and
`UNIDENTIFIED`. Nine new original skins depict civilian jackets, repaired cloth,
denim, wool caps and hoods without military insignia. The old seven factions and
their ordinals remain intact for existing saves.

Residents retain the server-owned behavior system:

- Slow patrols follow authored street and yard points and pause to observe.
- Watch, supply inspection, repair, radio and rest duties use visible exterior
  work stations and deterministic work/break schedules.
- Alerts interrupt routine work. Noise provides a location to investigate;
  confirmed visual contact is required for an actual target.
- Pursuit, search and cover movement remain bounded by the local neighborhood.
  Every shot checks solid collision geometry and allies can intercept bullets.
- New loadouts use conventional rifles with finite magazines, reloads and
  reserves, rather than military armor or special-effect firearms.

The finite resident roster is saved separately as `bobin_forest_residents`, with
its own deterministic UUID namespace. A slot is reserved before inserting the
entity. Unloaded residents are never inferred dead and defeated residents do
not respawn. The entity's duty progress, group, residence identity and firearm
state survive saves; future unsupported roster data is retained conservatively.

`/bobin warzone encounters` gives read-only diagnostics for the nearest occupied
forest settlement, including loaded state, issued slots and recorded deaths.
It does not reset or add residents.

## Vehicle repairs

The vehicle generator now uses continuous, equal-density UV projection across
the entire vehicle instead of restarting the texture at every reserved block.
Paint wear and dirt replace the artificial square rivet pattern. The truck's
glazing, hood, mirrors, bed, chassis and canopy supports have been repaired.
The former utility vehicle is presented as a worn civilian vehicle with luggage.
Existing cell IDs and footprints remain compatible with saved worlds.

See [the vehicle report](VEHICLE_TEXTURE_REPAIRS.md) for the defect analysis and
the focused geometry checks. Existing placed vehicles receive the corrected
resources automatically when clients and servers update the mod.

## Installation and compatibility

Install `bobin_craft-1.3.0.jar` with Minecraft 1.20.1, Java 17, matching Fabric
Loader and Fabric API on every client and server. Create a **new Bobin Craft /
Warzone world** for the new residential generation. On a dedicated server use
`level-type=bobin_craft:warzone` for the new world.

Saved generator profiles **1–4** retain their previous landscape, structures and
NPC identities. Replacing the JAR does not migrate a military compound into a
village, delete saved structures, reset inventories or refill encounters. Do not
manually change an existing world's generator profile. Vehicle asset repairs
apply to old worlds without recreating them. Hub services and raid progression
retain their existing behavior.

## Verification

The final production build and **150 Java tests** passed. Coverage includes
saved-profile checksums, residential structure slices and rotations, roof and
door connections, household inventories, task headroom, finite resident saves,
group/loadout mapping, and work schedules paused by alerts. A height-aware
connectivity regression checks actual generated task and patrol coordinates
against the street network on seeds 42, 7919 and −734842, rather than relying
only on a flat synthetic courtyard.

Resource and faction validators check model and texture references, locale
coverage, ten loadout profiles, thirty faction skins and reproducible assets.
Vehicle checks compare all reserved cells, cuboid collision bounds and four
horizontal rotations. Offline previews use production blueprints and models
with approximate lighting. A Minecraft client session is still required to
verify appearance, live navigation, combat balance, performance and multiplayer
interaction; the automated checks do not claim those playtests were performed.
