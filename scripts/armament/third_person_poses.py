"""Original external-view poses, in player-local Blender metres/degrees.
No FPS pose, camera offset, armature or clip is imported here.
Shared action durations are semantic timing contracts, not copied movements.
"""
REST = ((.10, .60, -.30), (8, 0, 60), (0, 0, 0))
LOW = ((.10, .55, -.43), (-42, 4, 70), (3, 0, -4))
# time, preferred gauge position, gauge XYZ rotation, torso XYZ rotation
POSES = {
 'idle': [(0,*REST),(1,*REST),(2,(.10,.60,-.297),(8.4,0,60),(.4,0,0)),(3,*REST),(4,*REST)],
 'inspect': [(0,*REST),(.22,(.10,.58,-.33),(4,0,63),(0,0,-3)),
             (.8,(.08,.58,-.26),(24,-12,95),(0,-2,9)),
             (1.15,(.08,.58,-.26),(24,-12,95),(0,-2,9)),
             (1.75,(.06,.60,-.28),(14,15,42),(0,2,-10)),
             (2.05,(.06,.60,-.28),(14,15,42),(0,2,-10)),
             (2.6,*REST),(2.75,*REST)],
 'raise': [(0,*LOW),(.10,*LOW),(.42,(.10,.60,-.28),(11,0,62),(0,0,1)),(.58,*REST),(.625,*REST)],
 'lower': [(0,*REST),(.08,*REST),(.38,*LOW),(.5,*LOW)],
}
DURATIONS = {'idle':4.,'inspect':2.75,'raise':.625,'lower':.5,'move':1.}

def interpolate(keys, t):
    for a,b in zip(keys,keys[1:]):
        if t <= b[0]:
            u=max(0,min(1,(t-a[0])/(b[0]-a[0])));u=u*u*(3-2*u)
            return tuple(tuple(x+(y-x)*u for x,y in zip(a[k],b[k])) for k in (1,2,3))
    return keys[-1][1:]
