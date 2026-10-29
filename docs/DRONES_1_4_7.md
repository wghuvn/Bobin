# Stretched-X FPV airframe, larger scale and dynamic ground shadow — 1.4.7

Install `bobin_craft-1.4.7.jar` on every client and the server, with Minecraft
1.20.1, Java 17, Fabric Loader 0.15.11 or newer and matching Fabric API. Existing
worlds, kits and controllers remain usable; no new world is required.

## Elongated FPV airframe and 0.5-block clearance

- **FPV Drone (`bobin_craft:fpv_drone`)**:
  - Physical collision box is **0.48 × 0.28 blocks** (strictly under `0.5 × 0.5` blocks), preserving full clearance through 0.5-block-wide and 0.5-block-high tunnels and open single-block doorways.
  - Visual render scale is increased from `0.30` to **`0.42`** (+40%), with an elongated stretched-X wheelbase (`0.54` blocks overall length, `0.46` blocks width across spinning rotors, `0.25` blocks height).
  - The 3D model is rebuilt as an authentic FPV kamikaze quadcopter: twin-deck carbon-fiber sandwich plates, central flight-controller/ESC stack, four angled diagonal X-frame carbon arms (`arm0..3`), top-mounted LiPo battery pack with dual Velcro straps, front camera cage with cyan optical lens, rear-tilted VTX lollipop antenna, low-profile under-motor pads, and an under-slung RPG-style shaped-charge warhead with conical nose and protruding front contact-fuse striker probe.
- **Bomber Drone (`bobin_craft:bomber_drone`)**:
  - Retains its `1.75 × 0.85` block collision box while increasing render scale from `1.24` to **`1.36`** with an elongated X-frame, rear-angled VTX antenna, tall twin landing skids, and three visible under-slung timed bomb canisters.

## Dynamic drone-shaped ground shadow

- The static vanilla circular blob shadow (`shadowRadius = 0.45f`) is disabled (`shadowRadius = 0.0f`).
- `DroneRenderer` now projects a **dynamic drone-shaped silhouette shadow** directly onto the supporting block/slab/stair collision surface up to 8 blocks below the aircraft:
  - Renders single-sided horizontal quads for the elongated fuselage, four diagonal X-frame arms, motor pods, and four spinning rotor blades.
  - Rotates with aircraft `yaw`, foreshortens dynamically with `pitch` and `bankAngle`, spins its rotor blades in sync with motor throttle, and scales and fades smoothly with altitude above the surface.
