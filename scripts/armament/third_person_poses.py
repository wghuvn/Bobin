"""External-view authoring only. No FPS poses, transforms or clips are imported.
World-facing gauge angles are authored independently of the bladed torso stance.
The pelvis is the shared pivot of every upper-body layer.
"""
REST = ((.18,.60,-.35), (-10,0,12), (-2,0,-32), (0,0,0))
LOW = ((.20,.45,-.50), (-38,0,18), (-2,0,-38), (0,0,0))
SIGHT = ((.20,.29,-.115), (2,0,2), (-5,0,-40), (-1,-3,0))
CHECK = ((.17,.48,-.28), (16,-5,30), (-2,0,-28), (0,0,0))
# seconds, gauge preference, world gauge Euler XYZ, torso Euler, head assist Euler
POSES = {
 'idle': [(0,*REST),(1,*REST),(2,(.18,.60,-.348),(-9.8,0,12),(-2.1,0,-32),(0,0,0)),(3,*REST),(4,*REST)],
 'inspect': [(0,*REST),(.25,(.18,.56,-.36),(-12,0,15),(-2,0,-32),(0,0,0)),
             (.85,(.17,.50,-.25),(18,-8,42),(-3,0,-26),(1,-1,0)),
             (1.22,(.17,.50,-.25),(18,-8,42),(-3,0,-26),(1,-1,0)),
             (1.92,(.16,.52,-.28),(11,8,20),(-2,0,-34),(0,1,0)),
             (2.25,(.16,.52,-.28),(11,8,20),(-2,0,-34),(0,1,0)),
             (2.65,*CHECK),(2.75,*CHECK)],
 'inspect_recover': [(0,*CHECK),(.12,*CHECK),(.70,*REST),(.90,*REST)],
 'raise': [(0,*LOW),(.10,*LOW),(.48,*REST),(.625,*REST)],
 'lower': [(0,*REST),(.08,*REST),(.43,*LOW),(.5,*LOW)],
 'aim': [(0,*REST),(.08,*REST),(.62,*SIGHT),(.85,*SIGHT),(1.2,*SIGHT)],
}
DURATIONS = {'idle':4.,'inspect':2.75,'raise':.625,'lower':.5,'move':1.,'aim':1.2,'inspect_recover':.9}

def interpolate(keys,t):
    for a,b in zip(keys,keys[1:]):
        if t<=b[0]:
            u=max(0,min(1,(t-a[0])/(b[0]-a[0])));u=u*u*u*(10+u*(-15+6*u))
            return tuple(tuple(x+(y-x)*u for x,y in zip(a[k],b[k])) for k in (1,2,3,4))
    return keys[-1][1:]
