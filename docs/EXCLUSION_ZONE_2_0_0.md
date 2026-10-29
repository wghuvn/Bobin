# Black Alder Exclusion Zone — 2.0.0

The Bobin_craft preset now has one authored combat world: seed **19860426**, profile
**6**, size **2048 × 2048**, centered on X/Z = 0. The seed in the ordinary creation
screen does not randomize this dimension. The forest hub remains the entry point.

## Seed selection

Ten natural-terrain candidates were surveyed: `42`, `7919`, `-734842`, `1986`,
`260426`, `19860426`, `2013`, `1337`, `20261022`, and `3407`. The chosen seed combines
broad dry lowlands, wooded ridges, marsh pockets and a branched river network with
three usable bridge alignments. A 16-block survey of the inner 1760-block square
found 11,230 dry samples, 1,091 wet samples, 387 ridge samples and 1,106 marsh samples.
Categories can overlap. The choice favors the requested setting and workable
infrastructure; it is a design choice, not a universal ranking of Minecraft seeds.

## Areas

Y is the approximate area level; individual residential foundations follow local ground.

| ID | Area | Center X / Z | Approx. Y | Main buildings | Character and purpose |
| --- | --- | --- | --- | --- | --- |
| 1 | Black Alder village | -568 / 470 | 74 | 5 | Plaster/timber homes, shop, garden, well, lean-to, bus shelter and an abandoned Humvee. |
| 2 | Kamenka brick village | 486 / 92 | 79 | 5 | Brick homes around a different street arrangement, side-facing clinic, well, bus shelter and supply truck. |
| 3 | Pine Hollow logging hamlet | -526 / -458 | 74 | 4 | Scattered cabins facing different directions, shack, repair shop, timber piles and a truck. |
| 4 | Zarya collective farm | 386 / 630 | 73 | 3 | Long barn with hay and loft, farmhouse, machine shop, two irrigated fields, grain silos and truck. |
| 5 | Northern sawmill | -498 / -56 | 70 | 3 | Timber processing hall, work benches, stockpiles, office, repair shop, cargo store and logging truck. |
| 6 | Concrete works No. 4 | 520 / -420 | 72 | 3 | Sawtooth-roof production hall, mixers, office, maintenance shop, silos, brick chimney, cargo store and tank. |
| 7 | Unfinished workers' estate | -74 / -488 | 74 | 3 | Two exposed concrete frames with continuous stairs, partial masonry, reinforcement and builders' shack. |
| 8 | Reclamation waterworks | -270 / 188 | 77 | 3 | Pump hall, office, workshop, twin settling basins, connecting pipework, tower and service Humvee. |
| 9 | Hunters' hollow | 680 / 698 | 76 | 2 | Small repaired timber/metal shelters, well, stacked firewood and narrow local access. |

Village homes differ in dimensions, orientation, wall palette, roof direction, room
partitions, kitchen position and furnishing. Industrial footprints use graded service
yards; residential pads are individual, preserving woods and uneven ground between
homes. The lighting and material palette use the project's existing rust, muted
plaster, weathered timber, brick and concrete assets.

## Transport and water

Three selected crossings span wet channels rather than arbitrary road clearings:

| Bridge | Center X / Z | Direction | Design |
| --- | --- | --- | --- |
| 1 | 0 / 16 | North–south | Weathered concrete road bridge with concrete abutments and piers. |
| 2 | 96 / -672 | East–west | Oxidized steel/copper truss over a concrete/asphalt deck. |
| 3 | 368 / 208 | North–south | Timber-supported metal service crossing with industrial railings. |

The road planner connects every area and bridge end to the return shelter. It routes
around wet ground and building footprints, allows vehicle-width clearance and never
jumps onto the side of a bridge. Each building has a local access lane. Bridge piers
reach the existing bed; the river is not filled to make a causeway.

Every area has a buried collector with a downward grade, a maintenance shaft and
ladder, an iron hatch controlled by a curb lever, and a real outlet. Seven collectors
reach receiving water; the remaining two use infiltration basins. Shallow recessed
road shoulders and the waterworks pipework make the drainage purpose visible.
These are static block-built drainage environments; no custom fluid simulation runs
every tick. Water uses Minecraft's normal fluid behavior.

The return radio shelter remains near **0 / 696**, with a clear landing circle,
signal fire, radio, supplies and field antenna.

## Generation and old content

`ExclusionZonePreset` owns the fixed profile. `ZonePlan` owns geography, roads,
bridges, foundations and drainage. `ResidentialStructures` builds all new architecture;
`ZoneInfrastructure` builds roads, bridges, collectors and the return shelter. Designs
are cached and sliced into individual chunks, so generation order cannot split a
roof, stair flight or pipe. Vegetation honors the same authored reservations.

Sixteen retired renderer/module classes were removed, including both old bridge
generators, the compound renderer, old combat structures and their module builders.
Old terrain and NPC metadata types remain for reading saves. Existing saved chunks
are not erased. Thirteen self-drop loot tables for old, never-registered imported
prop IDs were removed; the equipment-panel art is now bound to a real block.

Use a new world for this release. Install one 2.0.0 mod JAR on each client and server.
The source ZIP includes `build/libs/bobin_craft-2.0.0.jar` and exact plan previews.
