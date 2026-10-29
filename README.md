> **Текущий архив: исходники `2.7.7-clean`, АК-47 удалён.** См. [WEAPON_RESET_README_RU.md](WEAPON_RESET_README_RU.md).
> Ниже сохранены исторические сведения о предыдущих релизах; не принимайте результаты их проверки за проверку текущего архива.

# Bobin Craft 2.7.0 — Black Alder Exclusion Zone

Minecraft **1.20.1**, **Java 17**, Fabric Loader **0.15.11+**, Fabric API **0.92.2+1.20.1**.

## Install

1. Put `build/libs/bobin_craft-2.7.0.jar` in the client and server `mods` folders. Keep one Bobin Craft JAR in each folder.
2. Existing **2.4.0–2.6.0 / profile 10** worlds can be kept: these rendering, combat and vehicle fixes apply in existing chunks. To obtain the full revised map when upgrading from **2.3.0 or older**, create a new world and choose **Bobin_craft: Black Alder Exclusion Zone**. Existing structures are not regenerated.
3. Enter through the existing forest camp and deployment table. The combat zone uses **seed 19860426**, **2048 × 2048 blocks**, and **generation profile 10**. The world creation seed does not change this curated combat map.
4. `/bobin warzone bases`, `/bobin warzone tp <id>`, and `/bobin warzone info` inspect the named districts when commands are enabled.

## Update 2.7.0 — Tank layout and grenade animation

The functional vehicle is rebuilt around the T-72M1 layout, enlarged for Minecraft occupants: a forward driver station, rounded turret, two round crew hatches, central breech, rear engine grilles and six road wheels per side. Continuous tracks and external footholds replace the cut-out climbing steps. Light interior lining, seats, instruments, cables and a radio make the open compartment readable. There remains one actual operator; the model's three seating locations do not add a three-player crew system.

**C** cycles the driver periscope, cannon sight and cabin. Entry selects the driver view; the cabin permits free look. **H** opens/closes both hatches. Empty-hand **Shift + right-click** near a hatch operates them from outside. Walking and shooting use the same articulated mesh layout, including when hull and turret headings differ.

Right-clicking the hand grenade now animates a two-hand pin pull, windup, release and follow-through. Release occurs after **22 ticks (1.1 seconds at 20 TPS)**, followed by a **120-tick / six-second fuse**. Crouch at the start for a short throw. The small physical charge uses actual tank surfaces, so the broad vehicle bounds and seated crew no longer seal an open hatch. A closed cover still blocks entry. An already thrown hand grenade keeps its fuse if its thrower dies or disconnects; drone fuse profiles are unchanged.

[Controls and changes (Russian)](docs/TANK_REWORK_2_7_0_RU.md) · [Validation](docs/VALIDATION_2_7_0.md) · [Textured mesh diagnostic](docs/tank-rework-2.7.0.png)

## Previous update: 2.6.0 — Tank access and crew

The functional tank has muted woodland paint, steel access steps, a walkable hull, a hollow turret and a hinged armored hatch. Standing on the moving hull carries a player without occupying its gunner seat. The actual seated NPC or player can be seen and shot through the open hatch. Hull surfaces and the closed cover intercept shots; a physical grenade fits through the opening.

Press **H** from the station, or **Shift + right-click with an empty hand** from the deck near the hatch. The new **fragmentation grenade** is in Combat items and crafts from four iron nuggets plus gunpowder. Right-click throws; crouching makes a short throw for dropping it through the opening. Its three-second fuse uses the existing server-owned grenade and fragment pipeline. A charge already inside the basket retains its internal effect even if the hatch closes. **C** selects the external optical view from the enclosed station.

Hatch state persists and synchronizes. Old tanks acquire an open hatch without regenerating the world. The decorative block-built wrecks remain separate scenery. Retired, incompatible generation sources and stale loot from the input archive are preserved under `tools/retired`, outside runtime source sets.

[Controls and detailed code review (Russian)](docs/TANK_REWORK_2_6_0_RU.md) · [Validation](docs/VALIDATION_2_6_0.md) · [Textured mesh diagnostic](docs/tank-access-2.6.0.png)

## Previous update: 2.5.0

