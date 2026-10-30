"""New FPS sprint/aim only, authored from the supplied Bodycam references.
The original five FPS clips, camera composition and arm geometry are untouched.
Metres/Euler XYZ in the original Blender presentation-root coordinates.
"""
AIM=(-.24,-.08,.075)
SPRINT=(.025,-.01,-.10)
# time, root location, Euler XYZ degrees
CLIPS={
 'aim':[(0,AIM,(0,0,0)),(.15,AIM,(0,0,0)),(1,(AIM[0]+.0004,AIM[1],AIM[2]+.0007),(.03,0,.02)),
        (2,(AIM[0],AIM[1],AIM[2]+.001),(0,.02,0)),(3,(AIM[0]-.0004,AIM[1],AIM[2]+.0007),(-.03,0,-.02)),(3.85,AIM,(0,0,0)),(4,AIM,(0,0,0))],
 'sprint':[(0,SPRINT,(-38,6,-18)),(.17,(.036,-.014,-.106),(-36.5,7,-16.5)),
           (.34,SPRINT,(-38,6,-18)),(.51,(.014,-.006,-.094),(-39.5,5,-19.5)),(.68,SPRINT,(-38,6,-18))],
}
