# Bobin Craft 2.2.0 — occupied exclusion zone

Minecraft 1.20.1 / Java 17 / Fabric. Fixed seed **19860426**, size **2048 × 2048**, generation profile **8**. Install the new JAR on both client and server and create a **new world**. Existing generated chunks are not rewritten. Profiles 1–7 remain readable; this release does not claim to preserve the geometry of newly generated chunks in older authored saves.

## Changes from the supplied screenshots

| Problem/request | Implemented behavior |
| --- | --- |
| Tangled rectangular wire grids | Three independent, continuously sagging conductors. Native block entity meshes retain their geometry across chunk serialization; neighboring phases never acquire automatic cross-connections. Whole trees are excluded from cable corridors. |
| Crates floating over pallets | Their wooden supports reach the crate bottoms. |
| Unsupported well post / plants in water | Well copings meet the posts; the shaft and air above its water are cleared. |
| Excessively visible sewer mouth | A small dark, hollow pipe at the bank replaces the broad concrete headwall and basin. Its connection rises inside the bank. Tunnel generation leaves receiving water intact. |
| Manhole appearance and animation | A round cast-iron cover lies flush with the ground, without a raised collar. Use it to slide the cover sideways over 16 ticks. The animation and open state synchronize and persist. A separate lever is unnecessary. The opening becomes passable near the end of the slide; closing waits while someone occupies the aperture. |
| Uniform dirt strips and hard edges | Paths use dark trampled earth, forest soil, litter and occasional ruts. Irregular, progressively thinning edges merge into vegetation; paved grading blends over a wider verge. |
| Gaps beside slabs | Fences, iron bars, panes and walls connect to both slab halves in generated and player-placed geometry. Dissimilar slabs can fill one cube; a solid ordinary block can also fill a slab's empty half. Both stored materials render and persist; breaking drops both ingredients. Containers and other block entities are excluded from material combination. |
| Repeated cottages and log piles | Seven exterior families add hipped roofs, different gables, split rooflines, flat roofs, two-storey houses and collapsed wings. Eight ground-floor arrangements remain furnished. Homestead details vary between wood stores, workshops, washing lines, small animal shelters and walled work areas. Log stacks vary in species, length, height, direction and broken ends. |
| Empty industrial yards | A refinery process strip, pump skids, furnace, heat exchangers, supported racks, transfer manifold, loading connections and an isolated flare occupy separate service areas. The substation has switching, transformer and outgoing feeder bays, cable trenches and control space. |
| Empty but promising clearings | 24 small isolated discoveries use eight structure families. Repeated families change rotation, mirroring, damage, dimensions or furnishing. They receive no additional road or utility-pole spur. |
| Armed occupation missing from the scenery | Faction-colored markers, sandbags, warning signs, barbed wire, guard shelters, radios and ammunition corners mark the occupied entrances. Travel lanes stay open. Existing faction residents, combat and finite rosters remain in place. |

## Industrial reference projects

These are compressed visual adaptations for a fictional abandoned combat zone, with the existing rusted metal, aged masonry and dark forest materials. Equipment locations are arranged around use and maintenance access; the layouts are not engineering drawings or exact replicas.

- **PNG Oil Refinery Project, Port Moresby — Clough / InterOil.** The contractor's project description identifies a tank farm, crude distillation unit, utility systems and loading infrastructure. Those broad components informed the map's storage/process/dispatch organization. [Project source](https://www.cloughgroup.com/en-gb/projects/png-oil-refinery-project).
- **Alexandria Refinery Green Project — EBRD.** The published facility inventory informed the separation of tanks, process support, workshops, utility and administrative space. [Project summary](https://www.ebrd.com/content/dam/ebrd_dxp/documents/project/51018/alexandria-refinery-green-project-nts.pdf).
- **Phnom Penh Transmission and Distribution Expansion, Phase II — JICA.** Chapter 8, especially the GS5 survey in Figure 8.3-11 and the AIS equipment sections, informed the fenced switchyard, transformer bays, gantries, control house and access clearances. [Study and survey](https://openjicareport.jica.go.jp/pdf/12182697_03.pdf).
- Supporting process reference: [EIA — refining process](https://www.eia.gov/energyexplained/oil-and-petroleum-products/refining-crude-oil-the-refining-process.php). Supporting component reference: [Hitachi Energy — air-insulated switchgear](https://www.hitachienergy.com/products-and-solutions/high-voltage-switchgear-and-breakers/air-insulated-switchgear).

## Locations

The 11 principal district coordinates are retained: Black Alder (-568, 470), Kamenka (486, 92), Pine Hollow (-526, -458), Zarya farm (386, 630), sawmill (-498, -56), concrete works (520, -420), workers' estate (-74, -488), waterworks (-270, 188), Hunters' hollow (680, 698), refinery (-498, -876), substation (692, -138). Fourteen scattered homesteads and 24 isolated discoveries supplement them. See the updated overview image for their distribution.

## Interaction

Use the round cover directly in normal view, or approach and interact through Bobin mode. Its lid slides east onto the neighboring ground; avoid blocking that landing. The opening keeps the existing supported ladder into the collector. The final small pipe is an outlet, not another human entrance.

To combine materials, use a different slab or an ordinary solid building block on the empty half of an existing slab. Normal same-material slab placement remains unchanged. Sneaking retains ordinary placement. Mixed blocks use full-cube collision and save both material states. The cover and utility blocks can also be obtained through `/give` for building.

## Validation and inspection

The release includes the production JUnit output, resource checks, remapped-mixin audit and checksums. The native chunk regression includes all 24 discoveries and runs both forward and reverse generation orders. Additional checks cover cable phase separation and mesh serialization, manhole animation timing, mixed-material persistence and collision, slab connections across chunk borders, pallet/well support, unobstructed faction entrances and water-preserving outfalls.

`zone-overview.png`, `zone-interiors.png` and `zone-architecture.png` are diagnostics made from production plans. `zone-utility-mesh.png` captures production renderer vertices for the actual circular cover and curved conductors. Diagnostic images use inspection colors and are **not Minecraft screenshots**. No graphical client playtest was performed. In particular, third-party shader water rendering has not been verified.

Runtime loads the baked profile-8 layout and retains asynchronous deployment-map preparation. The offline planner is only a build/verification tool.
