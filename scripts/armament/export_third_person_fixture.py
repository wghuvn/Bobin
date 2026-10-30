"""Independent vanilla-player 3P authoring scene. Blender 4.3+, --variant default|slim.
Only the neutral gauge meshes are exported. Player cuboids are preview proxies.
"""
import bpy, json, math, sys, struct
from pathlib import Path
from mathutils import Matrix, Vector, Euler, Quaternion
sys.path.insert(0,str(Path(__file__).parent))
from ergonomic_calibration import create_gauge  # geometry only, no FPS animations
from third_person_poses import POSES, DURATIONS, REST, interpolate
from glb_rigid_scales import normalize_rigid_scales
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/bobin_craft/armament/debug'
ART=ROOT/'art/armament'
VARIANT=sys.argv[sys.argv.index('--variant')+1] if '--variant' in sys.argv else 'default'
assert VARIANT in ('default','slim')
STEM='third_person_fixture'+('_slim' if VARIANT=='slim' else '')
FPS=96
# Minecraft model coordinates -> Blender. Both are right-handed, one metre per block.
B=Matrix(((-1,0,0,0),(0,0,-1,0),(0,-1,0,0),(0,0,0,1)))
BI=B.inverted()
C=Matrix(((1,0,0,0),(0,0,1,0),(0,-1,0,0),(0,0,0,1))) # Blender -> glTF
A=C@B
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.context.preferences.filepaths.save_version=0
scene=bpy.context.scene;scene.render.fps=FPS;scene.unit_settings.system='METRIC'
scene.frame_start=0;scene.frame_end=4*FPS

def empty(name,parent=None):
    o=bpy.data.objects.new(name,None);scene.collection.objects.link(o);o.parent=parent
    o.rotation_mode='QUATERNION';return o
motion=empty('tp_motion');stance=empty('tp_stance',motion);body=empty('tp_body',stance)
weapon_motion=empty('tp_weapon_motion',body);weapon=empty('tp_weapon',weapon_motion)
head_assist=empty('tp_head_assist')
HIP=Vector((0,0,-.75))

def at_hip(rotation):
    return Matrix.Translation(HIP)@rotation.to_4x4()@Matrix.Translation(-HIP)
gauge,meshes,contacts=create_gauge(weapon,forward_support_y=.19)
width=(3 if VARIANT=='slim' else 4)/16
end_x=.5/16 if VARIANT=='slim' else 1/16
arms={s:empty('tp_arm_'+s,body) for s in ('right','left')}
# Exact vanilla pivot and distal rectangle centre. No elbow or hand bones.
shoulders={'right':Vector((5/16,0,-(2.5 if VARIANT=='slim' else 2)/16)),
           'left':Vector((-5/16,0,-(2.5 if VARIANT=='slim' else 2)/16))}
end_model={'right':Vector((-end_x,10/16,0)), 'left':Vector((end_x,10/16,0))}
end_blender={s:(B.to_3x3()@v) for s,v in end_model.items()}
# End-centre sockets lie outside the grip surfaces, along the approach of the
# rigid cuboids. These are independent third-person attachments.
# The forward support spans Y=.095..285. Grasp its rear section: a rigid
# vanilla support arm cannot reach a human rifle's forward grip from a square
# torso. Keep the actual gauge geometry and full arm lengths unchanged.
local_grips={'right':contacts['right'].translation.copy(), 'left':Vector((-.04,.10,-.040))}
sockets={}
for s in arms:
    o=empty('tp_grip_'+s,weapon);o.location=local_grips[s];sockets[s]=o
    o=empty('tp_contact_'+s,arms[s]);o.location=end_blender[s];sockets['contact_'+s]=o

def solve_translation(rotation,preferred):
    c=[shoulders[s]-rotation@local_grips[s] for s in ('right','left')]
    axis=c[1]-c[0];distance=axis.length;axis.normalize()
    rr=end_blender['right'].length;rl=end_blender['left'].length
    along=(rr*rr-rl*rl+distance*distance)/(2*distance)
    assert abs(rr-rl)<distance<rr+rl,'Unreachable two-hand hold'
    mid=c[0]+axis*along;circle=math.sqrt(rr*rr-along*along)
    direction=preferred-mid;direction-=axis*direction.dot(axis)
    assert direction.length>1e-6
    return mid+direction.normalized()*circle

