# Bobin Craft 2.4.0 — checkpoints, crew stations and combat repairs

This release contains the complete source, assets, tools, tests and Fabric production JAR. Black Alder keeps seed **19860426** and its **2048 × 2048** terrain. New worlds use **profile 10**. Install the same JAR on client and server. Start a fresh Bobin_craft world for the rebuilt checkpoint and trench geometry; replacing the JAR does not overwrite already generated chunks or reset defeated encounters.

## Checkpoints and trenches

Five checkpoint families retain different road obstructions, guard shelters, concrete fighting positions, flood-control fittings or cargo. Oversized tarp cubes are replaced by 1/8-block roof sheets using the existing rusted metal, olive tarp and blue tarp art. Supports use slender fence posts. Burlap sandbag stacks use the existing individually shaped, darker bag model, with a collider matching its 14/16-block height. Signs meet their supports; faction posts are shorter.

Checkpoint trenches now run 24–32 blocks. Placement checks their full footprint, dugout and entrance landings against buildings, fields, vehicle bays and blueprint bounds. It selects a clear flank instead of cutting through a house or shortening the excavation at the site boundary. Their three-block-wide passages have connected turns, graded floors, stair transitions and clear headroom. Retaining timbers remain outside the excavation, including at bends. Excavating a connection preserves earlier passages rather than adding a retaining wall across them. Lower dugouts have covered rooms and a connector into the trench.

## Occupants

Encounter activation checks the loaded neighborhood of each post and a nearby living visitor. A distant unloaded district corner no longer prevents all its NPCs from appearing. Authored residents use their own persistent roster independently of hub initialization. An explicitly rejected entity insertion can retry; an unloaded or defeated resident is never recreated merely because it is absent from memory.

Each of the eleven districts has its three original residents plus two checkpoint sentries in a new world. Short trodden connections join their posts to the lane; all duty points are checked for headroom and street connectivity. The extraction exclusion and creative/spectator targeting rules remain. `/bobin warzone encounters` reports active posts and issued/dead slots.

## Tank station and movement

Right-click an empty vehicle to occupy a seated station inside its hull. The first-person cabin has a forward slit, seat, instrument panel, ammunition rack and moving breech. External views retain the detailed weathered hull and articulated turret. The optical sight follows the cannon axis so the sight and shell no longer travel along offset parallel lines.

| Input | Action |
| --- | --- |
| WASD | Drive mobile armor |
| Mouse | Traverse/elevate the gun |
| V or left mouse | Fire the chambered round |
| R | Load the chamber; replenish ammunition from carried shells |
| C | Switch cabin / optical sight |
| Shift | Leave the vehicle |

The R binding is shared with the existing weapon-reload action, avoiding duplicate key bindings that would disable ordinary rifle reloading. Firing empties the chamber. Loading takes 60 ticks, moves the breech and shows a progress bar; NPC operators load automatically. Each supply item is one shell, and the rack holds 24 rounds.

New original mono Vorbis audio separates engine idle/load, track clatter, cannon report, shell impact and breech operation. The vehicle emits no walking step sounds.

Full-block ascent uses small collision-checked server movements, capped at 0.085 block upward per tick. Native step height is zero, so it cannot teleport the hull onto a higher block. A suspension pitch eases in during ascent. Two-block obstacles, insufficient overhead clearance, world borders and unloaded areas stop movement. This is a game collision model with a conservative rotated bounding box, rather than a physical track simulation.

Buried hulls sit 1.05 blocks below the surface instead of 1.90. The gun axis is about 1.11 blocks above level ground, with a limited downward angle that leaves the muzzle clear. Previously saved fixed vehicles migrate upward once; their saved identity, health and ammunition remain.

## Shells, impacts and injuries

Shells have a longitudinal octagonal 3D case and pointed cap aligned with velocity. They use swept block/entity collision, gravity, a bounded lifetime and loaded-chunk checks. Their faster, flatter gameplay trajectory is synchronized through a tracked vector, avoiding vanilla velocity-packet clamping. NPC elevation compensation uses the same speed and gravity.

Direct impact damage is 48. The separate HE pressure profile reaches 44 at the center, with cover, distance, armor and fragment calculations applied by the existing drone blast system. The impact adds a larger dust ring, fragment streaks, persistent smoke, voxel debris and a distinct heavy report. A bounded exposed-material crater removes up to 48 eligible blocks; protected blocks, block entities, distant material and unloaded chunks are excluded. Existing `bobinDroneTerrainDamage` controls these terrain edits. Solid armor shields seated occupants from external fragments and most pressure.

Players now enter the same severing path as guards. Limb state persists and is synchronized through native entity tracking, including late observers. Missing limbs, sleeves, trousers, armor and held items are hidden together; wound caps use the existing pixel-art style. A lost leg forces crawling and reduces movement; a missing hand cannot attack or use items. Treatment stops bleeding without regrowing limbs. Actual death/respawn creates a clean body. Creative and spectator immunity remains.

Drone control keeps the player as Minecraft's focused entity while moving the lens to the drone. This avoids the vanilla rule that skips the local player's body when a different entity becomes the camera. The operator remains at the original physical location and can be seen when in view.

## Inspection and limits

[Checkpoint geometry](zone-checkpoints.png), [tank exterior](zone-armor-mesh.png) and [seated cabin](zone-armor-station.png) are diagnostic renders with simplified colors. They use native generated geometry or captured production renderer vertices; they are not Minecraft screenshots.

[Validation record](VALIDATION_2_4_0.md) distinguishes native movement/collision tests, chunk generation, resource checks and mixin-target auditing from a graphical playtest. A live Minecraft client, multiplayer session, graphics-driver/shader compatibility and full live encounter AI have not been playtested here.
