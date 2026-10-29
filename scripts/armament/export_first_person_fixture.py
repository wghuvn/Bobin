"""Blender authoring/export for the first-person technical assembly.
Imports the existing stage-3 GLB unchanged in metres, adds two skinned arms and
artist-keyed clips. No weapon geometry, no Java-authored replacement animation.
Run: blender -b --python scripts/armament/export_first_person_fixture.py
"""
import bpy
import bmesh
import json
import math
import re
import struct
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
ASSETS = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug'
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

root = bpy.data.objects.new('presentation_root', None)
scene.collection.objects.link(root)
root.rotation_mode = 'XYZ'
technical_root.parent = root
arm_data = bpy.data.armatures.new('view_hand_skeleton')
arm = bpy.data.objects.new('view_hands_rig', arm_data)
scene.collection.objects.link(arm)
arm.parent = root
bpy.context.view_layer.update()

skin = bpy.data.materials.new('technical_hand_surface')
skin.use_nodes = True
shader = skin.node_tree.nodes['Principled BSDF']
shader.inputs['Base Color'].default_value = (0.42, 0.34, 0.28, 1)
shader.inputs['Metallic'].default_value = 0
shader.inputs['Roughness'].default_value = 0.9

# Select the actual imported front plate faces; contact geometry is not guessed
# from a Java offset or reconstructed as a Minecraft cube.
arms = {}
for side, sign in [('right', 1), ('left', -1)]:
    wanted = Vector((sign * 0.383, -0.924, 0))
    faces = [p for p in base.data.polygons if abs(p.normal.z) < 0.1]
    face = max(faces, key=lambda p: (base.matrix_world.to_3x3() @ p.normal).dot(wanted))
    center = base.matrix_world @ face.center
    outward = (base.matrix_world.to_3x3() @ face.normal).normalized()
    forward = -outward
    lateral = forward.cross(Vector((0, 0, 1))).normalized()
    edge = Vector((center.x, center.y, 0))
    wrist = edge - forward * 0.10 + Vector((0, 0, -0.028))
    arms[side] = {'side': side, 'sign': sign, 'forward': forward, 'lateral': lateral,
                  'edge': edge, 'shoulder': Vector((sign * 0.22, -0.89, 0.10)),
                  'elbow': Vector((sign * 0.38, -0.73, -0.07)), 'wrist': wrist}

bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode='EDIT')
for side, data in arms.items():
    upper = arm_data.edit_bones.new('upper_' + side)
    upper.head, upper.tail = data['shoulder'], data['elbow']
    lower = arm_data.edit_bones.new('forearm_' + side)
    lower.head, lower.tail, lower.parent, lower.use_connect = data['elbow'], data['wrist'], upper, True
    hand = arm_data.edit_bones.new('hand_' + side)
    hand.head, hand.tail, hand.parent, hand.use_connect = data['wrist'], data['wrist'] + data['forward'] * 0.085, lower, True
    hand.align_roll(Vector((0, 0, 1)))
    data['digits'] = []
    for finger, width in [('thumb', -0.041), ('index', -0.023), ('middle', -0.002), ('ring', 0.019), ('little', 0.037)]:
        # All four fingers curl along the actual plate face, outside its surface.
        # Thumb is shorter and angled laterally; this is an original technical hand.
        lateral_offset = data['lateral'] * width
        if side == 'left':
            lateral_offset = -lateral_offset
        h = 0.82 if finger in ('thumb', 'little') else 1.0
        p = [data['edge'] + lateral_offset - data['forward'] * 0.018 + Vector((0, 0, -0.02)),
             data['edge'] + lateral_offset - data['forward'] * 0.023 + Vector((0, 0, 0.025 * h)),
             data['edge'] + lateral_offset - data['forward'] * 0.021 + Vector((0, 0, 0.065 * h)),
             data['edge'] + lateral_offset - data['forward'] * 0.015 + Vector((0, 0, 0.085 * h))]
        if finger == 'thumb':
            direction = -1 if side == 'right' else 1
            p = [data['wrist'] + data['forward'] * 0.035 + data['lateral'] * (direction * 0.047),
                 data['edge'] - data['forward'] * 0.035 + data['lateral'] * (direction * 0.052) + Vector((0, 0, -0.008)),
                 data['edge'] - data['forward'] * 0.022 + data['lateral'] * (direction * 0.044) + Vector((0, 0, 0.026)),
                 data['edge'] - data['forward'] * 0.016 + data['lateral'] * (direction * 0.029) + Vector((0, 0, 0.05))]
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
    socket_objects.append(socket_object('grip_' + slot, technical_root, bind))
    hand_socket = bpy.data.objects.new('hand_' + slot, None)
    scene.collection.objects.link(hand_socket)
    hand_socket.parent, hand_socket.parent_type, hand_socket.parent_bone = arm, 'BONE', 'hand_' + side
    # Blender bone-parent objects use the bone tail as their parent origin.
    hand_socket.location = (0, -arm_data.bones['hand_' + side].length, 0)
    socket_objects.append(hand_socket)


