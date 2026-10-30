"""External-view authoring only. No FPS poses, transforms or clips are imported.
Low-ready carry is authored across the chest, independently of first person.
The pelvis is the shared pivot of every upper-body layer.
"""
REST = ((0,.54,-.33), (-12,0,42), (-2,0,-2), (0,0,0))
LOW = ((0,.53,-.39), (-28,0,40), (-2,0,-2), (0,0,0))
SIGHT = ((.12,.56,-.16), (0,0,4), (-3,0,-3), (0,0,0))
CHECK = ((0,.55,-.33), (8,-7,50), (-2,0,0), (0,0,0))
# seconds, gauge preference, world gauge Euler XYZ, torso Euler, head assist Euler
POSES = {
 'idle': [(0,*REST),(1,*REST),(2,(0,.54,-.328),(-11.8,0,42),(-2.1,0,-2),(0,0,0)),(3,*REST),(4,*REST)],
 'inspect': [(0,*REST),(.25,(0,.54,-.35),(-8,0,43),(-2,0,-2),(0,0,0)),
             (.85,(0,.56,-.28),(8,-7,54),(-3,0,0),(0,0,0)),
             (1.22,(0,.56,-.28),(8,-7,54),(-3,0,0),(0,0,0)),
             (1.92,(0,.56,-.32),(3,7,45),(-2,0,-3),(0,0,0)),
             (2.25,(0,.56,-.32),(3,7,45),(-2,0,-3),(0,0,0)),
             (2.65,*CHECK),(2.75,*CHECK)],
 'inspect_recover': [(0,*CHECK),(.12,*CHECK),(.70,*REST),(.90,*REST)],
 'raise': [(0,*LOW),(.10,*LOW),(.48,*REST),(.625,*REST)],
 'lower': [(0,*REST),(.08,*REST),(.43,*LOW),(.5,*LOW)],
 # Raise while still across the chest before aligning the stock. Turning at
 # the low carry height would sweep the rear pad through the torso.
 'aim': [(0,*REST),(.08,*REST),(.28,(.12,.56,-.16),(-3,0,42),(-3,0,-3),(0,0,0)),
         (.50,(.12,.56,-.16),(0,0,18),(-3,0,-3),(0,0,0)),(.70,*SIGHT),(.85,*SIGHT),(1.2,*SIGHT)],
}
DURATIONS = {'idle':4.,'inspect':2.75,'raise':.625,'lower':.5,'move':1.,'aim':1.2,'inspect_recover':.9}

def interpolate(keys,t):
    for a,b in zip(keys,keys[1:]):
        if t<=b[0]:
            u=max(0,min(1,(t-a[0])/(b[0]-a[0])));u=u*u*u*(10+u*(-15+6*u))
            return tuple(tuple(x+(y-x)*u for x,y in zip(a[k],b[k])) for k in (1,2,3,4))
    return keys[-1][1:]
