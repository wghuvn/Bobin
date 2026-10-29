# Forest camp assets

The rebuilt Hub uses 24 original 64 × 64 RGBA textures and 15 registered block
types. The palette follows the supplied reference: muted olive canvas, weathered
timber, charcoal-green equipment, tan burlap, gray slate and warm brass details.
All pixels and static model geometry are authored in
`tools/build_forest_camp_art.py`, using Python's standard library. No external
texture pack, extracted artwork or generated photograph is an input.

## Registered blocks

| Material or prop | Resource IDs |
| --- | --- |
| Canvas and timber | `tent_fabric`, `reinforced_wood`, `palisade` |
| Tower roof | `roof_shingles` |
| Netting and defenses | `camouflage_net`, `sand_bags`, `hub_barbed_wire` |
| Storage scenery | `supply_crate`, `ammo_crate`, `equipment_locker` |
| Service props | `map_table`, `weapon_rack`, `radio`, `warning_sign` |
| Wing-emblem cloth | `outpost_banner` |

Every block has a block model, item model, blockstate and self-drop loot table.
Directional equipment, signs, banners and wire use the horizontal `facing`
property; their models face north before blockstate rotation. The hanging banner
extends down into the block below its mounting position. Wire and banner cloth
have no collision. Camouflage netting, banners and wire use the client cutout
render layer. Furniture outline shapes rotate with their blockstates.

Supply crates, ammunition crates, lockers and weapon racks are scenery.
Persistent player items use vanilla chests/barrels; the planning table opens the
existing raid interface. No ticking block entity was introduced for decorative
assets.

## Reproduce and verify

From the project root:

```text
python tools/build_forest_camp_art.py
python tools/build_forest_camp_art.py --check
python tools/build_forest_camp_art.py --check --contact-sheet contact-sheet.png
python tools/validate_resources.py
```

`--check` compares all 24 PNGs and 69 model/blockstate/item/loot files against
their deterministic authored output. The broader resource validator checks JSON,
custom resource references, registered loot IDs, translations and forest assets.
The contact sheet includes a separate text legend identifying each cell.

The former `generate_hub_textures.py`, `generate_outpost_assets.py` and
`generate_zone_assets.py` pipelines were removed. Obsolete bunker materials,
industrial container/scavenger variants and their exclusively owned models,
textures, blockstates, loot and translations were deleted. All retained Hub
texture filenames were replaced with newly authored 64 px artwork. Shared
Warzone forest, wildlife and faction art remains part of those systems.
The three removed registered block IDs (`metal_floor`, `bunker_wall`,
`equipment_panel`) are unsupported in older saved camps. The rebuild requires a
fresh Warzone world; its initialization gate does not migrate legacy blocks.

## Inspecting the block plan

`tools/preview/render_hub_plan.py` renders the actual exported Hub block plan
using the new resource models and textures, plus a user-supplied local Minecraft
client/runtime jar for vanilla materials. It generates an isometric preview,
north-up overview and roof-removal inspection view. These are explicitly labeled
**Block-plan preview**. They are offline geometry checks with approximate
lighting, fluids, plant tint and block entities; they are not in-game screenshots.
NPCs are not shown.

## 1.1.2 geometry corrections

Net sheets sit on a continuous timber frame; full-height sandbag cores meet
vertically. Tower columns remain continuous through the railing level, and
stepped roofs have filled risers. The planning table uses nine ordered tiles
with continuous UVs from a single unfolded map; all tiles open the same raid UI.
Native glowing, waxed wall signs identify the six camp zones and storage.