def make_hand(data):
    vertices, faces, influences = [], [], []

    def tube(points, radii, weights, segments=10):
        start = len(vertices)
        for row, center in enumerate(points):
            tangent = points[min(row + 1, len(points) - 1)] - points[max(0, row - 1)]
            tangent.normalize()
            axis = tangent.cross(Vector((1, 0, 0)))
            if axis.length < 0.1:
                axis = tangent.cross(Vector((0, 0, 1)))
            axis.normalize()
            other = tangent.cross(axis).normalized()
            for k in range(segments):
                angle = k * 2 * math.pi / segments
                vertices.append(center + radii[row] * (axis * math.cos(angle) + other * math.sin(angle)))
                influences.append(weights[row])
        for row in range(len(points) - 1):
            for k in range(segments):
                a, b = start + row * segments + k, start + row * segments + (k + 1) % segments
                faces.append((a, b, b + segments, a + segments))
        faces.append(tuple(start + k for k in reversed(range(segments))))
        end = start + (len(points) - 1) * segments
        faces.append(tuple(end + k for k in range(segments)))

    side = data['side']
    upper, lower, hand = 'upper_' + side, 'forearm_' + side, 'hand_' + side
    points, radii, weights = [], [], []
    for i in range(5):
        t = i / 4
        points.append(data['shoulder'].lerp(data['elbow'], t))
        radii.append(0.057 * (1 - t) + 0.043 * t)
        weights.append({upper: 1} if i < 3 else {upper: 1 - 0.25 * (i - 2), lower: 0.25 * (i - 2)})
    for i in range(1, 7):
        t = i / 6
        points.append(data['elbow'].lerp(data['wrist'], t))
        radii.append(0.043 * (1 - t) + 0.025 * t)
        if i < 2:
            weights.append({upper: 0.2, lower: 0.8})
        elif i < 5:
            weights.append({lower: 1})
        else:
            weights.append({lower: 1 - (i - 4) * 0.3, hand: (i - 4) * 0.3})
    tube(points, radii, weights)
    # A flattened, rounded palm; its main axis follows the imported grip orientation.
    start = len(vertices)
    for row in range(7):
        theta = math.pi * (row + 0.1) / 6.2
        for col in range(12):
            phi = col * 2 * math.pi / 12
            center = data['wrist'] + data['forward'] * 0.043
            vertices.append(center + data['forward'] * (0.05 * math.cos(theta)) + data['lateral'] * (0.046 * math.sin(theta) * math.cos(phi)) + Vector((0, 0, 0.016 * math.sin(theta) * math.sin(phi))))
            influences.append({hand: 1})
    for row in range(6):
        for col in range(12):
            a, b = start + row * 12 + col, start + row * 12 + (col + 1) % 12
            faces.append((a, b, b + 12, a + 12))
    faces.append(tuple(start + k for k in reversed(range(12))))
    faces.append(tuple(start + 72 + k for k in range(12)))
    for finger, path, names in data['digits']:
        centers, radii, weights = [], [], []
        radius = 0.010 if finger != 'little' else 0.008
        for j in range(3):
            for k in range(3):
                t = k / 3
                centers.append(path[j].lerp(path[j + 1], t))
                radii.append(radius * (1 - (j + t) * 0.13))
                weights.append({names[j]: 1} if k else {names[max(0, j - 1)]: 0.25, names[j]: 0.75})
        centers.append(path[-1]);radii.append(radius * 0.35);weights.append({names[-1]: 1})
        tube(centers, radii, weights, 8)
    mesh = bpy.data.meshes.new('hand_surface_' + side)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    bm = bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm, faces=bm.faces);bm.to_mesh(mesh);bm.free()
    obj = bpy.data.objects.new('view_arm_' + side, mesh)
    scene.collection.objects.link(obj)
    obj.parent = arm
    obj.data.materials.append(skin)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    groups = {name: obj.vertex_groups.new(name=name) for name in arm_data.bones.keys()}
    for i, weight in enumerate(influences):
        # A repeated name at the first finger row denotes one influence, not 0.75 total.
        total = sum(weight.values())
        for name, value in weight.items():
            groups[name].add([i], value / total, 'REPLACE')
    modifier = obj.modifiers.new('Blender_linear_skinning', 'ARMATURE')
    modifier.object = arm
    modifier.use_deform_preserve_volume = False
    return obj


