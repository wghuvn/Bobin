"""Blender-only neutral ergonomic gauge, dimensioned in metres. No weapon parts.
Hand frame: +Y primary, -Z palm normal, right thumb -X / left thumb +X.
Frames are right handed. Contact origins come from independent object surfaces.
"""
import bpy
from mathutils import Matrix, Vector

PALM_LENGTH = .065
PALM_FIRST = .035
CONTACT_DEPTH = .03125


def frame(origin, primary, dorsal):
    y, z = Vector(primary).normalized(), Vector(dorsal).normalized()
    x = y.cross(z).normalized()
    return Matrix(((x.x,y.x,z.x,origin[0]),(x.y,y.y,z.y,origin[1]),(x.z,y.z,z.z,origin[2]),(0,0,0,1)))


def create(root, variant):
    gauge = bpy.data.objects.new('ergonomic_gauge', None);bpy.context.scene.collection.objects.link(gauge);gauge.parent=root
    materials = {}
    for name, color in [('gauge_body',(.075,.15,.18,1)),('gauge_pad',(.13,.15,.16,1)),('gauge_contact',(.13,.42,.38,1)),('axis_primary',(.95,.40,.04,1)),('axis_normal',(.12,.40,.85,1))]:
        m=bpy.data.materials.new(name);m.use_nodes=True;m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=color
        m.node_tree.nodes['Principled BSDF'].inputs['Metallic'].default_value=0;materials[name]=m
    objects=[]
    def box(name,center,dimensions,material):
        bpy.ops.mesh.primitive_cube_add(size=1,location=center);o=bpy.context.object;o.name=name;o.dimensions=dimensions
        bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.parent=gauge;o.data.materials.append(materials[material])
        # Authored bevels produce real triangles, not Minecraft ModelPart cubes.
        bevel=o.modifiers.new('small_panel_edges','BEVEL');bevel.width=.002;bevel.segments=1
        bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=bevel.name)
        objects.append(o);return o
    box('gauge_long_body',(0,.195,.065),(.10,.69,.09),'gauge_body')
    box('gauge_rear_support',(0,-.23,.065),(.16,.16,.10),'gauge_pad')
    box('gauge_primary_handle',(0,-.025,-.115),(.0625,.085,.18),'gauge_contact')
    # Join the handle to the body behind the thumb; extending its entire top
    # would fill the thumb clearance. The neck overlaps both solids by >2 mm.
    box('gauge_handle_neck',(0,-.0575,-.0015),(.04,.02,.053),'gauge_contact')
    box('gauge_forward_support',(0,.34,.0025),(.10,.19,.035),'gauge_contact')
    box('gauge_forward_axis',(0,.49,.115),(.02,.07,.015),'axis_primary')
    box('gauge_side_axis',(.055,.12,.07),(.015,.04,.025),'axis_normal')
    # Surfaces are specified before the hands exist. 1 mm contact clearance.
    contacts={'right':frame((.03225,-.055,-.115),(0,1,0),(1,0,0)),
              'left':frame((-.015,.34,-.016),(1,0,0),(0,0,-1))}
    arms={}
    for side,contact in contacts.items():
        sign=1 if side=='right' else -1
        local_offset=Vector((0,PALM_FIRST,-CONTACT_DEPTH))
        wrist_matrix=contact @ Matrix.Translation(-local_offset)
        wrist=wrist_matrix.translation.copy();primary=wrist_matrix.col[1].xyz;dorsal=wrist_matrix.col[2].xyz;lateral=wrist_matrix.col[0].xyz
        approach=Vector((.35,-.80,-.35)) if side=='right' else Vector((-.8,-.4,-.15))
        elbow=wrist+approach.normalized()*.25
        upper_direction=Vector((.45,-.15,-.85)) if side=='right' else Vector((-.75,-.30,-.55))
        shoulder=elbow+upper_direction.normalized()*.375
        width=(3 if variant=='slim' else 4)/16*.625
        digits=[];order=('index','middle','ring','little') if side=='right' else ('little','ring','middle','index')
        for finger in ('thumb',*order):
            if finger=='thumb':
                direction=-1 if side=='right' else 1
                start=wrist+primary*.05+lateral*(direction*width/2)
                if side=='right':
                    thumb_fit=(.15625-width)/2
                    path=[start,start+primary*.010+lateral*direction*(.025+thumb_fit),start+primary*.05-dorsal*.055+lateral*direction*(.028+thumb_fit),start+primary*.055-dorsal*.075+lateral*direction*(.028+thumb_fit)]
                else:
                    path=[start,start-primary*.015+lateral*.025,start-primary*.018+lateral*.055-dorsal*.015,start-primary*.005+lateral*.065-dorsal*.03]
            else:
                offset=[-.369,-.125,.125,.369][order.index(finger)]*width
                start=wrist+primary*PALM_LENGTH+lateral*offset
                if side=='right':
                    path=[start,start+primary*.06-dorsal*.004,start+primary*.072-dorsal*.065,start+primary*.055-dorsal*.125]
                else:
                    path=[start,start+primary*.055-dorsal*.007,start+primary*.065-dorsal*.060,start+primary*.055-dorsal*.095]
            digits.append((finger,path,[f'{finger}_{i}_{side}' for i in (1,2,3)]))
        arms[side]={'side':side,'sign':sign,'shoulder':shoulder,'elbow':elbow,'wrist':wrist,'forward':primary,'lateral':lateral,'dorsal':dorsal,'digits':digits,'palm_width':width,'contact_frame':contact,'contact_offset':local_offset}
    return gauge,objects,arms
