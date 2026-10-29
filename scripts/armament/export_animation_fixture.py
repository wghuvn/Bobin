"""Original Blender rig, real exported clips and independent evaluated-pose golden data.
Run: blender -b --python scripts/armament/export_animation_fixture.py
No weapon geometry; no Java-generated runtime motion or hand-edited GLB animation.
"""
import bpy
import json
import math
import struct
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
ASSETS = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug'
ART.mkdir(parents=True, exist_ok=True)
ASSETS.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1
scene.render.fps = 24
scene.frame_start, scene.frame_end = 0, 48


def empty(name, parent=None, location=(0, 0, 0)):
    obj = bpy.data.objects.new(name, None)
    scene.collection.objects.link(obj)
    obj.parent, obj.location = parent, location
    return obj


root = empty('fixture_root', location=(0.1, -0.05, 0))
texture = bpy.data.images.load(str(ART / 'uv_checker.png'))
texture.pack()


def material(name, color, textured=False):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = 0
    bsdf.inputs['Roughness'].default_value = 0.8
    if textured:
        node = mat.node_tree.nodes.new('ShaderNodeTexImage')
        node.image = texture
        mat.node_tree.links.new(node.outputs['Color'], bsdf.inputs['Base Color'])
    return mat


checker = material('animation_checker', (1, 1, 1), True)
blue = material('fixed_blue', (0.08, 0.25, 0.8))
orange = material('moving_orange', (1, 0.35, 0.04))


def finish(obj, name, parent, mat):
    obj.name = name
    obj.parent = parent
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(island_margin=0.025)
    bpy.ops.object.mode_set(mode='OBJECT')
    return obj


bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.65, depth=0.08, location=(0, 0, 0.04))
base = finish(bpy.context.object, 'fixed_base', root, blue)
rotor = empty('rotor', root, (-0.36, -0.06, 0.37))
rotor.rotation_mode = 'XYZ'
bpy.ops.mesh.primitive_cone_add(vertices=3, radius1=0.24, radius2=0.08, depth=0.09, location=(0, 0, 0))
pointer = finish(bpy.context.object, 'rotor_pointer', rotor, checker)
pointer.location = (0, 0, 0)
socket = empty('socket_debug', rotor, (0.22, 0.02, 0.1))
bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.1, location=(0.25, -0.22, 0.18))
slider = finish(bpy.context.object, 'slider', root, orange)
slider_rest = slider.location.copy()

# Three connected Blender bones, with four-influence glTF streams (two nonzero here).
armature_data = bpy.data.armatures.new('technical_rig')
armature = bpy.data.objects.new('armature', armature_data)
scene.collection.objects.link(armature)
armature.parent, armature.location = root, (0, 0.16, 0.14)
bpy.context.view_layer.objects.active = armature
armature.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
bone_names = ['joint_root', 'joint_mid', 'joint_tip']
previous = None
for i, name in enumerate(bone_names):
    bone = armature_data.edit_bones.new(name)
    bone.head, bone.tail = (0, 0, i * 0.3), (0, 0, (i + 1) * 0.3)
    if previous:
        bone.parent, bone.use_connect = previous, True
    previous = bone
bpy.ops.object.mode_set(mode='OBJECT')
for bone in armature.pose.bones:
    bone.rotation_mode = 'XYZ'

verts, faces = [], []
for row in range(10):
    for x, y in [(-0.06, -0.04), (0.06, -0.04), (0.06, 0.04), (-0.06, 0.04)]:
        verts.append((x, y, row * 0.1))
for row in range(9):
    for side in range(4):
        a, b = row * 4 + side, row * 4 + (side + 1) % 4
        faces.append((a, b, b + 4, a + 4))
