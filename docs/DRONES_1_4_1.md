# Mouse flight and bomber hover — 1.4.1

**Historical release guide.** Version 1.4.2 changes lift to Space/Shift, bomber
release to F, adds unlinking, and replaces the payload separation gate. Use
the [current 1.4.2 guide](DRONES_1_4_2.md) for current controls and gameplay.

Version 1.4.1 makes drone flight easier to control and repairs bomber payload
release. Install `bobin_craft-1.4.1.jar` on every client and the server, with
Minecraft 1.20.1, Java 17, Fabric Loader 0.15.11 or newer and matching Fabric API.
Existing worlds, drone kits, controllers and stored drone state remain usable;
no new world is required.

## Start flying

Use an FPV or bomber kit outside the safe camp, after dismounting and choosing
clear launch space. The live camera opens with the motors stopped. Press **R**
to start them, then hold **E** or **Up arrow** to lift off. Move the mouse to
turn and look around. Use **WASD** to move and release the movement keys to
brake and hold position and height.

In Survival, deployment replaces the kit with a reusable Drone Controller.
Use it to reconnect to your deployed aircraft after exiting its camera. Using
another drone kit also reconnects to your existing aircraft instead of
launching a second one. Creative mode keeps the kit. The operator's body stays
behind and remains vulnerable.

## Default controls

Both drones begin in assisted flight. The bomber keeps its airframe level while
the mouse aims its camera. The FPV moves in the camera's direction when moving
forward, so looking up or down changes its flight path. Releasing movement
keys brakes the aircraft and captures a position and height to hold. Turning
the camera alone does not command forward movement.

| Control | Action |
| --- | --- |
| Mouse | Turn and look |
| W / S | Move forward / back |
| A / D | Strafe left / right |
| E or Up arrow | Climb while held |
| Q or Down arrow | Descend while held |
| Release movement keys | Brake, then hold position and height |
| R | Start / stop motors |
| C | Bomber: switch forward / downward camera |
| Space | Bomber: release one bomb |
| M | FPV: switch assisted / advanced flight |
| H | Show / hide controls |
| Esc | Return to the operator and stop the motors |

Climb and descend are held commands in assisted mode. Releasing them stops the
climb or descent and holds height; there is no manual throttle setting to
balance on the bomber. Stopping the motors with R or exiting with Esc ends
hover support and lets the aircraft descend.

The dedicated flight screen captures its controls. The usual gun, field-map
and top-down-camera bindings do not interrupt piloting. The HUD shows battery,
signal, motor output, speed, ground clearance, range, flight mode and payloads.
Weak signal produces interference in the live Minecraft world view. The FPV
view banks with the airframe; the bomber also has a downward camera.

## Bomber release repair

The bomber carries three charges. Climb to more than **5.5 game blocks** above
the surface, keep the payload bay clear, and press Space once to release a bomb.
A low or obstructed release shows a warning and spends neither a charge nor
the release cooldown. Holding Space does not repeat releases.

The released payload starts outside the carrier and stays inert until it has
separated by five game blocks and existed for at least six ticks. This removes
the immediate release explosion that could destroy a low-flying bomber.
The aircraft still takes ordinary damage; a later armed blast can damage it.
Payloads follow game gravity and inherit part of the aircraft's movement.
Their explosions damage entities, preserve blocks and create no fire.

## Optional advanced FPV

Press M in the FPV view to enter advanced acro flight. **W/S** controls pitch,
**A/D** controls roll, and the **mouse** turns the aircraft. **E/Q** or
**Up/Down arrows** adjust a persistent throttle setting. Releasing those keys
holds the throttle value. Pitch and roll retain their chosen attitude and
require active correction. Reduce throttle before starting the motors in this
mode. Press M again to return to assisted flight.

The bomber always uses assisted hover flight and does not switch to acro mode.

## Aircraft, crafting and recovery

| Item | Payload | Nominal battery budget | Maximum link range |
| --- | --- | --- | --- |
| FPV Kamikaze Drone | One armed impact payload | Up to 3 minutes | 112 blocks |
| Bomber Drone | Three individually released bombs | Up to 4 minutes | 96 blocks |

Battery use varies with motor output. Solid obstructions weaken the signal;
the server's view distance can shorten usable range. Drones do not load distant
chunks or automatically fly home. A lost link, empty battery or damage to the
operator ends control and stops the motors. Hard collisions, water, lava and
incoming damage can destroy an aircraft. Unarmed FPV contact does not detonate
its impact payload.

Recipes are unchanged. Each grid produces one item; a dot is an empty slot.
`I` is iron ingot, `R` redstone, `C` copper ingot, `Q` quartz, `G` gunpowder.

```text
FPV             Bomber          Controller
I R I           I R I           C R C
C Q C           I Q I           I Q I
. G .           G G G           . I .
```

The items are also in the Combat creative tab. To recover your aircraft, land,
stop the motors and right-click it. Remaining battery and charges are stored
on the recovered kit; recovery does not recharge it or replace spent bombs.
If the inventory is full, the item drops beside you. An unattended drone
despawns after one minute. Death, logout or leaving its dimension removes the
deployed aircraft.

## Resource checks

The existing item and entity IDs and original 32×32 icons are retained. New
mouse-control, hover and release-warning text is translated in English,
Russian and Ukrainian. Run:

```sh
python tools/generate_drone_assets.py --check
python tools/validate_drone_resources.py
```

The targeted validator checks the registered drone items, models, textures,
recipes, actual Java translation references and duplicate JSON keys. The
full-project resource validator still encounters 14 unregistered legacy loot
IDs already present in the supplied archive; the drone update does not change
or allowlist them. Automated checks do not establish Minecraft client handling,
camera comfort or multiplayer balance; those need a client play session.