def pose(t,clip):
    for o in (motion,stance,body,weapon_motion,weapon,head_assist,*arms.values()):
        o.location=(0,0,0);o.rotation_quaternion=(1,0,0,0);o.scale=(1,1,1)
    loc,angles,torso,head=interpolate(POSES[clip],t)
    body_rotation=Euler(tuple(math.radians(a) for a in torso),'XYZ').to_matrix()
    body_transform=at_hip(body_rotation)
    body.matrix_local=body_transform
    world_rotation=Euler(tuple(math.radians(a) for a in angles),'XYZ').to_matrix()
    rotation=body_rotation.inverted()@world_rotation
    preference=body_transform.inverted()@Vector(loc)
    weapon.location=solve_translation(rotation,preference);weapon.rotation_quaternion=rotation.to_quaternion()
    head_assist.rotation_quaternion=Euler(tuple(math.radians(a) for a in head),'XYZ').to_quaternion()
    for side,o in arms.items():
        target=weapon.location+rotation@local_grips[side]
        direction=target-shoulders[side];axis=direction.normalized()
        q=end_blender[side].rotation_difference(direction)
        # Stable cuboid faces: independently authored whole-arm roll, not the
        # shortest-arc twist that produced diagonal slabs in front of the chest.
        current=q@Vector((-1,0,0));desired=body_rotation.inverted()@Vector((-1,0,0))
        current-=axis*current.dot(axis);desired-=axis*desired.dot(axis)
        current.normalize();desired.normalize()
        twist=math.atan2(axis.dot(current.cross(desired)),current.dot(desired))
        o.location=shoulders[side];o.rotation_quaternion=Quaternion(axis,twist)@q

tracked=[motion,stance,body,weapon,head_assist,*arms.values(),weapon_motion]
for clip,duration in DURATIONS.items():
    names=[body,weapon,head_assist,*arms.values()] if clip!='move' else [motion,weapon_motion]
    for o in names:
        o.animation_data_create();o.animation_data.action=bpy.data.actions.new(clip+'_'+o.name)
    for f in range(round(duration*FPS)+1):
        t=f/FPS
        pose(t,clip if clip!='move' else 'idle')
        if clip=='move':
            # Rotation around the pelvis, with no independent vertical bob.
            phase=2*math.pi*t
            motion.matrix_local=at_hip(Euler((math.radians(.25)*math.sin(phase),math.radians(.30)*math.sin(phase),math.radians(.4)*math.sin(phase)),'XYZ').to_matrix())
            weapon_motion.location=(.002*math.sin(phase),0,.003*math.sin(2*phase))
        for o in names:
            o.keyframe_insert('location',frame=f);o.keyframe_insert('rotation_quaternion',frame=f)
    for o in names:
        action=o.animation_data.action
        for fc in action.fcurves:
            for k in fc.keyframe_points:k.interpolation='LINEAR'
        o.animation_data.action=None
        track=o.animation_data.nla_tracks.new();track.name=clip;strip=track.strips.new(clip,0,action);strip.action_frame_start=0;strip.action_frame_end=duration*FPS
# Separate posture and run layers; never drive the legs or the head.
for clip in ('crouch','run'):
    nodes=[stance] if clip=='crouch' else [motion,weapon_motion]
    for o in nodes:o.animation_data_create();o.animation_data.action=bpy.data.actions.new(clip+'_'+o.name)
    for f in range(FPS+1):
        for p in tracked:p.location=(0,0,0);p.rotation_quaternion=(1,0,0,0)
        t=f/FPS
        if clip=='crouch':
            u=min(1,t/.2);u=u*u*(3-2*u)
            stance.matrix_local=at_hip(Euler((math.radians(-22)*u,0,0),'XYZ').to_matrix())
        else:
            motion.matrix_local=at_hip(Euler((math.radians(-3),math.radians(.6)*math.sin(2*math.pi*t),math.radians(.8)*math.sin(2*math.pi*t)),'XYZ').to_matrix())
            weapon_motion.location=(.005*math.sin(2*math.pi*t),0,-.015+.006*math.sin(4*math.pi*t))
            weapon_motion.rotation_quaternion=Euler((math.radians(-6),0,0),'XYZ').to_quaternion()
        for o in nodes:o.keyframe_insert('location',frame=f);o.keyframe_insert('rotation_quaternion',frame=f)
    for o in nodes:
        action=o.animation_data.action
        for fc in action.fcurves:
            for k in fc.keyframe_points:k.interpolation='LINEAR'
        o.animation_data.action=None;track=o.animation_data.nla_tracks.new();track.name=clip;track.strips.new(clip,0,action)
