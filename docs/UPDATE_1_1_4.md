# Camp personnel and deserter strike — 1.1.4

## Install and existing saves

Replace the old Bobin Craft JAR with `bobin_craft-1.1.4.jar` on both client and server. Minecraft 1.20.1, Java 17, Fabric Loader 0.15.11 or newer and Fabric API remain required.

New hubs contain no generated signs. Existing design-4 hubs automatically remove the 15 wall labels from 1.1.3 at server start. Missing authored wall or gate cells behind those labels are restored. Player replacements and storage are retained. Older design-4 layouts can also use `/bobin hub repair`; its conditional expectations support 1.1.1–1.1.3. The full camp is never rebuilt during this upgrade.

Existing personnel retain their UUIDs, inventories, trade offers and used stock. Their synchronized role names and audio flags are refreshed when the world starts. Ordinary villagers and hostile faction guards retain their usual rendering and behavior.

## Deserter

Leaving the camp's wall footprint without a successful map deployment starts a red **Deserter — 10 seconds** boss bar and a spoken warning. The footprint covers the full wall columns from X/Z -46 through 46. Falling below Y=68 also counts as departure. Returning inside cancels the warning and falling bomb.

A completed deployment from the map authorizes travel to the Warzone. Holding a map, opening it, or changing dimensions by another means does not authorize departure. Creative and spectator players are exempt.

The final 1.5 seconds show a modeled falling bomb, smoke trail and custom whistle. At ten seconds, the strike produces a flash, fire, smoke, an expanding shockwave and a custom explosion sound. It applies **1,000 damage** to the offending player, bypassing armor, resistance, enchantments, shields and normal damage immunity. The spectacle does not destroy terrain or damage bystanders or NPCs. Saved deadlines survive reconnects and server restarts.

## Personnel and dialogue

Right-click sentries or service NPCs in ordinary view. In Bobin view, right-click them to approach and interact. Conversations include status, camp rules, role-specific duties and goodbye. Service personnel also offer an action to open their department interface.

The quartermaster, cook, medic and commander use distinct field requisition screens with department colors and emblems, briefing text, offers, costs, remaining stock and inventory slots. These screens use Minecraft's actual merchant handler: buying, selling, shift-clicking, prices and stock are validated by the server. Mouse wheel and arrow keys navigate longer offer lists.

Five original 256-pixel human skins distinguish sentries, quartermasters, cooks, medics and commanders. Twenty original English spoken cues use role-specific radio processing and Foley. Three additional sounds provide the deserter warning, falling-bomb whistle and explosion. Text and subtitles are localized in English, Ukrainian and Russian. Voice cooldowns prevent rapid interactions from layering speech.

## Verification

Automated tests cover the exact deadline, cancellation, persisted countdown, authorized travel, model parsing, damage configuration, conversation sessions, access range, expiry, request throttling, saved raid data and conditional hub repairs. The structural checks verify that signs are gone and gate/wall backing cells remain complete. The production JAR passes the mixin remapping audit for both service-screen and custom trade-sound hooks.

The skin image and sound montage are asset previews. A live Minecraft client/server session was not launched during this update; in-game screen presentation, bomb animation and audio balance still need a live play check.
