"""Blender-only front/side/rear review of the independent 3P pose assets."""
import bpy,sys
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/armament/task5-2-1/previews';OUT.mkdir(parents=True,exist_ok=True)
for variant in ('default','slim'):
    stem='third_person_fixture'+('_slim' if variant=='slim' else '')
    bpy.ops.wm.open_mainfile(filepath=str(ROOT/'art/armament'/(stem+'.blend')))
    scene=bpy.context.scene
    for clip,time in [('idle',0),('aim',1.2)]:
        for o in scene.objects:
            if o.animation_data:
                for tr in o.animation_data.nla_tracks:tr.mute=tr.name!=clip
        bpy.data.objects['tp_motion'].location=(0,0,0)
        scene.frame_set(round(time*scene.render.fps));bpy.context.view_layer.update()
        basis=bpy.data.objects['tp_body'].matrix_world.to_3x3()
        bpy.data.objects['tp_motion'].location.z-=abs(basis[2][0])*.25+abs(basis[2][1])*.125+1/64
        bpy.context.view_layer.update()
        for view,position in [('front',(0,4,1)),('side',(4,0,1)),('rear',(0,-4,1))]:
            scene.camera.location=position
            scene.camera.rotation_euler=(Vector((0,0,-.4))-scene.camera.location).to_track_quat('-Z','Y').to_euler()
            scene.render.filepath=str(OUT/(variant+'.'+clip+'.'+view+'.png'))
            bpy.ops.render.render(write_still=True)
