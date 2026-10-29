"""Run with Blender 4.3+: blender -b --python scripts/armament/export_glb_fixture.py.
Creates original technical geometry, UVs, packed texture, .blend, GLB and reference data.
Not weapon geometry. All runtime mesh data must be read from this exported GLB.
"""
import bpy
import math
import json
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
EXPORT = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug/technical_fixture.glb'
ART.mkdir(parents=True, exist_ok=True)
EXPORT.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1.0
root = bpy.data.objects.new('FixtureRoot', None)
scene.collection.objects.link(root)

image = bpy.data.images.new('UV_orientation_checker', width=64, height=64, alpha=True)
pixels = []
for y in range(64):
    for x in range(64):
        checker = 0.6 if (x // 8 + y // 8) % 2 else 1.0
        rgb = ((0.9, 0.1, 0.7) if x < 32 else (0.1, 0.2, 0.5)) if y < 32 else ((1.0, 0.9, 0.3) if x < 32 else (0.1, 0.9, 0.9))
        if y >= 60: rgb = (1, 0, 0)
        if x < 4: rgb = (0, 1, 0)
        pixels.extend([*(v * checker for v in rgb), 1.0])
image.pixels = pixels
image.filepath_raw = str(ART / 'uv_checker.png')
image.file_format = 'PNG'
image.save()
image.pack()

def material(name, color, textured=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = 0
    bsdf.inputs['Roughness'].default_value = 0.8
    if textured:
        tex = m.node_tree.nodes.new('ShaderNodeTexImage')
        tex.image = image
        tex.interpolation = 'Closest'
        m.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    return m

checker = material('UVChecker', (1,1,1), True)
blue = material('BlueMatte', (0.08,0.2,0.8))
yellow = material('YellowMatte', (0.9,0.65,0.03))
red = material('ForwardIndicator', (0.8,0.06,0.03))

def finish(obj, name, mat, parent=root):
    obj.name = name
    obj.parent = parent
    obj.data.materials.append(mat)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.035)
    bpy.ops.object.mode_set(mode='OBJECT')
    return obj

bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.5, depth=0.12, location=(0,0,0.06))
finish(bpy.context.object, 'OctagonalBase_1m', checker)
bpy.ops.mesh.primitive_cylinder_add(vertices=6, radius=0.12, depth=0.68, location=(0,0,0.46))
finish(bpy.context.object, 'HexagonalPillar', blue)
bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.2, location=(0,0,0.8))
finish(bpy.context.object, 'FacetedCap', yellow)
assembly = bpy.data.objects.new('NestedAssembly', None)
scene.collection.objects.link(assembly)
assembly.parent = root
assembly.location = (0.14,-0.04,0.45)
assembly.rotation_euler = (0,0,math.radians(22))
assembly.scale = (0.9,1.1,1.0)
bpy.ops.mesh.primitive_torus_add(major_segments=12, minor_segments=4, major_radius=0.24, minor_radius=0.045)
ring = finish(bpy.context.object, 'NestedPolygonRing', checker, assembly)
ring.rotation_euler = (math.pi/2, 0, 0)
# Triangular prism pointing along Blender +Y -> exported glTF -Z (Minecraft north).
mesh = bpy.data.meshes.new('IndicatorPrismMesh')
mesh.from_pydata([(-.12,.20,.12),(.12,.20,.12),(0,.48,.12),(-.12,.20,.18),(.12,.20,.18),(0,.48,.18)], [],
                 [(2,1,0),(3,4,5),(0,1,4,3),(1,2,5,4),(2,0,3,5)])
mesh.update()
arrow = bpy.data.objects.new('ForwardMinusZ', mesh)
scene.collection.objects.link(arrow)
finish(arrow, 'ForwardMinusZ', red)
bpy.context.view_layer.update()
# Independent reference computed by Blender from its scene, not the Java loader.
def convert(v): return [float(v.x), float(v.z), float(-v.y)]
reference = {'blender_version':bpy.app.version_string, 'metres_per_blender_unit':1.0,
             'minecraft_blocks_per_metre':1.0, 'axis_mapping':'Blender (x,y,z) -> glTF/Minecraft model (x,z,-y)', 'nodes':{}}
for obj in scene.objects:
    if obj.type != 'MESH': continue
    points = [convert(obj.matrix_world @ v.co) for v in obj.data.vertices]
    reference['nodes'][obj.name] = {'vertices':points,
        'min':[min(v[i] for v in points) for i in range(3)], 'max':[max(v[i] for v in points) for i in range(3)]}
(ART / 'technical_fixture.reference.json').write_text(json.dumps(reference, indent=2)+'\n')
bpy.ops.object.select_all(action='DESELECT')
root.select_set(True)
for obj in scene.objects:
    if obj.type in {'MESH','EMPTY'}: obj.select_set(True)
bpy.context.view_layer.objects.active = root
bpy.ops.export_scene.gltf(filepath=str(EXPORT), export_format='GLB', use_selection=True,
    export_yup=True, export_texcoords=True, export_normals=True, export_materials='EXPORT',
    export_animations=False, export_skins=False, export_extras=False)
# Render an authoring reference; this is NOT a Minecraft screenshot.
bpy.ops.object.camera_add(location=(2.0,-2.6,1.8))
camera = bpy.context.object
camera.rotation_euler = (Vector((0,0,0.45))-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 1.8
scene.camera = camera
for position,energy,size in [((2,-3,4),250,4),((-3,1,2),150,3)]:
    bpy.ops.object.light_add(type='AREA', location=position)
    light=bpy.context.object
    light.data.energy=energy;light.data.shape='DISK';light.data.size=size
    light.rotation_euler=(Vector((0,0,0.4))-light.location).to_track_quat('-Z','Y').to_euler()
scene.render.engine='CYCLES'
scene.cycles.device='CPU';scene.cycles.samples=16;scene.cycles.use_denoising=False
scene.render.resolution_x=512;scene.render.resolution_y=512;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=True
scene.render.filepath=str(ART/'technical_fixture.blender-preview.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ART/'technical_fixture.blend'))
bpy.ops.render.render(write_still=True)
print('ARMAMENT_FIXTURE_EXPORTED',EXPORT)
