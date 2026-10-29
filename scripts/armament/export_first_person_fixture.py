"""Blender authoring/export for the first-person technical assembly.
Imports the stage-3 calibration GLB, gives its handheld copy a documented 45 cm
diameter in Blender, and authors Steve/Alex panel meshes, skin UVs and poses.
Run: blender -b --python scripts/armament/export_first_person_fixture.py
"""
import bpy
import bmesh
import json
import math
import re
import struct
import sys
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
ASSETS = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug'
sys.path.insert(0, str(Path(__file__).parent))
from minecraft_hand_geometry import make_arm, skin_image, skin_material
VARIANT = sys.argv[sys.argv.index('--variant') + 1] if '--variant' in sys.argv else 'default'
assert VARIANT in ('default', 'slim')
STEM = 'first_person_fixture' + ('_slim' if VARIANT == 'slim' else '')
CAMERA_OFFSET = [0.34, -0.07, -0.90]  # 90 cm camera distance leaves clearance during inspect.
CAMERA_ROTATION = Quaternion((0, 0, 1), math.radians(15))  # Deliberate FPS roll towards the sight line.
HANDHELD_METRES = 0.45
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1
scene.render.fps = 96  # Dense export baking keeps fractional-frame error below 0.08 mm.
scene.frame_start, scene.frame_end = 0, 288
bpy.ops.import_scene.gltf(filepath=str(ASSETS / 'technical_fixture.glb'))
technical = list(scene.objects)
for obj in technical:
    obj.name = 'fixture_' + re.sub(r'[^a-z0-9_]', '_', obj.name.lower())
technical_root = next(obj for obj in technical if obj.parent is None)
base = next(obj for obj in technical if 'octagonalbase' in obj.name)
object_meshes = [obj for obj in technical if obj.type == 'MESH']
technical_root.scale *= HANDHELD_METRES
bpy.context.view_layer.update()

root = bpy.data.objects.new('presentation_root', None)
scene.collection.objects.link(root)
root.rotation_mode = 'XYZ'
technical_root.parent = root
arm_data = bpy.data.armatures.new('view_hand_skeleton')
arm = bpy.data.objects.new('view_hands_rig', arm_data)
scene.collection.objects.link(arm)
arm.parent = root
bpy.context.view_layer.update()

image = skin_image(ART, VARIANT)
materials = {side: (skin_material('player_skin_' + side, image),
                    skin_material('player_sleeve_' + side, image, True)) for side in ('right', 'left')}

# Select the actual imported front plate faces; contact geometry is not guessed
# from a Java offset or reconstructed as a Minecraft cube.
arms = {}
for side, sign in [('right', 1), ('left', -1)]:
    wanted = Vector((0.383, -0.924, 0)) if side == 'right' else Vector((-0.924, -0.383, 0))
    faces = [p for p in base.data.polygons if abs(p.normal.z) < 0.1]
    face = max(faces, key=lambda p: (base.matrix_world.to_3x3() @ p.normal).dot(wanted))
    center = base.matrix_world @ face.center
    outward = (base.matrix_world.to_3x3() @ face.normal).normalized()
    forward = -outward
    lateral = forward.cross(Vector((0, 0, 1))).normalized()
    edge = Vector((center.x, center.y, 0))
    wrist = edge - forward * 0.185 + Vector((0, 0, -0.095))
    approach = Vector((.8, -.36, .15)) if side == 'right' else Vector((-.8, -.36, .15))
    upper_direction = Vector((sign * .70, -.20, -.68))
    elbow = wrist + approach.normalized() * .25
    shoulder = elbow + upper_direction.normalized() * .375
    arms[side] = {'side': side, 'sign': sign, 'forward': forward, 'lateral': lateral,
                  'edge': edge, 'shoulder': shoulder, 'elbow': elbow, 'wrist': wrist}

bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode='EDIT')
for side, data in arms.items():
    upper = arm_data.edit_bones.new('upper_' + side)
    upper.head, upper.tail = data['shoulder'], data['elbow']
    upper.align_roll(Vector((0, 0, 1)))
    lower = arm_data.edit_bones.new('forearm_' + side)
    lower.head, lower.tail, lower.parent, lower.use_connect = data['elbow'], data['wrist'], upper, True
    lower.align_roll(Vector((0, 0, 1)))
    hand = arm_data.edit_bones.new('hand_' + side)
    hand.head, hand.tail, hand.parent, hand.use_connect = data['wrist'], data['wrist'] + data['forward'] * 0.085, lower, True
    hand.align_roll(Vector((0, 0, 1)))
    data['digits'] = []
    pixel_width = (3 if VARIANT == 'slim' else 4) / 16
    finger_order = ('index', 'middle', 'ring', 'little') if side == 'right' else ('little', 'ring', 'middle', 'index')
    data['palm_width'] = pixel_width * .92
    for finger in ('thumb', *finger_order):
        if finger == 'thumb':
            direction = -1 if side == 'right' else 1
            start = data['wrist'] + data['forward'] * .10 + data['lateral'] * (direction * data['palm_width'] / 2)
            p = [start,
                 start + data['lateral'] * (direction * .045) + data['forward'] * .015 + Vector((0, 0, .008)),
                 start + data['lateral'] * (direction * .060) + data['forward'] * .050 + Vector((0, 0, .045)),
                 start + data['lateral'] * (direction * .050) + data['forward'] * .075 + Vector((0, 0, .065))]
        else:
            j = finger_order.index(finger)
            offset = ([-.369, -.125, .125, .369][j]) * data['palm_width']
            start = data['wrist'] + data['forward'] * .115 + data['lateral'] * offset
            p = [start, start + data['forward'] * .035 + Vector((0, 0, .045)),
                 start + data['forward'] * .040 + Vector((0, 0, .105)),
                 start + data['forward'] * .018 + Vector((0, 0, .135))]
        previous = hand
        names = []
        for i in range(3):
            bone = arm_data.edit_bones.new(f'{finger}_{i + 1}_{side}')
            bone.head, bone.tail, bone.parent = p[i], p[i + 1], previous
            bone.use_connect = i > 0
            names.append(bone.name)
            previous = bone
        data['digits'].append((finger, p, names))
bpy.ops.object.mode_set(mode='OBJECT')
for bone in arm.pose.bones:
    bone.rotation_mode = 'QUATERNION'


def socket_object(name, parent, matrix):
    obj = bpy.data.objects.new(name, None)
    scene.collection.objects.link(obj)
    obj.parent = parent
    obj.matrix_local = matrix
    return obj


socket_objects = []
for side, data in arms.items():
    slot = 'primary' if side == 'right' else 'secondary'
    bind = arm_data.bones['hand_' + side].matrix_local.copy()
    socket_objects.append(socket_object('grip_' + slot, technical_root, technical_root.matrix_world.inverted() @ bind))
    hand_socket = bpy.data.objects.new('hand_' + slot, None)
    scene.collection.objects.link(hand_socket)
    hand_socket.parent, hand_socket.parent_type, hand_socket.parent_bone = arm, 'BONE', 'hand_' + side
    # Blender bone-parent objects use the bone tail as their parent origin.
    hand_socket.location = (0, -arm_data.bones['hand_' + side].length, 0)
    socket_objects.append(hand_socket)


