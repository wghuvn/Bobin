"""Author separate FPS presentation clips and append ONLY their accessors/channels to the
approved binary GLB. Never re-export its geometry or existing animation data.
"""
import bpy,json,math,struct,sys,copy,hashlib,tempfile
from pathlib import Path
from mathutils import Matrix,Vector
sys.path.insert(0,str(Path(__file__).parent))
from first_person_motion_poses import CLIPS
from glb_rigid_scales import normalize_rigid_scales
ROOT=Path(__file__).resolve().parents[2];ART=ROOT/'art/armament';ASSETS=ROOT/'src/main/resources/assets/bobin_craft/armament/debug'
variant=sys.argv[sys.argv.index('--variant')+1] if '--variant' in sys.argv else 'default'
assert variant in ('default','slim')
suffix='_slim' if variant=='slim' else '';base='vanilla_first_person_fixture'+suffix;stem='vanilla_first_person_motion'+suffix

def unpack(path):
 raw=path.read_bytes();size=struct.unpack_from('<I',raw,12)[0];doc=json.loads(raw[20:20+size]);off=20+size
 n,kind=struct.unpack_from('<II',raw,off);assert kind==0x004e4942
 return doc,bytearray(raw[off+8:off+8+n])
def pack(path,doc,data):
 doc['buffers'][0]['byteLength']=len(data);data+=b'\0'*((-len(data))%4)
 j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*((-len(j))%4)
 path.write_bytes(struct.pack('<4sII',b'glTF',2,28+len(j)+len(data))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(data),0x004e4942)+data)
original_doc,original_bin=unpack(ASSETS/(base+'.glb'));baseline_sha=hashlib.sha256((ASSETS/(base+'.glb')).read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(ART/(base+'.blend')));scene=bpy.context.scene
bpy.context.preferences.filepaths.save_version=0;root=bpy.data.objects['presentation_root']
root.animation_data.action=None
for tr in list(root.animation_data.nla_tracks):root.animation_data.nla_tracks.remove(tr)
root.rotation_mode='XYZ';scene.render.fps=192;scene.frame_start=0;scene.frame_end=768
# New-state anchors route the full vanilla cuboids down out of view, rather
# than pointing their proximal ends at the lens. Original binds/clips stay in
# the combined GLB; only the three new animations contain these arm channels.
C=Matrix(((1,0,0,0),(0,0,1,0),(0,-1,0,0),(0,0,0,1)));CI=C.inverted()
arm_nodes=[]
for side,direction in [('right',(-.25,.91,-.33)),('left',(.30,.86,-.41))]:
 arm=bpy.data.objects['vanilla_arm_'+side];arm.animation_data_create()
 arm.animation_data.action=None
 for track in list(arm.animation_data.nla_tracks):arm.animation_data.nla_tracks.remove(track)
 y=Vector(direction).normalized();normal=Vector((1,0,0) if side=='right' else (0,-1,0))
 x=y.cross(normal).normalized()
 if x.x<0:x.negate()
 z=x.cross(y).normalized();toward=-normal
 width=(3 if variant=='slim' else 4)/16
 offset=x*(math.copysign(width/2,toward.dot(x)) if abs(toward.dot(x))>1e-6 else 0)
 offset+=z*(math.copysign(.125,toward.dot(z)) if abs(toward.dot(z))>1e-6 else 0)
 grip=bpy.data.objects['grip_'+('primary' if side=='right' else 'secondary')]
 end=(C@grip.matrix_local).translation-offset
 anchor=Matrix(((x.x,y.x,z.x,end.x),(x.y,y.y,z.y,end.y),(x.z,y.z,z.z,end.z),(0,0,0,1)))
 arm.matrix_local=CI@anchor@C;arm.rotation_mode='QUATERNION';arm_nodes.append(arm)
for clip,keys in CLIPS.items():
 action=bpy.data.actions.new('fp_motion_'+clip);root.animation_data.action=action
 for seconds,loc,rot in keys:
  root.location=loc;root.rotation_euler=tuple(math.radians(v) for v in rot)
  root.keyframe_insert('location',frame=seconds*192);root.keyframe_insert('rotation_euler',frame=seconds*192)
 for curve in action.fcurves:
  for key in curve.keyframe_points:key.interpolation='BEZIER';key.handle_left_type=key.handle_right_type='AUTO_CLAMPED'
 root.animation_data.action=None;track=root.animation_data.nla_tracks.new();track.name=clip;track.strips.new(clip,0,action)
 for arm in arm_nodes:
  action=bpy.data.actions.new('fp_motion_'+clip+'_'+arm.name);arm.animation_data.action=action
  for seconds in (0,keys[-1][0]):
   arm.keyframe_insert('location',frame=seconds*192);arm.keyframe_insert('rotation_quaternion',frame=seconds*192)
  arm.animation_data.action=None;track=arm.animation_data.nla_tracks.new();track.name=clip;track.strips.new(clip,0,action)
