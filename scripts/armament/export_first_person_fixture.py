"""Author the neutral ergonomic gauge and two held Minecraft hands in Blender.
All dimensions are metres; no object import/rescale and no Java-authored mesh.
Calibration samples the exact bind pose at idle time zero; animated idle breathes.
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
from ergonomic_calibration import create as create_gauge
from triangle_contacts import nonadjacent_contacts
from first_person_animation_poses import CLIP_POSES, authoring_metadata
VARIANT = sys.argv[sys.argv.index('--variant') + 1] if '--variant' in sys.argv else 'default'
assert VARIANT in ('default', 'slim')
STEM = 'first_person_fixture' + ('_slim' if VARIANT == 'slim' else '')
CAMERA_OFFSET = [0.24, -0.23, -0.90]  # 12 cm view clearance; preserve all authored metric dimensions.
CAMERA_ROTATION = Quaternion((0, 0, 1), 0)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1
scene.render.fps = 192  # Fast draw/stow curves need denser baking to keep subframe error below 0.08 mm.
scene.frame_start, scene.frame_end = 0, round(max(keys[-1][0] for keys in CLIP_POSES.values()) * scene.render.fps)
root = bpy.data.objects.new('presentation_root', None)
scene.collection.objects.link(root)
root.rotation_mode = 'XYZ'
technical_root, object_meshes, arms = create_gauge(root, VARIANT)
arm_data = bpy.data.armatures.new('view_hand_skeleton')
arm = bpy.data.objects.new('view_hands_rig', arm_data)
scene.collection.objects.link(arm)
arm.parent = root
bpy.context.view_layer.update()

image = skin_image(ART, VARIANT)
materials = {side: (skin_material('player_skin_' + side, image),
                    skin_material('player_sleeve_' + side, image, True)) for side in ('right', 'left')}

bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode='EDIT')
for side, data in arms.items():
    upper = arm_data.edit_bones.new('upper_' + side)
    upper.head, upper.tail = data['shoulder'], data['elbow']
    tangent = (upper.tail-upper.head).normalized()
    lateral = (data['lateral']-tangent*data['lateral'].dot(tangent)).normalized()
    upper.align_roll(lateral.cross(tangent))
    lower = arm_data.edit_bones.new('forearm_' + side)
    lower.head, lower.tail, lower.parent, lower.use_connect = data['elbow'], data['wrist'], upper, True
    tangent = (lower.tail-lower.head).normalized()
    lateral = (data['lateral']-tangent*data['lateral'].dot(tangent)).normalized()
    lower.align_roll(lateral.cross(tangent))
    hand = arm_data.edit_bones.new('hand_' + side)
    hand.head, hand.tail, hand.parent, hand.use_connect = data['wrist'], data['wrist'] + data['forward'] * 0.065, lower, True
    hand.align_roll(data['dorsal'])
    for finger, path, names in data['digits']:
        previous = hand
        x_axis = data['lateral'] if finger != 'thumb' else data['forward'] * (1 if side == 'right' else -1)
        for i, name in enumerate(names):
            bone = arm_data.edit_bones.new(name)
            bone.head, bone.tail, bone.parent = path[i], path[i+1], previous
            bone.use_connect = i > 0
            tangent = (path[i+1]-path[i]).normalized()
            x_axis = (x_axis-tangent*x_axis.dot(tangent)).normalized()
            bone.align_roll(x_axis.cross(tangent))
            previous = bone
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
    bind = data['contact_frame']
    socket_objects.append(socket_object('grip_' + slot, technical_root, technical_root.matrix_world.inverted() @ bind))
    hand_socket = bpy.data.objects.new('hand_' + slot, None)
    scene.collection.objects.link(hand_socket)
    hand_socket.parent, hand_socket.parent_type, hand_socket.parent_bone = arm, 'BONE', 'hand_' + side
    # Blender bone-parent objects use the bone tail as their parent origin.
    offset = data['contact_offset']
    hand_socket.location = (offset.x, offset.y-arm_data.bones['hand_' + side].length, offset.z)
    socket_objects.append(hand_socket)


hand_meshes = [obj for data in arms.values() for obj in make_arm(data, arm, VARIANT, *materials[data['side']])]
# Independently authored action phases; Blender creates and bakes all keyframes.
specs = CLIP_POSES
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


reference = {'blender': bpy.app.version_string, 'source_model': 'ergonomic_calibration.py',
             'metres_per_unit': 1, 'gauge_body_metres': [.10,.69,.09], 'variant': VARIANT, 'camera_offset': CAMERA_OFFSET, 'clips': {}, 'subframe_checks': {}, 'intersections': []}
reference['camera_rotation'] = [CAMERA_ROTATION.x, CAMERA_ROTATION.y, CAMERA_ROTATION.z, CAMERA_ROTATION.w]
reference['animation_authoring'] = authoring_metadata()
reference['topology'] = {obj.name: {'vertices': len(obj.data.vertices), 'polygons': len(obj.data.polygons),
                                 'nonmanifold_edges': obj['nonmanifold_edges'], 'arm_pixels': obj['minecraft_arm_pixels'], 'weighted_bones': list(obj['weighted_bones']),
                                 'proximal_cap_vertices': list(obj['proximal_cap_vertices'])}
                         for obj in hand_meshes}
for obj in (root, arm):
    for track in obj.animation_data.nla_tracks:
        track.mute = True
for clip, keys in specs.items():
    for obj in (root, arm):
        obj.animation_data.action = actions[clip][obj.name]
    duration = keys[-1][0];samples = []
    # Retain the nine established golden poses and add genuinely off-grid times
    # to check GLB interpolation independently of Blender's baked key locations.
    for fraction in (0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1, .137, .413, .739):
        seconds = fraction * duration;frame = seconds * scene.render.fps
        scene.frame_set(int(frame), subframe=frame - int(frame))
        depsgraph = bpy.context.evaluated_depsgraph_get()
        sample = {'seconds': seconds, 'vertices': {}, 'sockets': {}, 'joints': {}, 'surface_contacts': {}}
        bvhs = {};holding_self = {}
        for obj in object_meshes + hand_meshes:
            evaluated = obj.evaluated_get(depsgraph);mesh = evaluated.to_mesh()
            world = evaluated.matrix_world
            points = [world @ v.co for v in mesh.vertices]
            sample['vertices'][obj.name] = [list((C @ p.to_4d()).xyz) for p in points]
            mesh.calc_loop_triangles()
            bvhs[obj.name] = BVHTree.FromPolygons(points, [list(t.vertices) for t in mesh.loop_triangles], all_triangles=True, epsilon=0)
            if obj.name.startswith('view_arm_'):
                mesh.calc_loop_triangles();triangles = [list(t.vertices) for t in mesh.loop_triangles]
                bvh = BVHTree.FromPolygons(points, triangles, all_triangles=True)
                holding_self[obj.name] = len(nonadjacent_contacts(points, triangles, bvh.overlap(bvh)))
            evaluated.to_mesh_clear()
        for obj in socket_objects:
            sample['sockets'][obj.name] = column_major(C @ obj.evaluated_get(depsgraph).matrix_world)
        evaluated_arm = arm.evaluated_get(depsgraph)
        for side in arms:
            for joint in ('upper_', 'forearm_', 'hand_'):
                name = joint + side
                sample['joints'][name] = list((C @ evaluated_arm.matrix_world @ evaluated_arm.pose.bones[name].head.to_4d()).xyz)
            slot = 'primary' if side == 'right' else 'secondary'
            target = next(obj for obj in socket_objects if obj.name == 'grip_' + slot).evaluated_get(depsgraph).matrix_world.translation
            # Independent evaluated triangle surfaces, not empty-to-empty socket error.
            palm = bvhs['view_arm_' + side].find_nearest(target)
            panel = bvhs['gauge_primary_handle' if side == 'right' else 'gauge_forward_support'].find_nearest(palm[0])
            sample['surface_contacts'][side] = {'target_to_skin_metres': palm[3], 'skin_to_panel_metres': panel[3]}
        intersections = {hand.name + '/' + obj.name: len(bvhs[hand.name].overlap(bvhs[obj.name])) for hand in hand_meshes for obj in object_meshes}
        intersections['left_arm/right_arm'] = len(bvhs['view_arm_left'].overlap(bvhs['view_arm_right']))
        for side in arms:
            intersections['skin/sleeve_' + side] = len(bvhs['view_arm_' + side].overlap(bvhs['view_sleeve_' + side]))
        reference['intersections'].append({'clip': clip, 'seconds': seconds, 'pairs': {k: v for k, v in intersections.items() if v}, 'self_contacts': holding_self})
        samples.append(sample)
    reference['clips'][clip] = samples[:9]
    reference['subframe_checks'][clip] = samples[9:]

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
        intersections = nonadjacent_contacts(points, triangles, bvh.overlap(bvh))
        result['meshes'][obj.name] = {'min_edge_ratio': min(ratios), 'max_edge_ratio': max(ratios), 'nonadjacent_triangle_contacts': len(intersections)}
        evaluated.to_mesh_clear()
    reference['joint_stress'].append(result)
arm_pose(0)
bpy.context.view_layer.update()

raw = export.read_bytes();doc = json.loads(raw[20:20 + struct.unpack_from('<I', raw, 12)[0]])
parents = {child: i for i, node in enumerate(doc['nodes']) for child in node.get('children', [])}
rig_id = 'bobin_craft:debug/first_person_rig' + ('_slim' if VARIANT == 'slim' else '')
rig = {'schema_version': 1, 'id': rig_id, 'nodes': [], 'roles': {'weapon_root': 'presentation_root'}}
for i, node in enumerate(doc['nodes']):
    entry = {'name': node['name'], 'node_index': i}
    if i in parents:entry['parent'] = doc['nodes'][parents[i]]['name']
    rig['nodes'].append(entry)
sockets = {'schema_version': 1, 'id': 'bobin_craft:debug/' + STEM + '_sockets', 'rig_id': rig_id, 'sockets': []}
# Empty transforms export as C*M*C^-1. Semantic socket axes require C*M,
# so author the fixed socket-local basis C once, for both hands and targets.
for name in ('grip_primary', 'grip_secondary', 'hand_primary', 'hand_secondary'):
    sockets['sockets'].append({'name': name, 'node': name, 'transform': {'translation': [0, 0, 0], 'rotation': [C.to_quaternion().x, C.to_quaternion().y, C.to_quaternion().z, C.to_quaternion().w], 'scale': [1, 1, 1]}})
profile = {'schema_version': 1, 'id': 'bobin_craft:debug/first_person',
           'model': 'bobin_craft:armament/debug/' + STEM + '.glb',
           'rig': 'bobin_craft:armament/debug/' + STEM + '.rig.json',
           'sockets': 'bobin_craft:armament/debug/' + STEM + '.sockets.json',
           'camera_offset': CAMERA_OFFSET, 'camera_rotation': [CAMERA_ROTATION.x, CAMERA_ROTATION.y, CAMERA_ROTATION.z, CAMERA_ROTATION.w],
           'camera_clearance': 0.075, 'transition_seconds': 0.2, 'movement_threshold': 0.05,
           'contact_metres': 0.001, 'contact_degrees': 0.2,
           'clips': {name: name for name in specs},
           'grips': [{'name': 'primary', 'target': 'grip_primary', 'hand': 'hand_primary'}, {'name': 'secondary', 'target': 'grip_secondary', 'hand': 'hand_secondary'}],
           'inertia': {'enabled': False, 'frequency_hz': 4, 'lag_seconds': 0.016, 'max_yaw_degrees': 1.5, 'max_pitch_degrees': 1.0, 'metres_per_degree': 0.0015}}
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
scene.render.engine = 'CYCLES';scene.cycles.device = 'CPU';scene.cycles.samples = 16 if '--draft' in sys.argv else 128;scene.cycles.use_denoising = False
scene.render.resolution_x = 960;scene.render.resolution_y = 540;scene.render.resolution_percentage = 100
scene.render.film_transparent = False;scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(ART / (STEM + '.blender-preview.png'))
arm.show_in_front = True
from bpy_extras.object_utils import world_to_camera_view
bpy.context.view_layer.update()
framing = {'variant': VARIANT, 'vertical_fov_degrees': 70, 'aspect': 16 / 9,
           'camera_offset_metres': CAMERA_OFFSET, 'roll_degrees': 0, 'bounds_top_left_normalized': {},
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
# Blender's real perspective camera supplies independent golden screen bounds.
# Keep unclipped NDC bounds separate from the visible/clipped framing above.
reference['camera_diagnostics'] = {'joints': {}, 'projections': []}
for name, point in reference['clips']['idle'][0]['joints'].items():
    reference['camera_diagnostics']['joints'][name] = list((model_to_view @ Vector((*point, 1))).xyz)
for fov in (70, 90, 110):
    camera.data.lens = 12 / math.tan(math.radians(fov) / 2)
    for width, height in ((960, 720), (960, 540), (1260, 540)):
        scene.render.resolution_x, scene.render.resolution_y = width, height
        bpy.context.view_layer.update()
        bounds = {}
        for obj in hand_meshes:
            if not obj.name.startswith('view_arm_'):
                continue
            evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh = evaluated.to_mesh()
            points = [world_to_camera_view(scene, camera, evaluated.matrix_world @ v.co) for v in mesh.vertices]
            bounds[obj.name] = [min(p.x * 2 - 1 for p in points), min(p.y * 2 - 1 for p in points),
                                max(p.x * 2 - 1 for p in points), max(p.y * 2 - 1 for p in points),
                                min(p.z for p in points), max(p.z for p in points)]
            evaluated.to_mesh_clear()
        reference['camera_diagnostics']['projections'].append({'fov': fov, 'aspect': width / height, 'bounds': bounds})
scene.render.resolution_x, scene.render.resolution_y = 960, 540
camera.data.lens = 12 / math.tan(math.radians(70) / 2)
(ART / (STEM + '.reference.json')).write_text(json.dumps(reference, indent=2) + '\n')
bpy.ops.wm.save_as_mainfile(filepath=str(ART / (STEM + '.blend')))
bpy.ops.render.render(write_still=True)
print('FIRST_PERSON_EXPORTED', export)
print('CLIPS', [(a.get('name'), len(a['channels'])) for a in doc.get('animations', [])])
print('HAND_OBJECT_INTERSECTIONS', sum(bool(i['pairs']) for i in reference['intersections']))
