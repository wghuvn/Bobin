# Forest Hub rebuild

Hub design 4 replaces the supplied Hub generation and art with a newly authored
forest expedition camp. The reference determines the service placement, timber
enclosure, canvas shelters, central gathering area and forest setting. Minecraft's
block geometry and existing game systems determine the implementation; this is a
voxel interpretation rather than a claim of pixel-identical reproduction.

## Layout

The palisade occupies x/z −46 through +46, for a 93 × 93 footprint. The walking
surface is y=70 and standing positions begin at y=71. North is negative z; the
gate approach is on the south. Five watchtowers have decks at y=77, walkable
platforms, ladders, gray roofs, lanterns and wing banners.

The new `bobin_craft:forest_camp` dimension type fixes the time at 12500 for a
twilight setting, with low ambient light and lantern-lit services. The retained
`safe_bunker` biome ID is a saved-world compatibility identifier; its current
palette is the forest camp palette.

| Area | Implemented contents |
| --- | --- |
| Northwest rest quarter | Three olive tents, six beds, storage and a medical station |
| Northeast kitchen | Two canvas flies, cooking stations, chimney, food chest, barrels and two dining tables with benches |
| Southwest deployment | Net-covered lockers, weapon racks, radio, a broad map table, shared storage and starter chest |
| Southeast trading | Canvas-covered weapon display and counter, quartermaster, workbench, anvil, supply crates and a parked block-built truck |
| Center | Campfire, stone ring, log seating, crates and lamps |
| Southern entrance | Closed timber gate, sentries, banners, sandbags, approach lights and welcome sign |

Connected dirt/gravel paths provide approaches to all six service zones. Broad,
irregular shrub and flower islands and two small spruce trees divide the
courtyard while retaining clear service positions and routes. Ground materials
form coherent earth patches with occasional gravel along the worn routes. The surrounding
terrain forms a finite uneven forest basin with coordinate-addressed spruce
trees, undergrowth, rock outcrops, a winding southern approach, and an eastern
stream descending through northern rock pools and falls.

## Code and resources

| File | Responsibility |
| --- | --- |
| `raid/HubLayout.java` | Design version, service and sentry positions, areas and route constraints |
| `raid/HubBuilder.java` | One final block plan, world placement, initial inventories and arrival checks |
| `raid/HubChunkGenerator.java` | Terrain heights/materials, stream pools, chunk filling and matching column queries |
| `raid/HubForestDecorator.java` | Deterministic spruce forest, undergrowth and rocks clipped per chunk |
| `raid/HubContent.java` | Newly authored camp block registration, shapes and map-table action |
| `raid/HubNpcs.java` | Friendly service characters and sentries at the new service anchors |
| `raid/RaidManager.java` | One-time initialization and saved-layout compatibility gate |
| `tools/build_forest_camp_art.py` | Original 64 px camp textures and static block resources |

Java paths in this table are under `src/main/java/com/bobincraft/`. Resource
models, textures and blockstates live under
`src/main/resources/assets/bobin_craft/`; loot and mining tags use the normal
Minecraft data resource paths. See [the asset inventory](OUTPOST_ASSETS.md).

## Persistence and use

Create a **new Warzone world**, or repair an existing 1.1.1 design-4 camp as described below. Initialization builds the Hub,
stocks its starter/food containers once and spawns persistent friendly NPCs.
Subsequent loads preserve player edits and inventory state.
The current plan has 13 sentry posts, including the towers, checkpoint and
campfire gathering positions, plus four service NPCs: 17 friendly NPCs in total.

Older Hub layouts are unsupported. In particular, `metal_floor`, `bunker_wall`
and `equipment_panel` were removed from the block registry, so Minecraft cannot
retain those materials when it loads a save containing them. Complete
preservation of an older camp is not guaranteed.

Raid initialization does not intentionally rebuild old raid state or storage,
and disables raids for an incompatible recorded layout because the rebuilt
services occupy different coordinates. Unsupported or incomplete raid state
also prevents initialization. This compatibility gate is not a world migration
or a guarantee against missing legacy blocks during Minecraft registry loading.
A **fresh Warzone world is required** when upgrading from those older layouts.

## Verification and limits

The repository includes these checks:

- `HubLayoutTest` verifies service orientation, distinct anchors and connected
  approaches inside the palisade.
- `RaidStateTest` covers saved raid state and compatibility behavior.
- `tools/server-smoke/src/com/bobincraft/smoke/HubPlanChecks.java` checks the
  actual final block plan, including arrivals, fixtures and access.
- `HubForestChecks` checks terrain/column consistency and forest generation.
- `tools/validate_resources.py` and the art generator's `--check` mode validate
  resource references and deterministic art output.

Executed offline verification for 1.1.2 passed 2,352 actual-plan
assertions with 6,575 reachable ground cells. The unchanged terrain/forest
generator previously passed 3,179,254 assertions. The JUnit suite passed all
94 tests. The resource check passed 220 JSON files, 46 loot tables and 83 reviewed
item IDs, including deterministic comparison of the 24 camp textures and 69 camp
resource files. These are code/resource checks, not a live client playtest.
The repair delta contains 1,968 conditional edits; replay against the exported
1.1.1 baseline exactly reproduces the new plan and leaves inventory blocks intact.
`tools/check_hub_sign_text.py --minecraft-jar <local-client.jar>` verifies that all
66 translated sign lines fit Minecraft's 90px native wall-sign width.

The offline preview renderer reads exported block coordinates and actual model
resources. Its overview and roof-removal view help inspect the layout; lighting,
fluids, plant tint and block-entity rendering are approximate, and NPCs are
omitted. These renders are not screenshots from Minecraft.

Full visual fidelity, client lighting, camera/foliage cutaways, live NPC
interactions, multiplayer trading and performance still require an in-game
client check. The closed gate is deliberate Hub boundary scenery; deployment
continues through the existing planning-table interface. The truck is static
block scenery. Decorative ammunition crates do not introduce a new ammunition
system.

## Nature revision (1.1.1)

More than a quarter of the courtyard keeps a living grass/moss surface. Worn ground is reserved for connected circulation routes, work sites and the campfire plaza; gravel forms coherent patches rather than isolated speckles. Low grass, ferns, flowers and additional trees fill unused areas without blocking service anchors.

Forest undergrowth begins at 48 blocks from center, immediately beyond the wall supports. Spruce is mixed with birch and oak crowns and open glades. Nearby Gaussian hill forms rise behind the northwest wall, to the west, north and southeast, and beside the southern approach; grounded rock clusters and moss interrupt the slopes. The creek continues to z=103 and bends toward the front of the scene. The finite terrain extends to a maximum coordinate radius of 160, beyond the normal preview framing. Actual biome grass/foliage colors are used by the preview renderer.

## Repairing a 1.1.1 camp

Update to 1.1.2, enter the Hub with operator permission level 2, and run
`/bobin hub repair`. The command accepts only initialized design-4 camps.
The bundled delta is fully decoded before any mutation, applies only to the
expected old block or air, and protects inventories and other persistent block
entities. It preserves stored items, raid progress and NPCs. Player replacement
blocks are skipped. New camps receive corrected geometry directly.

The repair includes connected roofs and net frames, restored tower bearings,
lantern brackets, the continuous nine-tile map and native zone signs. The third
tent shell is shortened away from the northern tower; its beds and chest retain
their positions. Terrain generation and existing forest/creek saves are unchanged.