hand_meshes = [make_hand(data) for data in arms.values()]
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
export = ASSETS / 'first_person_fixture.glb'
bpy.ops.export_scene.gltf(filepath=str(export), export_format='GLB', use_selection=True,
    export_yup=True, export_normals=True, export_texcoords=True, export_materials='EXPORT',
    export_animations=True, export_skins=True, export_animation_mode='ACTIONS',
    export_force_sampling=True, export_optimize_animation_size=False, export_morph=False, export_extras=False)

C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))


def column_major(matrix):
    return [matrix[r][c] for c in range(4) for r in range(4)]


reference = {'blender': bpy.app.version_string, 'source_model': 'technical_fixture.glb',
             'metres_per_unit': 1, 'camera_offset': [0, -0.38, -1.15], 'clips': {}, 'intersections': []}
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
        bvhs = {}
        for obj in object_meshes + hand_meshes:
            evaluated = obj.evaluated_get(depsgraph);mesh = evaluated.to_mesh()
            world = evaluated.matrix_world
            points = [world @ v.co for v in mesh.vertices]
            sample['vertices'][obj.name] = [list((C @ p.to_4d()).xyz) for p in points]
            bvhs[obj.name] = BVHTree.FromPolygons(points, [list(p.vertices) for p in mesh.polygons], all_triangles=False, epsilon=0)
            evaluated.to_mesh_clear()
        for obj in socket_objects:
            sample['sockets'][obj.name] = column_major(C @ obj.evaluated_get(depsgraph).matrix_world @ C.inverted())
        intersections = {hand.name + '/' + obj.name: len(bvhs[hand.name].overlap(bvhs[obj.name])) for hand in hand_meshes for obj in object_meshes}
        reference['intersections'].append({'clip': clip, 'seconds': seconds, 'pairs': {k: v for k, v in intersections.items() if v}})
        samples.append(sample)
    reference['clips'][clip] = samples
(ART / 'first_person_fixture.reference.json').write_text(json.dumps(reference, indent=2) + '\n')