# Export matching NLA track names as independent clips across nodes.
for o in tracked:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:tr.mute=True
pose(0,'idle')
bpy.ops.object.select_all(action='DESELECT')
for o in [*tracked,gauge,*meshes,*sockets.values()]:o.select_set(True)
bpy.context.view_layer.objects.active=motion
for o in tracked:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:tr.mute=False
bpy.ops.export_scene.gltf(filepath=str(ASSETS/(STEM+'.glb')),export_format='GLB',use_selection=True,export_yup=True,
    export_animations=True,export_skins=False,export_animation_mode='NLA_TRACKS',export_force_sampling=True,
    export_optimize_animation_size=False,export_optimize_animation_keep_anim_object=True,export_morph=False,export_extras=False)
# NLA exporter may retain the first run key as an unanimated node default.
# Additive layers require a neutral bind for motion/stance, regardless of track
# enumeration order. Normalize only their defaults; preserve every exported key.
path=ASSETS/(STEM+'.glb');raw=path.read_bytes()
size=struct.unpack_from('<I',raw,12)[0]
document=json.loads(raw[20:20+size]);payload=bytearray(raw[28+size:])
normalize_rigid_scales(document,payload)
binary=struct.pack('<II',len(payload),0x004e4942)+payload
for n in document['nodes']:
    if n['name'] in ('tp_motion','tp_stance','tp_head_assist','tp_weapon_motion'):
        for k in ('translation','rotation','scale','matrix'):n.pop(k,None)
encoded=json.dumps(document,separators=(',',':')).encode();encoded+=b' '*((-len(encoded))%4)
path.write_bytes(struct.pack('<4sII',b'glTF',2,20+len(encoded)+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+binary)
# Independent Blender goldens, including all cuboid matrices and attachment points.
for o in tracked:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:tr.mute=True
samples=[]
for clip in DURATIONS:
    if clip=='move':continue
    for step in range(9):
        t=DURATIONS[clip]*step/8
        pose(0,'idle')
        for o in tracked:
            if o.animation_data:
                for tr in o.animation_data.nla_tracks:tr.mute=tr.name!=clip
        frame=t*FPS;scene.frame_set(int(frame),subframe=frame-int(frame));bpy.context.view_layer.update()
        def matrix(o):
            m=C@o.matrix_world@C.inverted();return [m[r][c] for c in range(4) for r in range(4)]
        samples.append({'clip':clip,'time':t,'matrices':{o.name:matrix(o) for o in [body,weapon,head_assist,*arms.values(),*sockets.values()]}})
meta={'schema_version':1,'variant':VARIANT,'coordinates':'glTF metres; model conversion diag(-1,-1,1)',
      'arm_length_pixels':12,'arm_end_model':{s:list(v) for s,v in end_model.items()},
      'source':'Independent third-person authored poses, no FPS animation inputs',
      'durations':DURATIONS,'samples':samples}
(ASSETS/(STEM+'.reference.json')).write_text(json.dumps(meta,indent=2)+'\n')
# Authoring-only player cuboids, never included in runtime GLB.
for o in tracked:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:tr.mute=True
pose(0,'idle');bpy.context.view_layer.update()
def cube(name,lo,hi,parent,material):
    verts=[(B@Vector((x,y,z))).to_tuple() for x,y,z in [(lo[0],lo[1],lo[2]),(hi[0],lo[1],lo[2]),(hi[0],hi[1],lo[2]),(lo[0],hi[1],lo[2]),(lo[0],lo[1],hi[2]),(hi[0],lo[1],hi[2]),(hi[0],hi[1],hi[2]),(lo[0],hi[1],hi[2])]]
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],[(0,1,2,3),(5,4,7,6),(4,0,3,7),(1,5,6,2),(4,5,1,0),(3,2,6,7)]);mesh.update()
    o=bpy.data.objects.new(name,mesh);scene.collection.objects.link(o);o.parent=parent;o.data.materials.append(material);return o