faces += [(3, 2, 1, 0), (36, 37, 38, 39)]
mesh_data = bpy.data.meshes.new('deforming_column_mesh')
mesh_data.from_pydata(verts, [], faces)
mesh_data.update()
column = bpy.data.objects.new('deforming_column', mesh_data)
scene.collection.objects.link(column)
column.parent, column.location, column.scale = armature, (0, 0, 0), (1.1, 0.9, 1)
bpy.context.view_layer.objects.active = column
column.select_set(True)
finish(column, column.name, armature, checker)
groups = [column.vertex_groups.new(name=name) for name in bone_names]
for v in column.data.vertices:
    position = max(0, min(2, (v.co.z - 0.15) / 0.3))
    lower, upper = int(math.floor(position)), int(math.ceil(position))
    if lower == upper:
        groups[lower].add([v.index], 1, 'REPLACE')
    else:
        groups[lower].add([v.index], 1 - (position - lower), 'REPLACE')
        groups[upper].add([v.index], position - lower, 'REPLACE')
modifier = column.modifiers.new('native_bone_deformation', 'ARMATURE')
modifier.object = armature
modifier.use_deform_preserve_volume = False  # Linear blend skinning, matching glTF.

animated = [rotor, slider, armature]
clips = ['spin', 'slide', 'curve', 'bend', 'combo']
actions = {}


def reset_pose():
    rotor.rotation_euler = (0, 0, 0)
    slider.location = slider_rest
    for bone in armature.pose.bones:
        bone.rotation_euler = (0, 0, 0)


for clip in clips:
    actions[clip] = {}
    for obj in animated:
        obj.animation_data_create()
        action = bpy.data.actions.new(clip + '__' + obj.name)
        obj.animation_data.action = action
        actions[clip][obj.name] = action
    for frame, fraction in [(0, 0), (12, 0.5), (24, 1), (36, 0.5), (48, 0)]:
        reset_pose()
        if clip in ('spin', 'combo'):
            rotor.rotation_euler.z = frame / 48 * 2 * math.pi
        if clip in ('slide', 'curve', 'combo'):
            slider.location.x += fraction * 0.35
        if clip in ('bend', 'combo'):
            for i, name in enumerate(bone_names):
                armature.pose.bones[name].rotation_euler.z = fraction * math.radians([10, 35, -45][i])
        rotor.keyframe_insert('rotation_euler', frame=frame)
        slider.keyframe_insert('location', frame=frame)
        for name in bone_names:
            armature.pose.bones[name].keyframe_insert('rotation_euler', frame=frame)
    for obj in animated:
        action = actions[clip][obj.name]
        for curve in action.fcurves:
            for key in curve.keyframe_points:
                key.interpolation = 'CONSTANT' if clip == 'slide' and obj == slider else 'LINEAR'
                if clip == 'curve' and obj == slider:
                    key.interpolation = 'BEZIER'
                    key.handle_left_type = key.handle_right_type = 'FREE'
                    key.handle_left = (key.co.x - 4, key.co.y)
                    key.handle_right = (key.co.x + 4, key.co.y)
        obj.animation_data.action = None
        track = obj.animation_data.nla_tracks.new()
        track.name = clip
        track.strips.new(clip, 0, action)

reset_pose()
scene.frame_set(0)
bpy.ops.object.select_all(action='DESELECT')
for obj in scene.objects:
    if obj.type in {'MESH', 'EMPTY', 'ARMATURE'}:
        obj.select_set(True)
bpy.context.view_layer.objects.active = root
export_file = ASSETS / 'animation_fixture.glb'
bpy.ops.export_scene.gltf(filepath=str(export_file), export_format='GLB', use_selection=True,
    export_yup=True, export_texcoords=True, export_normals=True, export_materials='EXPORT',
    export_animations=True, export_skins=True, export_animation_mode='ACTIONS',
    export_force_sampling=False, export_anim_slide_to_zero=True, export_optimize_animation_size=False,
    export_morph=False, export_extras=False)

# Independent reference: evaluated Blender matrices and Armature modifier output, not GLB decoding.
C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))


def column_major(matrix):
    return [matrix[r][c] for c in range(4) for r in range(4)]


reference = {'generator': bpy.app.version_string, 'seconds_per_frame': 1 / 24,
             'coordinates': '(x,y,z) -> (x,z,-y), metres', 'clips': {}}
for obj in animated:
    for track in obj.animation_data.nla_tracks:
        track.mute = True