hand_meshes = [obj for data in arms.values() for obj in make_arm(data, arm, VARIANT, *materials[data['side']])]
# Hand-authored key poses, seconds: translation metres, Euler degrees, elbow swivel degrees.
# These keyframes live in Blender and are exported/baked by its glTF exporter.
specs = {
    'idle': [(0, (0, 0, 0), (0, 0, 0), 0), (0.5, (0, 0.002, 0.002), (0.2, 0, 0.15), 0.6), (1, (0, 0, 0), (0, 0, 0), 0), (1.5, (0, -0.002, -0.002), (-0.2, 0, -0.15), -0.6), (2, (0, 0, 0), (0, 0, 0), 0)],
    'inspect': [(0, (0, 0, 0), (0, 0, 0), 0), (0.75, (0.025, 0.025, 0.035), (3, -2, -9), 4), (1.5, (-0.025, 0.025, 0.035), (-2, 2, 9), -4), (2.25, (0.01, 0.01, 0.015), (1, 0, -4), 2), (3, (0, 0, 0), (0, 0, 0), 0)],
    'raise': [(0, (0, 0.04, -0.30), (-5, 0, 0), 0), (0.5, (0, 0.025, -0.13), (-2, 0, 0), 1), (1, (0, 0.002, 0.006), (0.2, 0, 0), 0.4), (1.25, (0, 0, 0), (0, 0, 0), 0)],
    'lower': [(0, (0, 0, 0), (0, 0, 0), 0), (0.5, (0, 0.012, -0.08), (-1, 0, 0), 1), (1, (0, 0.035, -0.25), (-4, 0, 0), 0.2), (1.25, (0, 0.04, -0.30), (-5, 0, 0), 0)],
    'move': [(0, (0, 0, 0), (0, 0, 0), 0), (0.25, (0.012, 0.003, 0.008), (0.4, 0, 0.7), 1.2), (0.5, (0, 0, 0), (0, 0, 0), 0), (0.75, (-0.012, 0.003, 0.008), (-0.4, 0, -0.7), -1.2), (1, (0, 0, 0), (0, 0, 0), 0)]}
actions = {}


def arm_pose(swivel):
    for bone in arm.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)
    for side, data in arms.items():
        axis = (data['wrist'] - data['shoulder']).normalized()
        rotation = Quaternion(axis, math.radians(swivel * data['sign'])).to_matrix().to_4x4()
        transform = Matrix.Translation(data['shoulder']) @ rotation @ Matrix.Translation(-data['shoulder'])
        upper = arm_data.bones['upper_' + side].matrix_local
        forearm = arm_data.bones['forearm_' + side].matrix_local
        hand = arm_data.bones['hand_' + side].matrix_local
        # Derive FK bases directly. Evaluating depsgraph between matrix assignments
        # would let the currently active Action overwrite the partially keyed pose.
        arm.pose.bones['upper_' + side].rotation_quaternion = (upper.inverted() @ transform @ upper).to_quaternion()
        arm.pose.bones['hand_' + side].rotation_quaternion = (hand.inverted() @ forearm @ (transform @ forearm).inverted() @ hand).to_quaternion()
        for finger, _, names in data['digits']:
            arm.pose.bones[names[-1]].rotation_quaternion = Quaternion((1, 0, 0), math.radians(swivel * 0.08))


for clip, keys in specs.items():
    actions[clip] = {}
    for obj in (root, arm):
        obj.animation_data_create()
        obj.animation_data.use_nla = False
        action = bpy.data.actions.new('fp_' + clip + '__' + obj.name)
        obj.animation_data.action = action
        actions[clip][obj.name] = action
    for seconds, translation, rotation, swivel in keys:
        frame = round(seconds * scene.render.fps)
        root.location = translation
        root.rotation_euler = [math.radians(value) for value in rotation]
        arm_pose(swivel)
        root.keyframe_insert('location', frame=frame)
        root.keyframe_insert('rotation_euler', frame=frame)
        for bone in arm.pose.bones:
            bone.keyframe_insert('rotation_quaternion', frame=frame)
    for obj in (root, arm):
        action = actions[clip][obj.name]
        for curve in action.fcurves:
            for key in curve.keyframe_points:
                key.interpolation = 'LINEAR' if obj == arm else 'BEZIER'
                key.handle_left_type = key.handle_right_type = 'AUTO_CLAMPED'
        obj.animation_data.action = None
        track = obj.animation_data.nla_tracks.new();track.name = clip;track.strips.new(clip, 0, action)

scene.frame_set(0)
root.location = (0, 0, 0);root.rotation_euler = (0, 0, 0);arm_pose(0)
bpy.context.view_layer.update()
bpy.ops.object.select_all(action='DESELECT')
for obj in scene.objects:
    if obj.type in {'MESH', 'ARMATURE', 'EMPTY'}:
        obj.select_set(True)