skin=bpy.data.materials.new('preview_skin');skin.diffuse_color=(.60,.36,.22,1)
shirt=bpy.data.materials.new('preview_shirt');shirt.diffuse_color=(.04,.32,.36,1)
pants=bpy.data.materials.new('preview_pants');pants.diffuse_color=(.09,.12,.30,1)
cube('preview_body',(-.25,0,-.125),(.25,.75,.125),body,shirt)
head_proxy=cube('preview_head',(-.25,-.5,-.25),(.25,0,.25),None,skin)
leg_proxies=[]
for s in arms:
    cx=end_model[s].x
    cube('preview_'+s+'_arm',(cx-width/2,-.125,-.125),(cx+width/2,.625,.125),arms[s],skin)
    leg_proxies.append(cube('preview_'+s+'_leg',((-.119 if s=='right' else .119)-.125,.75,-.125),((-.119 if s=='right' else .119)+.125,1.5,.125),None,pants))
# Preview-only vanilla sneaking head/leg offsets. Runtime uses Minecraft's own
# head/leg animation and never exports or drives these parts.
preview_posture=[head_proxy,*leg_proxies]
for o in preview_posture:
    o.animation_data_create();o.animation_data.action=bpy.data.actions.new('preview_crouch_'+o.name)
    offset=B.to_3x3()@Vector((0,0,0) if o==head_proxy else (0,.2/16,4/16))
    for f,u in [(0,0),(FPS*.2,1),(FPS,1)]:
        o.location=offset*u;o.keyframe_insert('location',frame=f)
    action=o.animation_data.action;o.animation_data.action=None
    tr=o.animation_data.nla_tracks.new();tr.name='crouch';tr.strips.new('crouch',0,action);tr.mute=True;o.location=(0,0,0)
# Preview follows the same neck translation as the runtime adapter; the head
# keeps its independent look orientation instead of inheriting the torso yaw.
head_proxy.animation_data_clear()
head_proxy.constraints.new('COPY_LOCATION').target=body
head_proxy.constraints.new('COPY_ROTATION').target=head_assist
pose(0,'idle')
# Keep NLA actions usable in the saved authoring scene.
for o in tracked:
    if o.animation_data:
        for tr in o.animation_data.nla_tracks:tr.mute=tr.name!='idle'
scene.frame_set(0)
world=bpy.data.worlds.new('preview_world');scene.world=world;world.use_nodes=True;world.node_tree.nodes['Background'].inputs[0].default_value=(.13,.16,.20,1)
for name,loc,power,size in [('key',(3,4,5),800,4),('fill',(-3,1,2),500,3)]:
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=size
    o=bpy.data.objects.new(name,data);scene.collection.objects.link(o);o.location=loc;o.rotation_euler=(Vector((0,.1,-.35))-o.location).to_track_quat('-Z','Y').to_euler()
cam=bpy.data.objects.new('third_person_preview_camera',bpy.data.cameras.new('third_person_preview_camera'));scene.collection.objects.link(cam)
cam.location=(2.8,4,1.7);cam.rotation_euler=(Vector((0,.15,-.45))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=2.6;scene.camera=cam
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=False
scene.render.resolution_x=960;scene.render.resolution_y=960;scene.render.resolution_percentage=100
bpy.ops.wm.save_as_mainfile(filepath=str(ART/(STEM+'.blend')))
if '--render' in sys.argv:
    for clip,t in [('idle',0),('inspect',1),('raise',.3),('lower',.5),('move',.25),('crouch',1),('aim',1.0)]:
        for o in [*tracked,*preview_posture]:
            if o.animation_data:
                for tr in o.animation_data.nla_tracks:tr.mute=True
        for o in preview_posture:o.location=(0,0,0)
        pose(0,'idle')
        for o in [*tracked,*preview_posture]:
            if o.animation_data:
                for tr in o.animation_data.nla_tracks:tr.mute=tr.name not in (clip,'idle' if clip in ('move','crouch') else clip)
        scene.frame_set(round(t*FPS));bpy.context.view_layer.update()
        basis=body.matrix_world.to_3x3();motion.location.z-=abs(basis[2][0])*.25+abs(basis[2][1])*.125+1/64
        bpy.context.view_layer.update()
        scene.render.filepath=str(ART/(STEM+'.'+clip+'.png'));bpy.ops.render.render(write_still=True)
print('Third-person scene exported:',STEM)
