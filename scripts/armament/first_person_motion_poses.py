"""Separate FPS low-ready rest, sprint and aim. Not third-person transforms.
The original five FPS clips, camera composition and arm geometry are untouched.
Metres/Euler XYZ in the original Blender presentation-root coordinates.
"""
AIM=(-.24,.13,.075)
SPRINT=(.025,.10,-.10)
REST=(-.09,.14,-.095)
# time, root location, Euler XYZ degrees
CLIPS={
 'rest':[(0,REST,(-14,0,24)),(1,REST,(-14,0,24)),
         (2,(REST[0],REST[1],REST[2]+.001),(-13.9,0,24)),(3,REST,(-14,0,24)),(4,REST,(-14,0,24))],
 'aim':[(0,AIM,(0,0,0)),(.15,AIM,(0,0,0)),(1,(AIM[0]+.0004,AIM[1],AIM[2]+.0007),(.03,0,.02)),
        (2,(AIM[0],AIM[1],AIM[2]+.001),(0,.02,0)),(3,(AIM[0]-.0004,AIM[1],AIM[2]+.0007),(-.03,0,-.02)),(3.85,AIM,(0,0,0)),(4,AIM,(0,0,0))],
 'sprint':[(0,SPRINT,(-38,6,-18)),(.17,(.036,.096,-.106),(-36.5,7,-16.5)),
           (.34,SPRINT,(-38,6,-18)),(.51,(.014,.104,-.094),(-39.5,5,-19.5)),(.68,SPRINT,(-38,6,-18))],
}
