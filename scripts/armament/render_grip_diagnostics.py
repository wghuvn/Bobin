"""Read-only Blender authoring previews, NOT Minecraft screenshots.

Run after exporting both authored fixtures:
  blender -b --python scripts/armament/render_grip_diagnostics.py
  blender -b --python scripts/armament/render_grip_diagnostics.py -- --variant default --draft

Without options, preserves the existing .fov90.png and .grip-detail.png outputs.
Use --output-dir for the task-5.1.3 comparison set: matching 70/90/110 degree
FPS views, 4:3/21:9 views, and three close-ups of each actual grip surface.
Use --source-dir to render archived before .blend files without replacing them.
It never saves a .blend, exports GLB, or updates reference/metadata JSON.
The FPS preview retains the authored camera pose and metres, changing only
vertical FOV to 90 degrees. The external close-up shows both grip locations
with in-memory diagnostic arrows: green +Y primary tangent, orange -Z palm
normal, magenta anatomical thumb side (right -X, left +X). Gizmos are offset
200 mm outward so the inward normal arrow is visible instead of buried inside
the opaque handle; a grey line connects the displayed gizmo to the real origin.
"""
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
ASSETS = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug'


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--variant', choices=('all', 'default', 'slim'), default='all')
    parser.add_argument('--draft', action='store_true', help='16 samples instead of 64; geometry and camera are unchanged')
    parser.add_argument('--source-dir', type=Path, default=ART, help='Directory containing the authored .blend files')
    parser.add_argument('--output-dir', type=Path, help='Comparison output directory; also enables the extended view set')
    parser.add_argument('--label', default='authored', help='Provenance label recorded in the diagnostic manifest')
    parser.add_argument('--only-closeups', action='store_true', help='Refresh only six close-ups in an existing --output-dir set')
    parser.add_argument('--threads', type=int, default=4, help='CPU render threads')
    return parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])


def protected_hashes(source_dir=ART):
    # Archived sources can be rendered while a separate Blender process exports
    # the current fixtures; only protect this process's input artifacts then.
    folders = {ART, ASSETS} if source_dir.resolve() == ART.resolve() else {source_dir}
    files = [p for folder in folders for p in folder.glob('first_person_*') if p.suffix in ('.blend', '.glb', '.json')]
    return {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}


def vertical_fov(camera, degrees):
    camera.data.type = 'PERSP'
    camera.data.sensor_fit = 'VERTICAL'
    camera.data.sensor_height = 24
    camera.data.lens = 12 / math.tan(math.radians(degrees) / 2)


def freeze_idle(scene):
    for obj in scene.objects:
        animation = obj.animation_data
        if animation is not None and animation.nla_tracks:
            animation.action = None
            animation.use_nla = True
            for track in animation.nla_tracks:
                track.mute = track.name != 'idle'
    scene.frame_set(0)
    bpy.context.view_layer.update()


def emission(name, color):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    nodes.clear()
    output = nodes.new('ShaderNodeOutputMaterial')
    shader = nodes.new('ShaderNodeEmission')
    shader.inputs['Color'].default_value = (*color, 1)
    shader.inputs['Strength'].default_value = 1
    material.node_tree.links.new(shader.outputs['Emission'], output.inputs['Surface'])
    return material


def arrow(name, origin, direction, material, length=.055):
    direction = direction.normalized()
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D';curve.resolution_u = 1;curve.bevel_depth = .0016;curve.bevel_resolution = 0
    spline = curve.splines.new('POLY');spline.points.add(1)
    spline.points[0].co = (*origin, 1)
    spline.points[1].co = (*(origin + direction * (length - .008)), 1)
    obj = bpy.data.objects.new(name, curve);bpy.context.scene.collection.objects.link(obj)
    obj.data.materials.append(material)
    bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=.0045, radius2=0, depth=.010, location=origin + direction * (length - .005))
    tip = bpy.context.object;tip.name = name + '_arrowhead'
    tip.rotation_euler = direction.to_track_quat('Z', 'Y').to_euler();tip.data.materials.append(material)


