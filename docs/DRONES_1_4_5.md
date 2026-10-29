# Drone payload separation and low drops — 1.4.5

Install `bobin_craft-1.4.5.jar` on every client and the server, with Minecraft
1.20.1, Java 17, Fabric Loader 0.15.11 or newer and matching Fabric API. Existing
worlds, kits and controllers remain usable; no new world is required.

## Start flying

Leave the safe camp, dismount and choose clear launch space. Use a drone kit to
open the live camera. Press **R** to start the motors, then hold **Space** to
climb. Move the mouse to turn and look around; **WASD** moves the aircraft.
Release movement keys to brake and hold position and height.

The bomber stays level while its camera looks around. The FPV follows the
camera's direction when moving forward, including looking up or down. Turning
the camera alone does not command movement. Both drones start in assisted
flight; the FPV also has an optional advanced acro mode.

In Survival, deployment replaces the kit with a reusable Drone Controller.
Use it to reconnect after leaving the aircraft's camera. Using another kit
reconnects to your linked aircraft instead of launching a second one. Creative
mode keeps the kit. The operator's body stays behind and remains vulnerable.

## Controls

| Control | Action |
| --- | --- |
| Mouse | Turn and look |
| W / S | Move forward / back |
| A / D | Strafe left / right |
| Space | Climb while held |
| Either Shift | Descend while held |
| Release movement keys | Brake, then hold position and height |
| R | Start / stop motors |
| C | Bomber: switch forward / downward camera |
| Left click | Bomber: release one bomb |
| M | FPV: switch assisted / advanced flight |
| U | Unlink this drone and start a gentle landing |
| Hold Alt | Free the pointer to click the visible unlink button |
| H | Show / hide controls |
| Esc | Return to the operator and keep the current aircraft linked |

Space and Shift are held climb/descent commands in assisted mode. Releasing
them holds height; the bomber needs no manual throttle balancing. The old
arrow and E/Q flight bindings are removed. Stopping the motors with R or
exiting with Esc ends hover support and lets the aircraft descend.

The flight screen captures ordinary gun, map and top-down-camera bindings.
Its HUD shows battery, signal, motor output, speed, ground clearance, range,
flight mode and payloads. Weak signal adds interference to the live world
view. The FPV view banks with its airframe; the bomber also has a downward view.

## Unlink and land

Press **U**, or hold Alt and click **Unlink and land**, to free the controller
for a new drone. The previous aircraft disconnects, disarms its payload, stops
accepting flight commands and makes a gentle descent. It remains in the world for recovery;
unlinking does not return the aircraft item to your inventory or recharge it.

Use a new kit to deploy another aircraft. To recover the old one, wait until
it has landed and interact with it. Remaining battery and payloads stay on the
recovered kit. **Esc** is the ordinary camera exit: it preserves the link so
the controller can reconnect to the same aircraft.

## FPV size and contact payload

The FPV's model and collision box are about **22% smaller** than in 1.4.1. The
rendering and physical footprint are reduced together.

Starting its motors while it rests on the ground does not trigger its payload.
After takeoff, armed physical contact with an obstacle or another entity
triggers the impact payload at any speed; a slow approach is no longer ignored.
To recover an FPV, stop its motors before it touches the ground. Unarmed
contact does not trigger the payload. Explosions damage entities while
preserving blocks and creating no fire.

## Bomber payload release

The bomber carries three charges. Left click releases one bomb with **no minimum
altitude**. The whole charge is positioned below the actual carrier collision
box with a small gap. The payload bay must still be physically clear: a release
inside a solid block is rejected without spending a charge. Holding the mouse
button does not repeat releases. Space only climbs; Alt reserves clicks for UI.

A released charge is live immediately and explodes on its first real impact
with terrain, water or another target, even at low height. There is no delayed
arming or minimum-travel gate that can turn a low release into a silent dud.

Until a charge first clears its carrier by about 5.1 blocks, it ignores that
carrier as an impact target and its own explosion does not damage that carrier.
This protection applies only to the original carrier of that charge. Other
aircraft, the operator and other targets remain vulnerable. Once separation
has occurred, normal damage remains active even if the carrier approaches again.

Dropped payloads remain server controlled. Cleanup after owner death, logout,
dimension changes, safe-camp entry or world reload does not create an explosion.

## Optional advanced FPV

Press M to enter advanced acro mode. **W/S** controls pitch, **A/D** controls
roll, and the **mouse** turns the aircraft. **Space/Shift** raises/lowers a
persistent throttle setting; releasing those keys keeps the chosen throttle.
Pitch and roll retain their attitude and need active correction. Reduce
throttle before starting the motors in this mode. Press M again for assisted
flight. The bomber always uses assisted flight.

## Crafting, link limits and recovery

| Item | Payload | Nominal battery budget | Maximum link range |
| --- | --- | --- | --- |
| FPV Kamikaze Drone | One contact payload | Up to 3 minutes | 112 blocks |
| Bomber Drone | Three individually released bombs | Up to 4 minutes | 96 blocks |

Motor output affects battery use. Obstructions weaken the signal; server view
distance may shorten range. Drones do not load distant chunks or fly home
automatically. A lost link, empty battery or damage to the operator ends remote
control. Water, lava, hard collisions and incoming damage can destroy aircraft.

Recipes are unchanged. Each grid produces one item; a dot is an empty slot.
`I` iron ingot, `R` redstone, `C` copper ingot, `Q` quartz, `G` gunpowder:

```text
FPV             Bomber          Controller
I R I           I R I           C R C
C Q C           I Q I           I Q I
. G .           G G G           . I .
```

The items are also in the Combat creative tab. Recover your landed aircraft
with its motors stopped by right-clicking it. If your inventory is full, the
item drops beside you. Recovery preserves remaining battery and charges;
it does not refill either. An unlinked drone's one-minute pickup timer starts
after landing. Recover promptly. Death, logout or leaving their dimension
removes deployed aircraft.

## Resource checks

The existing item and entity IDs, recipes and original 32×32 icons are retained.
Controls, warnings and the unlink button are translated in English, Russian
and Ukrainian. Run:

```sh
python tools/generate_drone_assets.py --check
python tools/validate_drone_resources.py
```

The targeted validator checks registrations, models, textures, recipes, actual
Java translation references and duplicate JSON keys. The full-project resource
validator still encounters 14 unregistered legacy loot IDs already in the
supplied archive; the drone update does not change or allowlist them. Automated
checks do not establish client handling, camera comfort or multiplayer balance;
those require a Minecraft client play session.

## Repairs after the recorded playtest

The FPV contact fuse now uses Minecraft's verified block-collision flags. Small
hover corrections that vanilla deliberately omits from position updates no
longer count as an obstacle hit. Physical wall/floor contacts and swept entity
contacts retain their previous behavior.

Opening another screen temporarily no longer ends a healthy drone session.
The receiver sends neutral movement while that screen is open, then restores
the flight screen when it closes. Explicit Esc exits, U unlinks and lands, and
server-side link loss, death and dimension changes still end control.

The old launch-space error is cleared when a camera session successfully starts.
The bomber confirms an accepted left-click release with the remaining charge count.
Flight-session and payload diagnostics help distinguish a rejected drop,
physical damage, link loss and intentional camera exit in the game log.

The F key no longer releases bombs. Left click is consumed by the flight screen;
Alt + left click remains reserved for widgets and cannot release a payload.
The release still requires armed motors, a clear payload bay and remaining charges.
