# Bobin Craft 2.3.0 — occupied roads and underground cover

This release includes the complete source, generated assets, build tools, tests and current production JAR. The fixed Black Alder map remains seed 19860426, 2048 × 2048 blocks. New worlds use generation profile 9; old profile layouts remain readable for saved games. Existing chunks and saved encounter rosters are not reset by replacing the JAR. Create a new world for this complete revision.

## Repairs

- A thin continuous outer wall closes the manhole road block's side slit. The aperture remains round, the cover stays flush, and its existing 16-tick sliding animation is retained. No raised rim is added.
- The ladder backing is placed opposite the collector exit. Low shaft walls leave a three-block landing open instead of sealing the exit. All eleven shaft-to-collector routes pass native collision clearance checks.
- Well masonry now contacts the ground and roof posts. Campfires sit on floors. Composter trapdoors touch the composter rim. Industrial rack beams touch their columns and connected pipes; transformer radiators have footings.
- Vehicle turnout ground uses coherent dark wear with an irregular footprint. Water towers have smaller tapered tanks, rounded crowns, service decks, braces and backed ladders. Flag models have no extruded edge faces or repeated vertical texture.

## Occupied checkpoints and armor

The five families are a concrete chicane, timber road barrier, concrete gate bunker, flood-control post and cargo inspection post. Their obstacles restrict straight vehicle travel while retaining foot access. Faction markers, radios, supplies, roofs, posts and asymmetric firing cover use the existing aged palette. Where surrounding buildings or fields permit, checkpoints have graded timber-lined trenches and stocked dugouts.

Armored entities are issued at district IDs 2, 4, 5, 6, 8, 10 and 11. IDs 4 and 10 receive mobile armor; IDs 2, 5, 6, 8 and 11 receive buried emplacements. Their hulls sit 1.90 blocks below the local surface in the fixed configuration. Their exposed turrets, viewing ports and cannon use the same model and weathered materials as the mobile version.

Right-click an empty vehicle to board. WASD drives, mouse movement aims, V fires, and Shift dismounts. Fixed vehicles ignore movement input. The cannon has 24 rounds and a 60-tick reload; a cannon supply adds four rounds. Supply and placement items appear in the Combat creative inventory. Matching faction guards can approach, board and operate generated vehicles. Mobile patrol operators steer toward their assigned lane waypoints; fixed operators hold their emplacement. Occupied vehicles intercept NPC bullets, and hostile occupants make their vehicle a valid armor target. NPC cannon shots recheck sight lines and nearby allied occupants before firing.

Movement and cannon input are validated on the server against the current passenger. The server applies gravity, collision, steering limits, turret slew, reloads and ammunition. Input expires when packets stop. Shells use swept entity and block hits, a limited lifetime, loaded-chunk checks and a terrain-preserving explosion. Projectiles loaded from disk are inert. Dismount checks nearby supported, dry positions. Issued UUIDs persist separately from loaded entities, preventing duplicate vehicle spawns when a chunk unloads.

## Drones and injuries

Netting has three orientations and a real thin-sheet collider. The transparent weave openings do not permit a drone through. Armed FPV and bomber drones detonate on net contact. Utility conductors use continuous swept box-versus-segment contact rather than collision with the invisible cable anchor block. Stationary contact, fast contact and diagonal near misses are covered by regression checks.

Guards notice visible drones or close armed drone noise without gaining a firing solution through a wall. They engage visible threats with existing finite firearm ammunition, move toward cover for close approaches and alert nearby allies. Healthy mounted operators retain their armored position. Wounded guards try reachable, loaded cover within their territory; explicit trench, shelter and dugout candidates supplement local geometry checks. Torso and arm wounds lead to a walking retreat; damaging leg hits can synchronize a prone pose and reduced movement without removing the visible leg. Existing missing-limb movement and weapon restrictions remain. Temporary leg mobility lasts up to 1200 ticks and waits for standing clearance before recovery; this does not heal health or bleeding.

## Photographed clearings

| Center X, Z | Added destination |
| --- | --- |
| -900, -672 | Isolated ridge relay, relay mast, timber-lined trench, overhead net and underground communications dugout |
| -132, -830 | Separate trench supply position, dugout, ammunition stacks and a field work area |

The two locations retain the natural ridge heights. Foundations extend down to ground where needed, and excavated floors are graded rather than suspended over dips. The general discovery survey reserves space around these locations, preserving separation from the other 24 discoveries. The map now has 26 discovery destinations.

## Verification limits

The Java 17 production JAR, native collision shapes, generated chunks, resource manifests, model geometry, state bounds and save data were checked automatically. Diagnostic images use production geometry or blueprint data with simplified colors. No graphical Minecraft client, dedicated multiplayer session or shader-specific visual playtest was performed. Live vehicle handling and NPC encounters still need an in-game acceptance pass.
