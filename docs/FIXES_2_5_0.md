# Bobin Craft 2.5.0

This update retains seed 19860426, the 2048-block arena and generation profile 10. Replace the previous JAR on both client and server. A profile-10 world does not need regeneration for these fixes.

| Report | Change |
| --- | --- |
| Roof tiles and repeated markings | Native multipart materials and neighbor-resolved render states for checkpoint roofs, shingles, tarps and containers. Interior frames disappear, exposed boundaries retain trim, and stencils appear only at a face anchor. Old states and chunk boundaries are supported. |
| Moving severed limb | Missing bones and their cut-surface anchors reset their rotations and pivots. Dead guard models stop idle/weapon/crawl motion, and corpses stop horizontal locomotion. |
| Stiff crawling | Shared alternating shoulder pulls, restrained torso movement, surviving-leg drag and an inactive rest pose. Explicit player transforms replace the upright rendering caused by a forced collision pose without vanilla's swimming blend. |
| Drone hit ejects crew; NPCs crowd the tank | Capable crew remain inside. One expiring seat reservation selects one approaching guard. Ground-supported boarding stations are outside the rotated hull; unreachable approaches release the claim. Injured or incapacitated guards still seek cover. |
| Bad climbing and appearance; C sight | Track-contact solver with pitched/banked hull sections, 0.055-block maximum ascent per tick, limited angular change and client interpolation. Fixed UV areas on every face, weathered material variants, field stowage and moving tread links. Corrected Bobin camera handoff; C selects first-person optics, including from an exterior perspective. Hints wrap and fade. |
| No material impact debris | Bullet impacts produce outward material fragments; shell breaches eject a bounded set of larger fragments. Particles use block sprites, lighting, gravity and ground collision. NPC shots broadcast impacts as well. |
| Weak building/trench blast response | Pressure snapshots the exposed breach and can follow through that opening at reduced strength. Up to 96 reachable glass blocks within 12 blocks may shatter. Intact cover remains effective. Exposed players receive bounded stagger, screen fade, camera shake and ear ringing, with reduced exposure inside armor. |

Tank controls: right-click to board an empty vehicle, WASD to drive, mouse to aim, V / left mouse to fire, R to load, C for optics and Shift to leave. Control bindings remain configurable. Ammunition, reload timing and buried emplacement height remain compatible with 2.4.0.

Terrain changes honor `bobinDroneTerrainDamage`, the world border, loaded chunks, player modification permissions, protected hardness and block-entity exclusions. The pressure calculation uses game-scale collision traces; it is not a real fluid/structural simulation. It does not collapse an entire building or destroy distant rooms through intact walls.

Validation uses Minecraft collision shapes and production model methods in headless fixtures. No graphical Minecraft client or live multiplayer encounter was run. Camera feel, sound balance, continuous NPC navigation and GPU presentation require in-game acceptance testing.