Roof sheets, shingles, tarps and shipping-container panels resolve their neighbors at render time. Matching surfaces have continuous unframed materials, trim only at exposed boundaries, and one container stencil at the start of a face. Connections work across chunk boundaries and in existing saves.

Missing limbs no longer animate their cut surfaces. Corpses stop their locomotion loops. Injured players and guards use alternating shoulder pulls and a trailing surviving leg, with a quiet rest pose; the player renderer now actually turns the forced crawl pose horizontal.

An NPC reserves a single tank seat and approaches a supported station outside its rotated hull. Capable crew remain protected inside after a drone strike. Tank motion uses track contacts and pitched collision sections, bounded vertical/angular changes and interpolated rendering. The exterior has corrected two-dimensional surface UVs, stronger weathering, field stowage and moving tread links. Entering from Bobin mode selects the interior camera in the right order; **C** selects the optical sight from an exterior view too. Control hints wrap and fade, and the vanilla crosshair is hidden while operating armor.

Bullet impacts scatter material-colored fragments, and shell breaches eject bounded three-dimensional debris. A shell's pressure can enter the wall section that this impact breaches, break reachable windows up to 12 blocks away, and damage exposed occupants. Intact walls and trench banks still block the wave. Exposed players receive a short stagger, a fading neutral screen effect, slight camera shake and a quiet original ringing sound. Armor attenuates exposure. The terrain-damage gamerule continues to protect blocks when disabled.

[Detailed fixes and limits](docs/FIXES_2_5_0.md) · [Validation](docs/VALIDATION_2_5_0.md)

## Previous update: 2.4.0

Checkpoint roofs are thin aged sheets, their supports are slender, and sandbags use a darker sculpted burlap model. Trenches have 24–32-block continuous passages, three-block clearance, turns, stair approaches and connected dugouts. NPC activation now checks each nearby post instead of waiting for an entire district to load. Each district gains two checkpoint sentries with connected access to the street.

**Tank controls:** right-click an empty vehicle to sit inside; **WASD** drives, the mouse aims, **V / left mouse** fires, **R** loads, **C** selects the optical sight, and **Shift** exits. The cabin contains a seat, forward slit, ammunition rack, instruments and animated breech. The optical sight follows the cannon axis. Each ammunition item supplies one shell; the rack holds 24. Player loading takes three seconds; NPCs load automatically.

Separate original engine, track, cannon, impact and breech sounds replace the vehicle's footsteps and generic explosion report. Full-block ascent progresses through small native collision-checked movements with suspension pitch. Buried tanks sit higher and retain useful downward firing clearance. Old saved emplacements migrate upward once.

Cannon rounds use a pointed 3D mesh, a flatter gravity trajectory, swept collisions and unclamped motion synchronization. HE impacts apply stronger cover-aware damage, fragments, dust, smoke, voxel debris and bounded terrain destruction. Protected blocks and containers are excluded, and the existing terrain-damage gamerule applies.

Players now share NPC limb loss, wound caps, crawling and hand restrictions. Native tracking synchronizes missing limbs to other players, including observers who arrive later. Drone control retains the physical operator's visible body by keeping the correct focused entity.

[Changes, controls and limits](docs/WORLD_REWORK_2_4_0.md) · [Validation](docs/VALIDATION_2_4_0.md) · [Seated tank station](docs/zone-armor-station.png) · [Checkpoint geometry](docs/zone-checkpoints.png)

## World rebuild

The zone contains **11 districts and 14 separate homesteads**, with **64 principal buildings** and **26 off-road discoveries**, furnished rooms, gardens, farm sheds, workshops and smaller yard structures. Districts include three settlements, a collective farm, a sawmill, concrete works, an unfinished workers' estate, waterworks, hunting shacks, an oil refinery and an electrical distribution substation.

Tree placement rejects a whole tree when its crown would overlap a structure, road, farm, fence or power corridor. It does not shave individual crown blocks against rectangular clearings. Buildings and machinery use small blended foundations; industrial yards have irregular edges, coherent wear, weeds, working areas and storage.