for clip in clips:
    for obj in animated:
        obj.animation_data.action = actions[clip][obj.name]
    samples = []
    for seconds in (0, 0.1375, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.875, 2):
        position = seconds * 24
        scene.frame_set(int(position), subframe=position - int(position))
        depsgraph = bpy.context.evaluated_depsgraph_get()
        sample = {'seconds': seconds, 'nodes': {}, 'vertices': {}, 'joint_matrices': {}}
        for obj in (root, rotor, pointer, slider, socket, armature, column):
            matrix = obj.evaluated_get(depsgraph).matrix_world
            sample['nodes'][obj.name] = column_major(C @ matrix @ C.inverted())
        for obj in (base, pointer, slider, column):
            evaluated = obj.evaluated_get(depsgraph)
            data = evaluated.to_mesh()
            sample['vertices'][obj.name] = [list((C @ evaluated.matrix_world @ vertex.co.to_4d()).xyz) for vertex in data.vertices]
            evaluated.to_mesh_clear()
        mesh_world = column.evaluated_get(depsgraph).matrix_world
        arm_world = armature.evaluated_get(depsgraph).matrix_world
        for bone in armature.pose.bones:
            # Blender exports this skin's positions in world bind coordinates. Compare the
            # world-space deformation operator, not the original object's rebased local space.
            skin_matrix = arm_world @ bone.matrix @ bone.bone.matrix_local.inverted() @ arm_world.inverted()
            sample['joint_matrices'][bone.name] = column_major(C @ skin_matrix @ C.inverted())
        samples.append(sample)
    reference['clips'][clip] = samples
(ART / 'animation_fixture.reference.json').write_text(json.dumps(reference, indent=2) + '\n')

# Schema-compatible sidecars; indices come from the actual export, not assumed Blender ordering.
raw = export_file.read_bytes()
document = json.loads(raw[20:20 + struct.unpack_from('<I', raw, 12)[0]])
parents = {child: i for i, node in enumerate(document['nodes']) for child in node.get('children', [])}
rig_id = 'bobin_craft:debug/animation_fixture_rig'
rig = {'schema_version': 1, 'id': rig_id, 'nodes': [], 'roles': {}}
for i, node in enumerate(document['nodes']):
    entry = {'name': node['name'], 'node_index': i}
    if i in parents:
        entry['parent'] = document['nodes'][parents[i]]['name']
    rig['nodes'].append(entry)
rig['roles']['weapon_root'] = 'fixture_root'  # Existing generic schema's required root role only.
sockets = {'schema_version': 1, 'id': 'bobin_craft:debug/animation_fixture_sockets', 'rig_id': rig_id,
           'sockets': [{'name': 'debug', 'node': 'socket_debug',
                        'transform': {'translation': [0, 0, 0], 'rotation': [0, 0, 0, 1], 'scale': [1, 1, 1]}}]}
events = {'schema_version': 1, 'id': 'bobin_craft:debug/animation_fixture_events', 'clips': []}
for clip in clips:
    events['clips'].append({'name': clip, 'duration_seconds': 2,
        'markers': [{'name': 'start', 'kind': 'cosmetic', 'time_seconds': 0, 'event': 'bobin_craft:debug/presentation'},
                    {'name': 'midpoint', 'kind': 'cosmetic', 'time_seconds': 1, 'event': 'bobin_craft:debug/contact'},
                    {'name': 'end', 'kind': 'cosmetic', 'time_seconds': 2, 'event': 'bobin_craft:debug/presentation'}]})
for suffix, data in [('rig', rig), ('sockets', sockets), ('events', events)]:
    (ASSETS / ('animation_fixture.' + suffix + '.json')).write_text(json.dumps(data, indent=2) + '\n')

# Save an editable posed scene; all actions and NLA tracks remain available.
for obj in animated:
    obj.animation_data.action = None
    for track in obj.animation_data.nla_tracks:
        track.mute = track.name != 'combo'
scene.frame_set(24)
armature.show_in_front = True
bpy.ops.wm.save_as_mainfile(filepath=str(ART / 'animation_fixture.blend'))
print('ANIMATION_FIXTURE_EXPORTED', export_file)
print('CLIPS', [(a.get('name'), len(a['channels'])) for a in document.get('animations', [])])
print('INTERPOLATION', sorted({s.get('interpolation', 'LINEAR') for a in document.get('animations', []) for s in a['samplers']}))