bpy.context.view_layer.objects.active = root
export = ASSETS / (STEM + '.glb')
bpy.ops.export_scene.gltf(filepath=str(export), export_format='GLB', use_selection=True,
    export_yup=True, export_normals=True, export_texcoords=True, export_materials='EXPORT',
    export_animations=True, export_skins=True, export_animation_mode='ACTIONS',
    export_force_sampling=True, export_optimize_animation_size=False, export_morph=False, export_extras=False)

C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))


def column_major(matrix):
    return [matrix[r][c] for c in range(4) for r in range(4)]


reference = {'blender': bpy.app.version_string, 'source_model': 'technical_fixture.glb',
             'metres_per_unit': 1, 'handheld_metres': HANDHELD_METRES, 'variant': VARIANT, 'camera_offset': CAMERA_OFFSET, 'clips': {}, 'intersections': []}
reference['camera_rotation'] = [CAMERA_ROTATION.x, CAMERA_ROTATION.y, CAMERA_ROTATION.z, CAMERA_ROTATION.w]
reference['topology'] = {obj.name: {'vertices': len(obj.data.vertices), 'polygons': len(obj.data.polygons),
                                 'nonmanifold_edges': obj['nonmanifold_edges'], 'arm_pixels': obj['minecraft_arm_pixels'], 'weighted_bones': list(obj['weighted_bones'])}
                         for obj in hand_meshes}
for obj in (root, arm):
    for track in obj.animation_data.nla_tracks:
        track.mute = True
for clip, keys in specs.items():
    for obj in (root, arm):
        obj.animation_data.action = actions[clip][obj.name]
    duration = keys[-1][0];samples = []
    for fraction in (0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1):
        seconds = fraction * duration;frame = seconds * scene.render.fps
        scene.frame_set(int(frame), subframe=frame - int(frame))
        depsgraph = bpy.context.evaluated_depsgraph_get()
        sample = {'seconds': seconds, 'vertices': {}, 'sockets': {}}
        bvhs = {};holding_self = {}
        for obj in object_meshes + hand_meshes:
            evaluated = obj.evaluated_get(depsgraph);mesh = evaluated.to_mesh()
            world = evaluated.matrix_world
            points = [world @ v.co for v in mesh.vertices]
            sample['vertices'][obj.name] = [list((C @ p.to_4d()).xyz) for p in points]
            bvhs[obj.name] = BVHTree.FromPolygons(points, [list(p.vertices) for p in mesh.polygons], all_triangles=False, epsilon=0)
            if obj.name.startswith('view_arm_'):
                mesh.calc_loop_triangles();triangles = [list(t.vertices) for t in mesh.loop_triangles]
                bvh = BVHTree.FromPolygons(points, triangles, all_triangles=True)
                holding_self[obj.name] = sum(a < b and not set(triangles[a]).intersection(triangles[b]) for a, b in bvh.overlap(bvh))
            evaluated.to_mesh_clear()
        for obj in socket_objects:
            sample['sockets'][obj.name] = column_major(C @ obj.evaluated_get(depsgraph).matrix_world @ C.inverted())
        intersections = {hand.name + '/' + obj.name: len(bvhs[hand.name].overlap(bvhs[obj.name])) for hand in hand_meshes for obj in object_meshes}
        intersections['left_arm/right_arm'] = len(bvhs['view_arm_left'].overlap(bvhs['view_arm_right']))
        for side in arms:
            intersections['skin/sleeve_' + side] = len(bvhs['view_arm_' + side].overlap(bvhs['view_sleeve_' + side]))
        reference['intersections'].append({'clip': clip, 'seconds': seconds, 'pairs': {k: v for k, v in intersections.items() if v}, 'self_contacts': holding_self})
        samples.append(sample)
    reference['clips'][clip] = samples

# QA poses deliberately release the held object: exercise actual relative elbow
# flexion and wrist flexion, not just the contact-preserving holding swivel.
for obj in (root, arm):
    obj.animation_data.action = None
