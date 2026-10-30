"""Original Bobin first-person animation poses, authored in metres/seconds/degrees.

General FPS design: separate resting/motion cycles from exclusive draw, stow and
inspection actions, and give each action anticipation, a readable main phase and
settling. No TACZ code, models, keyframes or animation files are copied here.
The Blender exporter owns interpolation and baking; the runtime samples GLB.
"""

REST = ((0, 0, 0), (0, 0, 0), 0)
STOWED = ((0, .04, -.30), (-5, 0, 0), 0)

# (seconds, root translation in Blender metres, Euler XYZ degrees, elbow swivel).
# Repeated endpoint poses provide a finite stationary interval at loop seams;
# thus the exported LINEAR samples also have zero endpoint velocity.
CLIP_POSES = {
    'idle': [
        (0, *REST), (.125, *REST),
        (1, (.0005, 0, .0015), (.06, -.03, .04), .12),
        (2, (0, .0003, .0025), (.10, 0, 0), .20),
        (3, (-.0005, 0, .0015), (.06, .03, -.04), .12),
        (3.875, *REST), (4, *REST),
    ],
    'inspect': [
        (0, *REST),
        (.125, (.001, -.004, -.004), (-.3, 0, .5), -.20),
        (.625, (.012, .005, .025), (1.5, -1.2, -5), 1.50),
        (.875, (.012, .005, .025), (1.5, -1.2, -5), 1.50),
        (1.375, (-.012, .005, .018), (1.8, 1.2, 6), -1.50),
        (1.75, (-.012, .005, .018), (1.8, 1.2, 6), -1.50),
        (2.125, (.003, .004, .015), (.8, -.3, -1), .50),
        (2.625, *REST), (2.75, *REST),
    ],
    'raise': [
        (0, *STOWED), (8 / 96, *STOWED),
        (28 / 96, (0, .018, -.115), (-2.2, 0, 0), .60),
        (.5, (0, -.002, .004), (.1, 0, 0), .15),
        (56 / 96, *REST), (.625, *REST),
    ],
    'lower': [
        (0, *REST), (.0625, *REST),
        (.1875, (.001, .008, -.04), (-.8, 0, .2), .30),
        (.375, (0, .03, -.23), (-3.6, 0, 0), .40),
        (44 / 96, *STOWED), (.5, *STOWED),
    ],
    'move': [
        (0, *REST), (2 / 96, *REST),
        (.125, (.004, 0, -.002), (.1, -.05, .2), .30),
        (.25, (.007, .001, .003), (.2, 0, .4), .50),
        (.375, (.003, .002, .005), (.05, .1, .2), .25),
        (.5, *REST),
        (.625, (-.004, .001, -.002), (-.1, .05, -.2), -.30),
        (.75, (-.007, .001, .003), (-.2, 0, -.4), -.50),
        (.875, (-.003, .002, .005), (-.05, -.1, -.2), -.25),
        (94 / 96, *REST), (1, *REST),
    ],
}

CLIP_PHASES = {
    'idle': [('rest', 0, .125), ('inhale', .125, 2), ('exhale', 2, 3.875), ('rest', 3.875, 4)],
    'inspect': [('anticipate', 0, .125), ('first_side', .125, .625), ('first_hold', .625, .875),
                ('second_side', .875, 1.375), ('second_hold', 1.375, 1.75),
                ('centre_check', 1.75, 2.125), ('return', 2.125, 2.625), ('settle', 2.625, 2.75)],
    'raise': [('anticipate', 0, 8 / 96), ('present', 8 / 96, .5), ('settle', .5, .625)],
    'lower': [('anticipate', 0, .0625), ('stow', .0625, 44 / 96), ('hold', 44 / 96, .5)],
    'move': [('left_step', 0, .5), ('right_step', .5, 1)],
}


def authoring_metadata():
    return {
        'source': 'original Bobin Blender key poses; no imported TACZ code or assets',
        'units': {'translation': 'metres', 'rotation': 'Euler XYZ degrees', 'time': 'seconds'},
        'interpolation': 'AUTO_CLAMPED Bezier root; LINEAR contact-preserving arm bases; 192 Hz GLB bake',
        'clips': {name: {'duration': keys[-1][0], 'loop': name in ('idle', 'move'),
                          'phases': [{'name': label, 'start': start, 'end': end} for label, start, end in CLIP_PHASES[name]],
                          'keys': keys}
                  for name, keys in CLIP_POSES.items()},
    }