Roads reuse junctions, have graded approaches and muted verges, and use dark half-slabs at paved rises. Three different bridges have clear connected approaches. Vehicle turnouts have access tracks and blended ground instead of isolated gravel rectangles. Existing detailed truck, Humvee and tank wrecks remain part of the scenery.

The refinery has four round oil tanks, process towers, pipe racks, a chimney, loading space and a workshop. The substation has transformers, gantries, insulators, a control building and a connected three-wire distribution network. Wooden poles and steel supports carry three **continuous, independent sagging cables**, rendered as meshes rather than connected voxel ladders. Eleven descending underground collectors have a **flush round manhole cover** that **slides sideways** over 16 ticks, a supported ladder, and a small dark pipe outfall. The large exposed headwalls, raised manhole collars and separate levers are removed. Collector cuts do not excavate air pockets in receiving water. The extraction clearing has a recognizable **H** and a broken, blended edge.

Homes have seven exterior families (hipped, gabled, split, flat, two-storey and damaged variants) and eight interior partition arrangements, varying dimensions and orientations, porches, some dormers, partial roof damage, boarded or broken windows, stove flues, firewood, garden boundaries and coherent weathering. Materials continue the supplied muted forest, rusted metal and aged concrete art. Existing weapons, drones, combat, hub and raid systems are retained.

Roadside settlements now show their occupants: faction markers, sandbag cover, warning signs, barbed wire, radios and guard shelters, with clear travel lanes. Isolated discoveries include burned garages, chapel ruins, radio shacks, hunting camps, field hospitals, water towers, supply caches and quarry shelters. Their furnishing, damage, orientation and yard arrangements vary; no new approach roads or power spurs are added.

Dirt paths use dark trampled earth, forest soil and litter with irregular, thinning edges. Crates meet their supports, wells have supported posts and clear water, and paved verges blend over a wider slope. Different slabs can be combined in the same block; a solid material can also fill a slab's missing half. Fences, iron bars, panes and walls connect to slabs. Mixed materials persist and drop their two ingredients when broken.

The industrial sites adapt real project references into the zone's aged forest style: the PNG refinery project, the Alexandria refinery inventory and JICA's Phnom Penh substation survey. The in-game arrangements are compressed interpretations, with separate tank storage, process equipment, supported pipe racks and dispatch areas; the substation has switching, transformer and feeder bays. [Design references and changes](docs/WORLD_REWORK_2_2_0.md).

The deployment-map fix is retained: the layout is baked into the JAR and map preparation is warmed asynchronously. Runtime does not run the offline layout search.

## Documentation and previews

- [Minecraft Steve/Alex first-person GLB hands: redesign, skin binding and verification](docs/armament/HANDS_REDESIGN_RU.md)
- [Rebuild details, coordinates and validation](docs/WORLD_REWORK_2_2_0.md)
- [World overview](docs/zone-overview.png)
- [First-floor plans](docs/zone-interiors.png)
- [Architecture](docs/zone-architecture.png)
- [Production cable and cover meshes](docs/zone-utility-mesh.png)

Preview images use actual generation data and simplified diagnostic colors; they are **not Minecraft screenshots**. Historical 2.0.x and 2.1.0 documents describe those earlier releases; their upgrade instructions do not apply to this rebuild.

## Build and verify

```sh
./gradlew --no-daemon --max-workers=4 build
python tools/validate_resources.py
python tools/validate_faction_loadouts.py
python tools/validate_drone_resources.py
python tools/validate_combat_resources.py
python tools/test_vehicle_geometry.py
python tools/check_occupation_assets.py
```

With cached dependencies, use:

```sh
python tools/build_offline.py --gradle-cache /path/to/gradle-user-home
python tools/test_offline.py
sh tools/preview/run-authored.sh
```

The release passes **351 JUnit tests**, including occupied-hatch grenade entry, animation timing, native movement/collision checks and 102 chunk positions generated in two orders. [Validation record and limits](docs/VALIDATION_2_7_0.md).

The offline path compiles against real Minecraft/Fabric classes, checks mixin remapping and produces a loadable JAR. `AuthoredChunkTest` uses real in-memory Minecraft chunks, registered blocks and both generation orders. Test registration helpers are excluded from the release JAR. No graphical client playtest was performed.