root.location = (0, 0, 0);root.rotation_euler = (0, 0, 0)
reference['joint_stress'] = []
for elbow_degrees, wrist_degrees in [(0, 0), (45, 0), (-30, 0), (0, 25), (0, -25), (45, 25), (-30, -25)]:
    arm_pose(0)
    for side in arms:
        arm.pose.bones['forearm_' + side].rotation_quaternion = Quaternion((1, 0, 0), math.radians(elbow_degrees))
        arm.pose.bones['hand_' + side].rotation_quaternion = Quaternion((1, 0, 0), math.radians(wrist_degrees))
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    result = {'elbow_degrees': elbow_degrees, 'wrist_degrees': wrist_degrees, 'meshes': {}}
    for obj in hand_meshes:
        if 'sleeve' in obj.name:
            continue
        evaluated = obj.evaluated_get(depsgraph);mesh = evaluated.to_mesh()
        points = [evaluated.matrix_world @ v.co for v in mesh.vertices]
        ratios = [(points[e.vertices[1]] - points[e.vertices[0]]).length / (obj.data.vertices[e.vertices[1]].co - obj.data.vertices[e.vertices[0]].co).length for e in mesh.edges]
        mesh.calc_loop_triangles();triangles = [list(t.vertices) for t in mesh.loop_triangles]
        bvh = BVHTree.FromPolygons(points, triangles, all_triangles=True)
        intersections = [(a, b) for a, b in bvh.overlap(bvh) if a < b and not set(triangles[a]).intersection(triangles[b])]
        result['meshes'][obj.name] = {'min_edge_ratio': min(ratios), 'max_edge_ratio': max(ratios), 'nonadjacent_triangle_contacts': len(intersections)}
        evaluated.to_mesh_clear()
    reference['joint_stress'].append(result)
arm_pose(0)
bpy.context.view_layer.update()
(ART / (STEM + '.reference.json')).write_text(json.dumps(reference, indent=2) + '\n')

raw = export.read_bytes();doc = json.loads(raw[20:20 + struct.unpack_from('<I', raw, 12)[0]])
parents = {child: i for i, node in enumerate(doc['nodes']) for child in node.get('children', [])}
rig_id = 'bobin_craft:debug/first_person_rig' + ('_slim' if VARIANT == 'slim' else '')
rig = {'schema_version': 1, 'id': rig_id, 'nodes': [], 'roles': {'weapon_root': 'presentation_root'}}
for i, node in enumerate(doc['nodes']):
    entry = {'name': node['name'], 'node_index': i}
    if i in parents:entry['parent'] = doc['nodes'][parents[i]]['name']
    rig['nodes'].append(entry)
sockets = {'schema_version': 1, 'id': 'bobin_craft:debug/' + STEM + '_sockets', 'rig_id': rig_id, 'sockets': []}
for name in ('grip_primary', 'grip_secondary', 'hand_primary', 'hand_secondary'):
    sockets['sockets'].append({'name': name, 'node': name, 'transform': {'translation': [0, 0, 0], 'rotation': [0, 0, 0, 1], 'scale': [1, 1, 1]}})
profile = {'schema_version': 1, 'id': 'bobin_craft:debug/first_person',
           'model': 'bobin_craft:armament/debug/' + STEM + '.glb',
           'rig': 'bobin_craft:armament/debug/' + STEM + '.rig.json',
           'sockets': 'bobin_craft:armament/debug/' + STEM + '.sockets.json',
           'camera_offset': CAMERA_OFFSET, 'camera_rotation': [CAMERA_ROTATION.x, CAMERA_ROTATION.y, CAMERA_ROTATION.z, CAMERA_ROTATION.w],
           'camera_clearance': 0.075, 'transition_seconds': 0.2, 'movement_threshold': 0.05,
           'contact_metres': 0.001, 'contact_degrees': 0.2,
           'clips': {name: name for name in specs},
           'grips': [{'name': 'primary', 'target': 'grip_primary', 'hand': 'hand_primary'}, {'name': 'secondary', 'target': 'grip_secondary', 'hand': 'hand_secondary'}],
           'inertia': {'enabled': True, 'frequency_hz': 4, 'lag_seconds': 0.016, 'max_yaw_degrees': 1.5, 'max_pitch_degrees': 1.0, 'metres_per_degree': 0.0015}}
for kind, data in [('rig', rig), ('sockets', sockets), ('profile', profile)]:
    (ASSETS / (STEM + '.' + kind + '.json')).write_text(json.dumps(data, indent=2) + '\n')

