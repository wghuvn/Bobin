"""Author only the held gauge + rigid native-arm anchors; GLB contains NO hands.

The .blend preview includes plain vanilla-sized cuboids for camera calibration.
Minecraft renders its own PlayerEntityModel ModelParts, not those proxy meshes.
blender -b -t 4 --python scripts/armament/export_vanilla_first_person_fixture.py -- --variant default
"""
import bpy
import bmesh
import json
import math
import struct
import sys
from pathlib import Path
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
ASSETS = ROOT / 'src/main/resources/assets/bobin_craft/armament/debug'
sys.path.insert(0, str(Path(__file__).parent))
from ergonomic_calibration import create_gauge
from first_person_animation_poses import CLIP_POSES, authoring_metadata

VARIANT = sys.argv[sys.argv.index('--variant') + 1] if '--variant' in sys.argv else 'default'
assert VARIANT in ('default', 'slim')
STEM = 'vanilla_first_person_fixture' + ('_slim' if VARIANT == 'slim' else '')
WIDTH = (3 if VARIANT == 'slim' else 4) / 16
CAMERA_OFFSET = [.24, -.19, -.64]
C = Matrix(((1,0,0,0),(0,0,1,0),(0,-1,0,0),(0,0,0,1)))
CI = C.inverted()
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.render.fps = 192
scene.frame_start, scene.frame_end = 0, 768
scene.unit_settings.system = 'METRIC';scene.unit_settings.scale_length = 1
root = bpy.data.objects.new('presentation_root', None);scene.collection.objects.link(root)
# Bring the support back on the held gauge to fit a normal 12-pixel arm's reach.
# Keep the gauge farther from the lens so its rear block does not hide the hands.
gauge, objects, contacts = create_gauge(root, forward_support_y=.19)


def column_major(m):
    return [m[r][c] for c in range(4) for r in range(4)]


def empty(name, matrix):
    obj = bpy.data.objects.new(name, None);scene.collection.objects.link(obj)
    obj.parent = root;obj.matrix_local = matrix
    return obj


anchors = {};contact_objects = [];native = {}
for side in ('right', 'left'):
    # Rigid whole vanilla arms. +Y of the unposed ModelPart points shoulder->hand.
    y = Vector((-.45,.55,-.704)) if side == 'right' else Vector((.4,.51,-.76))
    y.normalize()
    normal = Vector((1,0,0)) if side == 'right' else Vector((0,-1,0))
    x = y.cross(normal).normalized()
    if x.x < 0: x.negate()
    z = x.cross(y).normalized()
    contact = C @ contacts[side]
    # Closest point of the existing vanilla end rectangle to the independent
    # gauge plane. Place that point at the target; never scale the arm cuboid.
    toward = -normal
    offset = x * (math.copysign(WIDTH / 2, toward.dot(x)) if abs(toward.dot(x)) > 1e-6 else 0)
    offset += z * (math.copysign(.125, toward.dot(z)) if abs(toward.dot(z)) > 1e-6 else 0)
    end = contact.translation - offset
    anchor = Matrix(((x.x,y.x,z.x,end.x),(x.y,y.y,z.y,end.y),(x.z,y.z,z.z,end.z),(0,0,0,1)))
    anchors[side] = empty('vanilla_arm_' + side, CI @ anchor @ C)
    slot = 'primary' if side == 'right' else 'secondary'
    contact_objects.append(empty('grip_' + slot, contacts[side]))
    hand_contact = contact.copy();hand_contact.translation = end + offset
    contact_objects.append(empty('hand_' + slot, CI @ hand_contact))
    native[side] = {'anchor': anchor, 'end_x': (-1 if side == 'right' else 1) * (.5 if VARIANT == 'slim' else 1) / 16}

actions = {}
for clip, keys in CLIP_POSES.items():
    root.animation_data_create();root.animation_data.use_nla = False
    action = bpy.data.actions.new(clip);root.animation_data.action = action;actions[clip] = action
    for seconds, location, rotation, _ in keys:
        root.location = location;root.rotation_euler = [math.radians(a) for a in rotation]
        root.keyframe_insert('location', frame=seconds*192);root.keyframe_insert('rotation_euler', frame=seconds*192)
    for curve in action.fcurves:
        for key in curve.keyframe_points:
            key.interpolation='BEZIER';key.handle_left_type=key.handle_right_type='AUTO_CLAMPED'
    root.animation_data.action = None
    track=root.animation_data.nla_tracks.new();track.name=clip;track.strips.new(clip,0,action)
