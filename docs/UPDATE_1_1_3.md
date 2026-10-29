# Forest camp props — 1.1.3

For Minecraft 1.20.1, Fabric Loader 0.15.11 or newer, and Fabric API 0.92.2+1.20.1.

## Install

Replace the previous Bobin Craft JAR in the instance's `mods` folder with `bobin_craft-1.1.3.jar`. Keep Fabric API installed. Use the same Bobin Craft version on the client and server.

New managed hubs generate the revised layout automatically. In an existing design-4 forest hub, an operator can run:

```text
/bobin hub repair
```

This upgrades the supplied camp layout without rebuilding the world or moving inventories. It supports both the previous repaired layout and its original 1.1.1 expectations. Connected edits preflight together. Groups containing player changes or persistent block data are preserved and counted in the command result. The command can be run again safely; already-correct blocks are left alone. Older bunker layouts remain unsupported by this forest-camp repair.

## Changes

- Replaced vanilla lantern assemblies and added support cubes with standalone field lamps and wall-mounted cage lamps. Both produce real level-15 light.
- Raised service shelters and camouflage-net canopies to roof Y=76, with clear standing room beneath their frames.
- Rebuilt dining tables, work counters and benches at sensible heights, and cleared the kitchen crossing.
- Removed freestanding signboard pillars and storage signposts. Service labels now mount directly on existing walls.
- Removed the ordinary-block vehicle. The hub now contains a detailed cargo truck and a wrecked tank; a Humvee model is also registered for authored generation.
- Vehicles have 256-pixel material textures and round wheels, tracks, grilles, windscreens, doors, handles and fittings. Each model is split into reserved block cells with matching local collision in all four orientations. Placement checks the complete footprint before changing it. Removing a vehicle cell cleans up its matching companions.
- Equipment cabinets are paired lower/upper blocks, with a single continuous two-block design, two occupied collision cells and normal placement checks.
- Added assault-rifle, sniper-rifle and SMG weapon racks. Stocks, receivers, magazines, barrels, sights, scopes and suppressors are modeled separately, using 128-pixel materials.

## Validation and previews

Generated resources are reproducible with `tools/build_forest_camp_art.py` and `tools/generate_military_vehicles.py`. `tools/validate_resources.py` validates references, loot IDs, translations and generated assets. Structural smoke checks cover clearances, routes, both cabinet cells, lighting and every vehicle orientation. The server smoke harness also contains real-world cabinet placement and drop checks.

The supplied images are offline previews of the actual models and textures. They are not in-game screenshots; lighting and some vanilla block entities are approximations. See the accompanying validation report for checks executed for this release.