for obj in (root, arm):
    obj.animation_data.action = None
    obj.animation_data.use_nla = True
    for track in obj.animation_data.nla_tracks:
        track.mute = track.name != 'idle'
scene.frame_set(0)
bpy.ops.object.camera_add(location=(-CAMERA_OFFSET[0], CAMERA_OFFSET[2], -CAMERA_OFFSET[1]))
camera = bpy.context.object
model_to_view = Matrix.Translation(CAMERA_OFFSET) @ CAMERA_ROTATION.to_matrix().to_4x4()
camera.matrix_world = C.inverted() @ model_to_view.inverted()
camera.data.sensor_fit = 'VERTICAL';camera.data.sensor_height = 24;camera.data.lens = 12 / math.tan(math.radians(70) / 2)
scene.camera = camera
for location, energy, size in [((1, -2, 3), 350, 4), ((-2, -1, 1), 180, 3)]:
    bpy.ops.object.light_add(type='AREA', location=location)
    light = bpy.context.object;light.data.energy = energy;light.data.size = size
    light.rotation_euler = (Vector((0, 0, 0.25)) - light.location).to_track_quat('-Z', 'Y').to_euler()
scene.render.engine = 'CYCLES';scene.cycles.device = 'CPU';scene.cycles.samples = 128;scene.cycles.use_denoising = False
scene.render.resolution_x = 960;scene.render.resolution_y = 540;scene.render.resolution_percentage = 100
scene.render.film_transparent = False;scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(ART / (STEM + '.blender-preview.png'))
arm.show_in_front = True
from bpy_extras.object_utils import world_to_camera_view
bpy.context.view_layer.update()
framing = {'variant': VARIANT, 'vertical_fov_degrees': 70, 'aspect': 16 / 9,
           'camera_offset_metres': CAMERA_OFFSET, 'roll_degrees': 15, 'bounds_top_left_normalized': {},
           'visible_bounds_top_left_normalized': {}}


def clip_to_viewport(points):
    for axis, boundary, sign in [(0, 0, 1), (0, 1, -1), (1, 0, 1), (1, 1, -1)]:
        out = []
        if not points:
            break
        previous = points[-1];previous_distance = sign * (previous[axis] - boundary)
        for current in points:
            distance = sign * (current[axis] - boundary)
            if (distance >= 0) != (previous_distance >= 0):
                out.append(previous.lerp(current, previous_distance / (previous_distance - distance)))
            if distance >= 0:
                out.append(current)
            previous, previous_distance = current, distance
        points = out
    return points


for label, objects in [('technical_object', object_meshes), ('left_hand_and_sleeve', [o for o in hand_meshes if o.name.endswith('_left')]),
                       ('right_hand_and_sleeve', [o for o in hand_meshes if o.name.endswith('_right')])]:
    points, visible = [], []
    for obj in objects:
        evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh = evaluated.to_mesh()
        projected = [world_to_camera_view(scene, camera, evaluated.matrix_world @ v.co) for v in mesh.vertices]
        points.extend(projected)
        mesh.calc_loop_triangles()
        for triangle in mesh.loop_triangles:
            visible.extend(clip_to_viewport([projected[i].xy for i in triangle.vertices]))
        evaluated.to_mesh_clear()
    framing['bounds_top_left_normalized'][label] = [min(p.x for p in points), 1 - max(p.y for p in points), max(p.x for p in points), 1 - min(p.y for p in points)]
    framing['visible_bounds_top_left_normalized'][label] = [min(p.x for p in visible), 1 - max(p.y for p in visible), max(p.x for p in visible), 1 - min(p.y for p in visible)]
(ART / (STEM + '.framing.json')).write_text(json.dumps(framing, indent=2) + '\n')
bpy.ops.wm.save_as_mainfile(filepath=str(ART / (STEM + '.blend')))
bpy.ops.render.render(write_still=True)
print('FIRST_PERSON_EXPORTED', export)
print('CLIPS', [(a.get('name'), len(a['channels'])) for a in doc.get('animations', [])])
print('HAND_OBJECT_INTERSECTIONS', sum(bool(i['pairs']) for i in reference['intersections']))