root.location=(0,0,0);root.rotation_euler=(0,0,0)
bpy.ops.object.select_all(action='DESELECT')
for n in original_doc['nodes']:bpy.data.objects[n['name']].select_set(True)
bpy.context.view_layer.objects.active=root
with tempfile.TemporaryDirectory() as tmp:
 temp=Path(tmp)/'new-actions.glb'
 bpy.ops.export_scene.gltf(filepath=str(temp),export_format='GLB',use_selection=True,export_yup=True,export_animations=True,export_skins=False,
  export_animation_mode='NLA_TRACKS',export_force_sampling=True,export_optimize_animation_size=False,export_optimize_animation_keep_anim_object=True,export_morph=False,export_extras=False)
 extra,extra_bin=unpack(temp)
 normalize_rigid_scales(extra,extra_bin)
 doc=copy.deepcopy(original_doc);data=original_bin.copy();accessor_map={};view_map={};names={n['name']:i for i,n in enumerate(doc['nodes'])}
 def accessor(index):
  if index in accessor_map:return accessor_map[index]
  a=copy.deepcopy(extra['accessors'][index]);vi=a['bufferView']
  if vi not in view_map:
   v=copy.deepcopy(extra['bufferViews'][vi]);start=v.get('byteOffset',0);length=v['byteLength']
   data.extend(b'\0'*((-len(data))%4));v['byteOffset']=len(data);v['buffer']=0;data.extend(extra_bin[start:start+length])
   view_map[vi]=len(doc['bufferViews']);doc['bufferViews'].append(v)
  a['bufferView']=view_map[vi];accessor_map[index]=len(doc['accessors']);doc['accessors'].append(a);return accessor_map[index]
 for anim in extra['animations']:
  assert anim['name'] in CLIPS
  a=copy.deepcopy(anim)
  for sampler in a['samplers']:sampler['input']=accessor(sampler['input']);sampler['output']=accessor(sampler['output'])
  for channel in a['channels']:channel['target']['node']=names[extra['nodes'][channel['target']['node']]['name']]
  doc['animations'].append(a)
 for key in ('nodes','meshes','materials','images','textures','scenes'):
  assert doc.get(key)==original_doc.get(key),key
 assert doc['animations'][:len(original_doc['animations'])]==original_doc['animations']
 assert data[:len(original_bin)]==original_bin
 pack(ASSETS/(stem+'.glb'),doc,data)
profile=json.loads((ASSETS/(base+'.profile.json')).read_text());profile['id']='bobin_craft:debug/'+stem;profile['model']='bobin_craft:armament/debug/'+stem+'.glb'
(ASSETS/(stem+'.profile.json')).write_text(json.dumps(profile,indent=2)+'\n')
# New editable Blender file; the approved source is never saved over.
for obj in [root,*arm_nodes]:
 for tr in obj.animation_data.nla_tracks:tr.mute=tr.name!='rest'
scene.frame_set(0);bpy.ops.wm.save_as_mainfile(filepath=str(ART/(stem+'.blend')))
meta={'base_sha256':baseline_sha,'variant':variant,'camera_offset':profile['camera_offset'],'camera_rotation':profile['camera_rotation'],'added_clips':list(CLIPS),'approved_binary_prefix_bytes':len(original_bin),'legacy_animations_unchanged':True,'geometry_metadata_unchanged':True}
(ASSETS/(stem+'.provenance.json')).write_text(json.dumps(meta,indent=2)+'\n')
if '--render' in sys.argv:
 for clip,time in [('rest',0),('aim',0),('sprint',.17)]:
  for obj in [root,*arm_nodes]:
   for tr in obj.animation_data.nla_tracks:tr.mute=tr.name!=clip
  scene.frame_set(round(time*192));scene.render.filepath=str(ART/(stem+'.'+clip+'.png'));bpy.ops.render.render(write_still=True)
print('APPENDED_FPS_MOTIONS',variant,meta)