root.location=(0,0,0);root.rotation_euler=(0,0,0);scene.frame_set(0);bpy.context.view_layer.update()
bpy.ops.object.select_all(action='DESELECT')
for obj in [root,gauge,*objects,*anchors.values(),*contact_objects]:obj.select_set(True)
bpy.context.view_layer.objects.active=root
path=ASSETS/(STEM+'.glb')
bpy.ops.export_scene.gltf(filepath=str(path),export_format='GLB',use_selection=True,export_yup=True,
    export_animations=True,export_skins=False,export_animation_mode='ACTIONS',export_force_sampling=True,
    export_optimize_animation_size=False,export_morph=False,export_extras=False)
raw=path.read_bytes();doc=json.loads(raw[20:20+struct.unpack_from('<I',raw,12)[0]])
assert len(doc['meshes'])==7 and not doc.get('skins')
assert {a['name'] for a in doc['animations']}==set(CLIP_POSES)
parents={child:i for i,node in enumerate(doc['nodes']) for child in node.get('children',[])}
rig_id='bobin_craft:debug/'+STEM+'_rig'
rig={'schema_version':1,'id':rig_id,'nodes':[],'roles':{'weapon_root':'presentation_root'}}
for i,node in enumerate(doc['nodes']):
    item={'name':node['name'],'node_index':i}
    if i in parents:item['parent']=doc['nodes'][parents[i]]['name']
    rig['nodes'].append(item)
rotation=C.to_quaternion()
sockets={'schema_version':1,'id':'bobin_craft:debug/'+STEM+'_sockets','rig_id':rig_id,'sockets':[
    {'name':name,'node':name,'transform':{'translation':[0,0,0],'rotation':[rotation.x,rotation.y,rotation.z,rotation.w],'scale':[1,1,1]}}
    for name in ('grip_primary','grip_secondary','hand_primary','hand_secondary')]}
profile={'schema_version':1,'id':'bobin_craft:debug/'+STEM,'model':'bobin_craft:armament/debug/'+STEM+'.glb',
    'rig':'bobin_craft:armament/debug/'+STEM+'.rig.json','sockets':'bobin_craft:armament/debug/'+STEM+'.sockets.json',
    'camera_offset':CAMERA_OFFSET,'camera_rotation':[0,0,0,1],'camera_clearance':.075,'transition_seconds':.2,
    'movement_threshold':.05,'contact_metres':.001,'contact_degrees':.2,'clips':{k:k for k in CLIP_POSES},
    'grips':[{'name':'primary','target':'grip_primary','hand':'hand_primary'},{'name':'secondary','target':'grip_secondary','hand':'hand_secondary'}],
    'inertia':{'enabled':False,'frequency_hz':4,'lag_seconds':.016,'max_yaw_degrees':1.5,'max_pitch_degrees':1,'metres_per_degree':.0015}}
for kind,data in [('rig',rig),('sockets',sockets),('profile',profile)]:
    (ASSETS/(STEM+'.'+kind+'.json')).write_text(json.dumps(data,indent=2)+'\n')

# Preview proxies use the exact vanilla local bounds/64x64 cuboid net.
# They are created AFTER GLB export, so can never become runtime hand geometry.
image=bpy.data.images.load(str(ART/('diagnostic_player_skin'+('_slim' if VARIANT=='slim' else '')+'_64.png')))
image.pack()
materials={}
for sleeve in (False,True):
    m=bpy.data.materials.new('preview_vanilla_sleeve' if sleeve else 'preview_vanilla_skin');m.use_nodes=True
    p=m.node_tree.nodes['Principled BSDF'];p.inputs['Roughness'].default_value=.9
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
    m.node_tree.links.new(tex.outputs['Color'],p.inputs['Base Color'])
    if sleeve:
        m.node_tree.links.new(tex.outputs['Alpha'],p.inputs['Alpha']);m.surface_render_method='DITHERED'
    materials[sleeve]=m
