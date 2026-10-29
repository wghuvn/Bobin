# FPV and bomber drones — 1.4.0

**Historical release guide.** The controls below describe 1.4.0. The default
controls and bomber release behavior have since changed. Use the
[current 1.4.2 guide](DRONES_1_4_2.md) for current controls and gameplay.

This update adds two playable remote aircraft to Bobin Craft for Minecraft
1.20.1. Drones are available in existing worlds, including older Warzone
generation profiles. Use the same 1.4.0 mod on the client and server.

## Aircraft

| Game item | Flight and camera | Payload | Nominal flight budget | Link range |
| --- | --- | --- | --- | --- |
| FPV Kamikaze Drone | Agile quadcopter; forward view follows pitch and roll | One impact payload; the aircraft is lost on an armed impact | Up to 3 minutes | 112 blocks from operator |
| Bomber Drone | Slower, self-leveling quadcopter; forward or downward camera | Three individually released bombs | Up to 4 minutes | 96 blocks from operator |

These are game balance values. Flight time depends on motor use. The server's
view distance may further shorten the effective link range. The HUD shows
remaining battery, radio link, throttle, speed, altitude, range, armed state,
flight mode and remaining payloads. The aircraft has a visible quadcopter model
with spinning propellers and motor audio that changes with throttle. Forward
and downward views are live Minecraft world views.

Armed FPV impacts and dropped bombs cause area damage to entities. Drone
explosions preserve blocks and do not create fire. Unarmed contact does not
trigger the FPV impact payload. Drones can be damaged and destroyed by gameplay
hazards, so the aircraft and unused payloads are not guaranteed to return.

## Obtain and launch

Find the drone kits and controller in Bobin Craft's creative inventory or craft
them with vanilla ingredients. Each recipe produces one item.

FPV recipe (`I` iron ingot, `R` redstone, `C` copper ingot, `Q` quartz, `G`
gunpowder; `.` is an empty slot):

```text
I R I
C Q C
. G .
```

Bomber recipe (`I` iron ingot, `R` redstone, `Q` quartz, `G` gunpowder):

```text
I R I
I Q I
G G G
```

Controller recipe (`C` copper ingot, `R` redstone, `I` iron ingot, `Q` quartz):

```text
C R C
I Q I
. I .
```

Leave the safe camp, dismount and choose clear launch space. Use the drone item
to deploy and open its video link. In Survival, a successful launch consumes
the inventory item and replaces it with a reusable Drone Controller; the
deployed aircraft can later be recovered. Creative mode retains the kit. Use
the controller to reconnect after leaving the aircraft's camera. Using another
drone kit also reconnects to an existing owned drone instead of deploying a
second one. A player can control only their own active drone.

The aircraft starts disarmed at zero throttle. The camera transfers to the
aircraft while the player's body stays behind. The body remains vulnerable;
remote piloting provides no invulnerability or teleportation.

## Flight controls

The dedicated flight screen captures its controls while open. The normal field
map, gun controls and top-down view do not interrupt drone flight.

| Key | In-game action |
| --- | --- |
| W / S | Nose down / nose up (pitch) |
| A / D | Bank left / bank right (roll) |
| Left / Right arrows | Turn left / turn right (yaw) |
| Up / Down arrows | Increase / decrease throttle |
| R | Arm or disarm; reduce throttle before arming |
| M | FPV: switch self-level / acro flight mode |
| C | Bomber: switch forward / downward camera |
| Space | Bomber: release one bomb |
| H | Show or hide the control reference |
| Esc | End control and return the camera to the operator |

Throttle is a persistent setting, rather than a held movement key. Releasing
the throttle keys holds the current value. Pitch and bank change the direction
of motor thrust; the drone has momentum and loses height when tilted without
enough lift. In self-level mode, releasing pitch and roll brings the drone back
toward level. FPV acro mode retains the chosen attitude and requires active
correction; the bomber always uses self-level flight. The FPV camera rolls with
the airframe, including the horizon.

The bomber's downward view helps position drops. Payloads fall under game
gravity with their inherited aircraft motion. Each press of Space releases one
bomb while the bomber is armed and airborne. The three charges are finite;
holding the key does not trigger repeated drops.

## Video link, landing and recovery

The camera overlay adds an aircraft frame, reticle, horizon reference and
telemetry. Weak links produce visible interference and warnings. Distance and
solid obstructions affect the signal. The link is local to the operator and
does not provide unlimited remote scouting or load distant chunks.

Exiting control disarms the motors and lets the drone descend. A lost link or
expired battery also ends remote control. The return to the operator is a
camera change; the aircraft does not teleport back or automatically fly home.

To recover an owned drone, land, disarm and right-click the aircraft. Recovery
preserves its remaining battery and bomber payload count. Picking it up and
relaunching does not recharge or rearm it. If the inventory is full, the recovered
item drops beside the owner. Recover promptly: an unattended drone despawns
after one minute. Leaving the world or dying removes the deployed aircraft.
Another player cannot take over the live control session.

## Assets and verification

The new registry IDs are `bobin_craft:fpv_drone`, `bobin_craft:bomber_drone`,
`bobin_craft:drone_controller` and the entity-only `bobin_craft:drone_payload`.
Item icons use original 32×32 pixel art. A neutral 32×32 material atlas supports
the native renderer's separately
colored frame, camera and payload components. All flight-screen text and drone
item text is localized in English, Russian and Ukrainian.

Regenerate or verify deterministic assets with:

```sh
python tools/generate_drone_assets.py
python tools/generate_drone_assets.py --check
python tools/validate_drone_resources.py
```

The targeted validator passes: the three registered items have valid models,
icons, crafting recipes and entity material references. All 41 Java translation
references and six registry names resolve in English, Russian and Ukrainian;
the checked JSON contains no duplicate keys. The existing full-project
`validate_resources.py` check remains blocked by 14 unregistered legacy loot
item IDs in the supplied archive. The drone update does not change or
allowlist those legacy items.

The guide describes implemented game behavior. A Minecraft client play session
is still required to assess camera comfort, perceived handling and multiplayer
balance on the target server.
