# World rebuild 2.1.0

This release addresses the terrain, building and road problems shown in the supplied screenshots and adds the requested industrial and utility infrastructure.

## Installation and compatibility

Use the 2.1.0 JAR on the client and server and create a **new world** using the Bobin_craft preset. The curated combat zone is seed **19860426**, size **2048**, profile **7**. Existing chunks are not overwritten. Saved legacy profiles remain readable, but an old world is not a substitute for the new complete layout.

## Layout

There are 36 district buildings and 28 buildings across 14 isolated homesteads: **64 principal buildings**, plus yard shelters and scenery. The homesteads have their own narrow approaches, sheds and gardens. They do not add a military garrison to every isolated house.

| District | X | Ground Y | Z |
|---|---:|---:|---:|
| Black Alder village | -568 | 74 | 470 |
| Kamenka brick village | 486 | 79 | 92 |
| Pine Hollow logging hamlet | -526 | 74 | -458 |
| Zarya collective farm | 386 | 73 | 630 |
| Northern sawmill | -498 | 70 | -56 |
| Concrete works No. 4 | 520 | 72 | -420 |
| Unfinished workers' estate | -74 | 74 | -488 |
| Reclamation waterworks | -270 | 77 | 188 |
| Hunters' hollow | 680 | 76 | 698 |
| Black Alder oil refinery | -498 | 79 | -876 |
| Eastern distribution substation | 692 | 71 | -138 |

Three crossings retain separate reinforced-concrete, truss and logging-trestle designs. Bridge approaches are aligned, graded and merged with the full carriageway. Roads no longer place bright gravel pixels and uniform pale borders along every lane. Road contacts are reused to avoid redundant nearby approaches.

## Clearings and wear

- Whole trees yield to buildings, crops, real fence lines, roads and overhead services. Individual canopy blocks are not clipped at site bounding boxes.
- Industrial clearings have irregular boundaries. Separate building and machinery pads blend into the underlying ground.
- Vehicles occupy level turnouts connected to loading tracks, with terrain blending around the footprint.
- Coherent areas of moss, exposed brick, rotten timber, dirt and damaged surfacing replace scattered single-block speckles.
- Domestic interiors have eight partition arrangements, with variations in dimensions, furnishing and orientation. Exterior additions include porches, dormers, boarded windows, damaged roofing, firewood, vines, broken boundaries and rear yards.
- Sawmill timber racks, lean-tos and loading piles; concrete-works aggregate, equipment and conveyors; waterworks maintenance structures; and unfinished-estate scaffolding and materials distinguish the service yards.

## Utilities

The refinery includes four oil tanks with circular containment edges, two process towers, pipe racks, a chimney, storage and vehicle loading access. The substation includes transformers, insulators, overhead gantries, perimeter fencing and control rooms. Its distribution network has connected wooden poles and steel supports with three thin, sagging cable runs. Span heights account for terrain and building clearance.

Eleven underground collectors descend toward receiving water or infiltration basins. Each street entrance has one controlled iron cover and a continuous supported ladder. The extraction clearing has an H marking and irregular mixed ground that follows the existing terrain.

## Verification

- **285 JUnit tests passed, 0 failed, 0 skipped.**
- Real Minecraft chunks: **45 positions**, generated in both forward and reverse order, covering bridges, districts, homesteads, utilities, collectors, extraction and world edges.
- Real registered utility-cable blocks survive the complete chunk generation pipeline.
- Screenshot regressions check whole tree crowns, clearing corners, connected level vehicle pads, wire support and clearance, single-cover manholes, road-width height joins and the H marking.
- Deployment-map preparation, packet decoding, offered choices, safe landing and NPC street access pass their existing checks. Cold map preparation is checked against a five-second limit; runtime loads baked geometry instead of executing the layout search.
- The baked layout matches the offline deterministic planner. Retired structure and bridge builders remain absent.
- The resource, equipment, drone, combat and vehicle-geometry validators are included in the project.

The previews show actual authored geometry using generic diagnostic colors. No graphical Minecraft client or live multiplayer playtest was performed; these checks establish generation and gameplay contracts, not a screenshot-based visual approval.