def connector(name, start, end, material):
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D';curve.resolution_u = 1;curve.bevel_depth = .0007;curve.bevel_resolution = 0
    spline = curve.splines.new('POLY');spline.points.add(1)
    spline.points[0].co = (*start, 1);spline.points[1].co = (*end, 1)
    obj = bpy.data.objects.new(name, curve);bpy.context.scene.collection.objects.link(obj);curve.materials.append(material)


def label(text, position, camera, material):
    curve = bpy.data.curves.new('diagnostic_label_' + text, 'FONT')
    curve.body = text;curve.size = .017;curve.align_x = 'CENTER'
    obj = bpy.data.objects.new(curve.name, curve);bpy.context.scene.collection.objects.link(obj)
    obj.location = position;obj.rotation_euler = camera.rotation_euler.copy()
    curve.materials.append(material)


def render(scene, path, records=None, view=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    print('BLENDER_DIAGNOSTIC_ONLY', path)
    if records is not None:
        records.append({'file': path.name, 'view': view,
                        'resolution': [scene.render.resolution_x, scene.render.resolution_y],
                        'vertical_fov_degrees': math.degrees(2 * math.atan(scene.camera.data.sensor_height / (2 * scene.camera.data.lens))),
                        'camera_world_column_major': [scene.camera.matrix_world[r][c] for c in range(4) for r in range(4)]})


def render_variant(variant, args):
    stem = 'first_person_fixture' + ('_slim' if variant == 'slim' else '')
    source = args.source_dir / (stem + '.blend')
    output = args.output_dir or ART
    records = []
    if not source.is_file():
        raise FileNotFoundError('Export the authored fixture first: ' + str(source))
    bpy.ops.wm.open_mainfile(filepath=str(source))
    scene = bpy.context.scene
    freeze_idle(scene)
    scene.render.engine = 'CYCLES';scene.cycles.device = 'CPU';scene.cycles.use_denoising = False
    scene.cycles.samples = 16 if args.draft else 64
    scene.render.threads_mode = 'FIXED';scene.render.threads = max(1, args.threads)
    scene.render.resolution_x = 960;scene.render.resolution_y = 540;scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG';scene.render.film_transparent = False
    if scene.camera is None:
        raise ValueError('Authored FPS camera is missing from ' + str(source))

    # Render the unmodified geometry from the exact authored FPS camera pose.
    fps_camera = scene.camera
    for degrees in () if args.only_closeups else ((70, 90, 110) if args.output_dir else (90,)):
        vertical_fov(fps_camera, degrees)
        render(scene, output / (stem + '.fov' + str(degrees) + '.png'), records, 'FPS 16:9')
    if args.output_dir and not args.only_closeups:
        vertical_fov(fps_camera, 90)
        for name, width in [('4x3', 720), ('21x9', 1260)]:
            scene.render.resolution_x = width
            render(scene, output / (stem + '.aspect-' + name + '.png'), records, 'FPS ' + name)
        scene.render.resolution_x = 960

    grips = [bpy.data.objects.get('grip_primary'), bpy.data.objects.get('grip_secondary')]
    if any(obj is None for obj in grips):
        raise ValueError('Both independent object grip sockets are required')
    matrices = [obj.evaluated_get(bpy.context.evaluated_depsgraph_get()).matrix_world.copy() for obj in grips]
    centres = [matrix.translation for matrix in matrices]
    target = (centres[0] + centres[1]) * .5 + Vector((0, 0, .02))
    radius = max((point - target).length for point in centres) + .18
    distance = radius / math.tan(math.radians(50) / 2) * 1.08
    camera_data = bpy.data.cameras.new('diagnostic_external_camera')
    camera = bpy.data.objects.new(camera_data.name, camera_data);scene.collection.objects.link(camera)
    # Look upward at the two contact surfaces. A top view hides the support
    # palm under the long body and makes its normal/label impossible to inspect.
    camera.location = target + Vector((1.1, 1.3, -.65)).normalized() * distance
    camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
    vertical_fov(camera, 50);scene.camera = camera
    bpy.context.view_layer.update()

    tangent = emission('diagnostic_primary_Y', (.15, 1, .20))
    palm = emission('diagnostic_palm_minus_Z', (1, .35, .04))
    thumb = emission('diagnostic_thumb_side', (1, .10, .85))
    text = emission('diagnostic_label_white', (.95, .95, .95))
    link = emission('diagnostic_origin_link', (.45, .45, .45))
    for index, matrix in enumerate(matrices):
        name = 'PRIMARY' if index == 0 else 'SECONDARY'
        actual_origin = matrix.translation
        axes = matrix.to_3x3()
        origin = actual_origin + axes.col[2].normalized() * .20
        connector('diagnostic_' + name + '_origin_link', actual_origin, origin, link)
        arrow('diagnostic_' + name + '_primary', origin, axes.col[1], tangent)
        arrow('diagnostic_' + name + '_palm', origin, -axes.col[2], palm)
        arrow('diagnostic_' + name + '_thumb', origin, axes.col[0] * (-1 if index == 0 else 1), thumb)
        camera_axes = camera.matrix_world.to_3x3()
        # Keep the label outside the wrist silhouette and slightly toward the
        # external camera. The grey connector still identifies the real origin.
        label(name, origin + camera_axes.col[1].normalized() * .085 + camera_axes.col[2].normalized() * .05, camera, text)
    if not args.only_closeups:
        render(scene, output / (stem + '.grip-detail.png'), records, 'both grips with semantic axes')

    if args.output_dir:
        # Show real surfaces without axis gizmos obscuring fingertip contacts.
        # These changes exist only in memory; meshes, bind pose and clips stay intact.
        for obj in scene.objects:
            if obj.name.startswith('diagnostic_') and obj != camera:
                obj.hide_render = True
        scene.render.resolution_x = scene.render.resolution_y = 800
        # The authored FPS lights illuminate arm exteriors. A soft inspection
        # light follows the close-up camera so underside contacts remain readable.
        light_data = bpy.data.lights.new('diagnostic_contact_light', 'AREA')
        light_data.energy = 12;light_data.shape = 'DISK';light_data.size = .5
        contact_light = bpy.data.objects.new(light_data.name, light_data)
        scene.collection.objects.link(contact_light)
        for index, matrix in enumerate(matrices):
            side = 'right' if index == 0 else 'left'
            axes = matrix.to_3x3()
            # Contact origin and orientation come from the independently authored
            # surface socket, rather than the hand socket under test.
            target = matrix.translation + axes.col[1] * .015 + axes.col[2] * .025
            for angle, local in [('dorsal', (.35, .15, 1)),
                                 ('fingers', (.35, .7, -.8)),
                                 ('side', (-1 if index == 0 else 1, -.25, .85))]:
                direction = sum((axes.col[i] * value for i, value in enumerate(local)), Vector()).normalized()
                camera.location = target + direction * .36
                camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
                contact_light.location = camera.location
                contact_light.rotation_euler = camera.rotation_euler
                vertical_fov(camera, 50)
                bpy.context.view_layer.update()
                render(scene, output / (stem + '.grip-' + side + '-' + angle + '.png'), records, side + ' grip ' + angle)
        manifest = {'label': args.label, 'variant': variant, 'source': str(source),
                    'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                    'blender': bpy.app.version_string, 'kind': 'Blender authoring diagnostics, not Minecraft screenshots',
                    'frame': 0, 'animation': 'idle', 'samples': scene.cycles.samples, 'renders': records}
        manifest_path = output / (stem + '.diagnostics.json')
        if args.only_closeups and manifest_path.exists():
            previous = json.loads(manifest_path.read_text())
            if previous['source_sha256'] != manifest['source_sha256'] or previous['label'] != args.label:
                raise ValueError('Cannot merge close-ups from a different source or phase')
            refreshed = {record['file'] for record in records}
            manifest['renders'] = [record for record in previous['renders'] if record['file'] not in refreshed] + records
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    args = arguments()
    if args.only_closeups and not args.output_dir:
        raise ValueError('--only-closeups requires --output-dir')
    before = protected_hashes(args.source_dir)
    for variant in ('default', 'slim') if args.variant == 'all' else (args.variant,):
        render_variant(variant, args)
    after = protected_hashes(args.source_dir)
    if before != after:
        raise RuntimeError('Protected .blend/GLB/JSON files changed while rendering; no source save/export is allowed')
    print('PASS_READ_ONLY', len(before), 'protected files unchanged; PNG authoring previews only')