raw = export.read_bytes();doc = json.loads(raw[20:20 + struct.unpack_from('<I', raw, 12)[0]])
parents = {child: i for i, node in enumerate(doc['nodes']) for child in node.get('children', [])}
rig_id = 'bobin_craft:debug/first_person_rig'
rig = {'schema_version': 1, 'id': rig_id, 'nodes': [], 'roles': {'weapon_root': 'presentation_root'}}
for i, node in enumerate(doc['nodes']):
    entry = {'name': node['name'], 'node_index': i}
    if i in parents:entry['parent'] = doc['nodes'][parents[i]]['name']
    rig['nodes'].append(entry)
sockets = {'schema_version': 1, 'id': 'bobin_craft:debug/first_person_sockets', 'rig_id': rig_id, 'sockets': []}
for name in ('grip_primary', 'grip_secondary', 'hand_primary', 'hand_secondary'):
    sockets['sockets'].append({'name': name, 'node': name, 'transform': {'translation': [0, 0, 0], 'rotation': [0, 0, 0, 1], 'scale': [1, 1, 1]}})
profile = {'schema_version': 1, 'id': 'bobin_craft:debug/first_person',
           'model': 'bobin_craft:armament/debug/first_person_fixture.glb',
           'rig': 'bobin_craft:armament/debug/first_person_fixture.rig.json',
           'sockets': 'bobin_craft:armament/debug/first_person_fixture.sockets.json',
           'camera_offset': [0, -0.38, -1.15], 'camera_rotation': [0, 0, 0, 1],
           'camera_clearance': 0.075, 'transition_seconds': 0.2, 'movement_threshold': 0.05,
           'contact_metres': 0.001, 'contact_degrees': 0.2,
           'clips': {name: name for name in specs},
           'grips': [{'name': 'primary', 'target': 'grip_primary', 'hand': 'hand_primary'}, {'name': 'secondary', 'target': 'grip_secondary', 'hand': 'hand_secondary'}],
           'inertia': {'enabled': True, 'frequency_hz': 4, 'lag_seconds': 0.016, 'max_yaw_degrees': 1.5, 'max_pitch_degrees': 1.0, 'metres_per_degree': 0.0015}}
for kind, data in [('rig', rig), ('sockets', sockets), ('profile', profile)]:
    (ASSETS / ('first_person_fixture.' + kind + '.json')).write_text(json.dumps(data, indent=2) + '\n')

for obj in (root, arm):
    obj.animation_data.action = None
    obj.animation_data.use_nla = True
    for track in obj.animation_data.nla_tracks:
        track.mute = track.name != 'idle'
scene.frame_set(0)
bpy.ops.object.camera_add(location=(0, -1.15, 0.38))
camera = bpy.context.object
camera.rotation_euler = Vector((0, 1, 0)).to_track_quat('-Z', 'Y').to_euler()
camera.data.sensor_fit = 'VERTICAL';camera.data.sensor_height = 24;camera.data.lens = 12 / math.tan(math.radians(70) / 2)
scene.camera = camera
for location, energy, size in [((1, -2, 3), 350, 4), ((-2, -1, 1), 180, 3)]:
    bpy.ops.object.light_add(type='AREA', location=location)
    light = bpy.context.object;light.data.energy = energy;light.data.size = size
    light.rotation_euler = (Vector((0, 0, 0.25)) - light.location).to_track_quat('-Z', 'Y').to_euler()
scene.render.engine = 'CYCLES';scene.cycles.device = 'CPU';scene.cycles.samples = 16;scene.cycles.use_denoising = False
scene.render.resolution_x = 960;scene.render.resolution_y = 540;scene.render.resolution_percentage = 100
scene.render.film_transparent = False;scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(ART / 'first_person_fixture.blender-preview.png')
arm.show_in_front = True
bpy.ops.wm.save_as_mainfile(filepath=str(ART / 'first_person_fixture.blend'))
bpy.ops.render.render(write_still=True)
print('FIRST_PERSON_EXPORTED', export)
print('CLIPS', [(a.get('name'), len(a['channels'])) for a in doc.get('animations', [])])
print('HAND_OBJECT_INTERSECTIONS', sum(bool(i['pairs']) for i in reference['intersections']))