proxies=[]
for side in ('right','left'):
    end_x=native[side]['end_x']
    for sleeve in (False,True):
        d=1/64 if sleeve else 0
        lo=Vector((end_x-WIDTH/2-d,-.125-d,-.125-d));hi=Vector((end_x+WIDTH/2+d,.625+d,.125+d))
        points=[Vector((x,y,z)) for x,y,z in [(lo.x,lo.y,lo.z),(hi.x,lo.y,lo.z),(hi.x,hi.y,lo.z),(lo.x,hi.y,lo.z),
                                             (lo.x,lo.y,hi.z),(hi.x,lo.y,hi.z),(hi.x,hi.y,hi.z),(lo.x,hi.y,hi.z)]]
        faces=[(0,1,2,3),(5,4,7,6),(4,0,3,7),(1,5,6,2),(4,5,1,0),(3,2,6,7)]
        local=[(CI @ Vector((*(p-Vector((end_x,.625,0))),1))).xyz for p in points]
        mesh=bpy.data.meshes.new('preview_cube');mesh.from_pydata(local,[],faces);mesh.update()
        layer=mesh.uv_layers.new(name='vanilla_64x64')
        u,v=((40,32) if sleeve else (40,16)) if side=='right' else ((48,48) if sleeve else (32,48))
        w=(3 if VARIANT=='slim' else 4)
        # Six standard cuboid islands; runtime UVs come from the actual Minecraft model.
        rects=[(u+4,v+4,w,12),(u+8+w,v+4,w,12),(u,v+4,4,12),(u+4+w,v+4,4,12),(u+4,v,w,4),(u+4+w,v,w,4)]
        for polygon,(a,b,c,dv) in zip(mesh.polygons,rects):
            for loop,uv in zip(polygon.loop_indices,[(a,b),(a+c,b),(a+c,b+dv),(a,b+dv)]):layer.data[loop].uv=(uv[0]/64,1-uv[1]/64)
        bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=bm.faces);bm.to_mesh(mesh);bm.free()
        obj=bpy.data.objects.new('vanilla_reference_'+side+('_sleeve' if sleeve else '_arm'),mesh);scene.collection.objects.link(obj)
        obj.parent=anchors[side];obj.data.materials.append(materials[sleeve]);proxies.append(obj)

metadata = authoring_metadata()
metadata['interpolation'] = 'AUTO_CLAMPED root poses, 192 Hz GLB bake; rigid native Minecraft ModelParts'
for clip_metadata in metadata['clips'].values():
    clip_metadata['keys'] = [key[:3] for key in clip_metadata['keys']]  # no elbow/finger channels
reference={'variant':VARIANT,'runtime_hands':'Minecraft PlayerEntityModel ModelParts','glb_meshes':7,'glb_skins':0,
           'camera_offset':CAMERA_OFFSET,'arm_pixels':[3 if VARIANT=='slim' else 4,12,4],
           'preview_only_geometry':True,'animation_authoring':metadata,'clips':{}}
for track in root.animation_data.nla_tracks:track.mute=True
for clip,action in actions.items():
    root.animation_data.action=action
    samples=[]
    for fraction in (0,.125,.25,.375,.5,.625,.75,.875,1):
        seconds=CLIP_POSES[clip][-1][0]*fraction;f=seconds*192;scene.frame_set(int(f),subframe=f-int(f));deps=bpy.context.evaluated_depsgraph_get()
        sample={'seconds':seconds,'anchors':{},'proxy_corners':{},'gauge_vertices':{}}
        for side,obj in anchors.items():sample['anchors'][side]=column_major(C @ obj.evaluated_get(deps).matrix_world @ CI)
        for obj in objects+proxies:
            ev=obj.evaluated_get(deps);mesh=ev.to_mesh();points=[(C @ (ev.matrix_world @ v.co).to_4d()).xyz for v in mesh.vertices]
            sample['proxy_corners' if obj in proxies else 'gauge_vertices'][obj.name]=[list(p) for p in points];ev.to_mesh_clear()
        samples.append(sample)
    reference['clips'][clip]=samples
(ART/(STEM+'.reference.json')).write_text(json.dumps(reference,indent=2)+'\n')
root.animation_data.action=None;root.animation_data.use_nla=True
for track in root.animation_data.nla_tracks:track.mute=track.name!='idle'
scene.frame_set(0)
bpy.ops.object.camera_add();camera=bpy.context.object
camera.matrix_world=CI @ Matrix.Translation(CAMERA_OFFSET).inverted();scene.camera=camera
camera.data.sensor_fit='VERTICAL';camera.data.sensor_height=24
for location,energy in [((1,-2,3),350),((-2,-1,1),180)]:
    bpy.ops.object.light_add(type='AREA',location=location);light=bpy.context.object;light.data.energy=energy;light.data.size=4
    light.rotation_euler=(Vector((0,0,.15))-light.location).to_track_quat('-Z','Y').to_euler()
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=16;scene.cycles.use_denoising=False
scene.render.resolution_x=960;scene.render.resolution_y=540;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
camera.data.lens=12/math.tan(math.radians(70)/2)
bpy.ops.wm.save_as_mainfile(filepath=str(ART/(STEM+'.blend')))
for fov in (70,90,110):
    camera.data.lens=12/math.tan(math.radians(fov)/2)
    scene.render.filepath=str(ART/(STEM+'.fov'+str(fov)+'.png'));bpy.ops.render.render(write_still=True)
print('VANILLA_GAUGE_ONLY_EXPORTED',path,'meshes',len(doc['meshes']),'skins',len(doc.get('skins',[])))
