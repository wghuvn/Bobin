# Vehicle texture and body repairs

The reported truck artifacts came from the production assets: every 16-unit
reserved block cell restarted the UV projection, and the metal sprite printed a
regular 64-pixel panel/rivet grid. The truck also had overlapping strip glazing,
long A-pillars ahead of its doors, unsupported hood strips, a cargo bed separated
from its chassis, and flat canopy crossbars below the arched cloth.

The generator now projects material UVs in continuous vehicle coordinates. The
longest vehicle dimension maps to one complete 256-pixel sprite, with equal texel
density on all three axes. Clipped neighbouring cells share their UV endpoints;
every UV stays within its texture-atlas sprite. Weathering is irregular fading,
dirt and small paint chips. Structural seams and hardware come from the model,
so there is no artificial square pattern repeated across the body or glazing.

The truck has an upright, continuous windscreen aligned with the doors, a solid
hood, connected mirror brackets, load-bearing chassis rails, a rear crossmember
and bumper, and steel canopy hoops matching the cloth arch. The old utility
vehicle retains its existing registry name but carries civilian luggage instead
of the raised weapon-hatch panel. Faded blue-green paint and brown canvas fit
the forest settlement setting.

All 237 cell registry IDs and all three reserved footprints are unchanged:
truck 3×4×7, utility vehicle 3×3×5, old tank 4×3×9. The tank remains available to old
saves; settlement generation does not need to place it. Every model cuboid is
also its exact collision cuboid, and all four horizontal rotations are checked.
Existing placed vehicles receive the asset/shape repairs when the mod updates.

Validation:

```text
python tools/generate_military_vehicles.py --check
python tools/test_vehicle_geometry.py
```

The eight focused regressions cover the reported block-sized texture seams,
regular panel grid, solid hood, windscreen position, canopy supports, chassis
connection, unchanged reserved cells, and visual/collision/rotation agreement.
`tools/preview/render_vehicles.py` renders low side views directly from the
production blockstates, models and textures. Its images are offline geometry
inspections with approximate lighting, not Minecraft client screenshots.
