"""Blender-only Minecraft arm authoring. One metre = one block, one pixel = 1/16 m.

Rectangular panel topology, shared elbow/wrist rings and welded finger webs.
UVs use the vanilla 64x64 cuboid net; the final two rows articulate as a hand.
This module is never used by the Java renderer.
"""
import bpy
import bmesh
from mathutils import Vector

PIXEL = 1 / 16


def skin_image(art, variant):
    """Original diagnostic skin, not an extracted Minecraft or reference texture."""
    pixel_width = 3 if variant == 'slim' else 4
    image = bpy.data.images.new('diagnostic_player_skin_' + variant, width=64, height=64, alpha=True)
    pixels = [0.0] * (64 * 64 * 4)
    for ox, oy, overlay in [(40, 16, False), (32, 48, False), (40, 32, True), (48, 48, True)]:
        for y in range(16):
            for x in range(16):
                # Pale grey / orange sleeve, contrasting bare terminal hand rows.
                rgb = (0.62, 0.65, 0.67) if y < 12 else (0.62, 0.39, 0.25)
                if y < 12 and (x in (3, 4, 10, 11) or y in (5, 6)):
                    rgb = (0.98, 0.46, 0.025)
                if y < 12 and x in (7, 8):
                    rgb = (0.12, 0.14, 0.16)
                if y < 4 and 4 + pixel_width <= x < 4 + 2 * pixel_width:
                    rgb = (0.62, 0.39, 0.25)  # Vanilla bottom-of-arm island.
                alpha = 1.0 if not overlay or 5 <= y < 12 else 0.0
                at = ((63 - (oy + y)) * 64 + ox + x) * 4
                pixels[at:at + 4] = [*rgb, alpha]
    image.pixels = pixels
    image.filepath_raw = str(art / ('diagnostic_player_skin' + ('_slim' if variant == 'slim' else '') + '_64.png'))
    image.file_format = 'PNG'
    image.save()
    image.pack()
    return image


def skin_material(name, image, overlay=False):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    shader = material.node_tree.nodes['Principled BSDF']
    shader.inputs['Roughness'].default_value = 0.9
    shader.inputs['Metallic'].default_value = 0
    texture = material.node_tree.nodes.new('ShaderNodeTexImage')
    texture.image = image
    texture.interpolation = 'Closest'
    material.node_tree.links.new(texture.outputs['Color'], shader.inputs['Base Color'])
    if overlay:
        clip = material.node_tree.nodes.new('ShaderNodeMath')
        clip.operation = 'GREATER_THAN';clip.inputs[1].default_value = 0.1
        material.node_tree.links.new(texture.outputs['Alpha'], clip.inputs[0])
        material.node_tree.links.new(clip.outputs[0], shader.inputs['Alpha'])
        material.surface_render_method = 'DITHERED'
        material.alpha_threshold = 0.1
    return material


def make_arm(data, arm, variant, material, overlay_material):
    side = data['side']
    width = (3 if variant == 'slim' else 4) * PIXEL
    upper, lower, hand = 'upper_' + side, 'forearm_' + side, 'hand_' + side
    vertices, faces, weights, uv_faces = [], [], [], []
    # Alternating finger and web intervals; shared vertices keep the hand closed.
    fractions = [0, .015, .247, .253, .497, .503, .747, .753, .985, 1]
    n = len(fractions)
    rings = []
    arm_uv = (40, 16) if side == 'right' else (32, 48)
    sleeve_uv = (40, 32) if side == 'right' else (48, 48)
    pixel_width = 3 if variant == 'slim' else 4

    def vertex(point, influence):
        index = len(vertices)
        vertices.append(point.copy())
        weights.append(influence.copy())
        return index

    def face(indices, coords):
        faces.append(tuple(indices))
        uv_faces.append(coords)

    def uv(direction, x, depth, row, origin=arm_uv):
        u, v = origin
        w, d = pixel_width, 4
        if direction == 'front':
            return ((u + d + x * w) / 64, 1 - (v + d + row) / 64)
        if direction == 'back':
            return ((u + 2 * d + w + (1 - x) * w) / 64, 1 - (v + d + row) / 64)
        if direction == 'right':
            return ((u + depth * d) / 64, 1 - (v + d + row) / 64)
        return ((u + d + w + (1 - depth) * d) / 64, 1 - (v + d + row) / 64)

    def axes(tangent):
        # No round cross-sections: two parallel, flat skin panels and two sides.
        x = data['lateral'] - tangent * data['lateral'].dot(tangent)
        if x.length < .01:
            x = tangent.cross(data['dorsal'])
        x.normalize()
        z = x.cross(tangent).normalized()
        return x, z

    def ring(center, tangent, w, h, influence, row):
        x, z = axes(tangent)
        front = [vertex(center + x * ((f - .5) * w) + z * h / 2, influence) for f in fractions]
        back = [vertex(center + x * ((f - .5) * w) - z * h / 2, influence) for f in fractions]
        value = {'front': front, 'back': back, 'row': row, 'center': center, 'x': x, 'z': z}
        if rings:
            previous = rings[-1]
            for direction in ('front', 'back'):
                for k in range(n - 1):
                    indices = [previous[direction][k], previous[direction][k + 1], value[direction][k + 1], value[direction][k]]
                    coords = [uv(direction, fractions[k], 0, previous['row']), uv(direction, fractions[k + 1], 0, previous['row']),
                              uv(direction, fractions[k + 1], 0, row), uv(direction, fractions[k], 0, row)]
                    face(indices if direction == 'front' else indices[::-1], coords if direction == 'front' else coords[::-1])
            for k, direction in [(0, 'right'), (n - 1, 'left')]:
                # One side panel of the palm becomes the connected thumb root.
                if previous.get('thumb_start') and k == (0 if side == 'right' else n - 1):
                    data['thumb_root'] = [previous['front'][k], value['front'][k], value['back'][k], previous['back'][k]]
                    if side == 'left':
                        data['thumb_root'] = [value['front'][k], previous['front'][k], previous['back'][k], value['back'][k]]
                    continue
                face([previous['front'][k], previous['back'][k], value['back'][k], value['front'][k]],
                     [uv(direction, 0, 1, previous['row']), uv(direction, 0, 0, previous['row']), uv(direction, 0, 0, row), uv(direction, 0, 1, row)])
        rings.append(value)
        return value

    s, e, w = data['shoulder'], data['elbow'], data['wrist']
    a, b, c = (e - s).normalized(), (w - e).normalized(), data['forward']
    # A bevel in the longitudinal topology, rather than an anatomical tapered tube.
    for t in (0, .25, .5, 1):
        blend = .5 if t == 1 else 0
        tangent = (a * (1 - blend) + b * blend).normalized()
        ring(s.lerp(e, t), tangent, width * (.8 if t == 1 else 1), 4 * PIXEL * (.8 if t == 1 else 1), {upper: 1 - blend, lower: blend}, t * 6)
    for t in (.5, .75, 1):
        joint = .5 if t == 1 else 0
        influence = {lower: 1} if t < 1 else {hand: 1}
        tangent = b if t < 1 else c
        ring(e.lerp(w, t), tangent, width * (1 - .375 * max(0, (t - .5) / .5)),
             PIXEL * (4 - 3 * max(0, (t - .5) / .5)), influence, 6 + t * 4)
    # Palm occupies the next skin row. Its width remains unmistakably Minecraft.
    for t in (.035 / .065, 1):
        r = ring(w + c * (.065 * t), c, width * .625, PIXEL, {hand: 1}, 10 + t)
        if t < 1:
            r['thumb_start'] = True
    first = rings[0]
    for k in range(n - 1):
        face([first['front'][k], first['front'][k + 1], first['back'][k + 1], first['back'][k]],
             [((arm_uv[0] + 4 + fractions[k] * pixel_width) / 64, 1 - arm_uv[1] / 64),
              ((arm_uv[0] + 4 + fractions[k + 1] * pixel_width) / 64, 1 - arm_uv[1] / 64),
              ((arm_uv[0] + 4 + fractions[k + 1] * pixel_width) / 64, 1 - (arm_uv[1] + 4) / 64),
              ((arm_uv[0] + 4 + fractions[k] * pixel_width) / 64, 1 - (arm_uv[1] + 4) / 64)])
    last = rings[-1]

    def digit(root_indices, path, names, x0, x1, thumb=False):
        previous = root_indices
        root_center = sum((vertices[i] for i in previous), Vector()) / 4
        transported_x = (vertices[root_indices[1]] - vertices[root_indices[0]]).normalized()
        # The root loop is shared with the palm. Subsequent closed rectangular loops
        # follow artist-defined knuckles. No separate overlapping capped phalanges.
        for j in range(3):
            center = path[j + 1]
            tangent = (path[j + 1] - path[j]).normalized()
            # Parallel transport avoids a 180 degree frame flip when a curled
            # fingertip passes vertical. A world-up cross product would tear it.
            x = (transported_x - tangent * transported_x.dot(tangent)).normalized()
            z = x.cross(tangent).normalized();transported_x = x
            digit_width = width * .625 * (x1 - x0) * (1 - .08 * j) if not thumb else .028 * (1 - .08 * j)
            height = .025 * (1 - .09 * j)
            influence = {names[j]: 1}
            if j < 2:
                influence = {names[j]: .5, names[j + 1]: .5}
            current = [vertex(center - x * digit_width / 2 + z * height / 2, influence),
                       vertex(center + x * digit_width / 2 + z * height / 2, influence),
                       vertex(center + x * digit_width / 2 - z * height / 2, influence),
                       vertex(center - x * digit_width / 2 - z * height / 2, influence)]
            for k in range(4):
                # Terminal skin pixels continue across all articulated finger faces.
                direction = ('front', 'left', 'back', 'right')[k]
                face([previous[k], previous[(k + 1) % 4], current[(k + 1) % 4], current[k]],
                     [uv(direction, x0, 0, 11 + j / 3), uv(direction, x1, 1, 11 + j / 3),
                      uv(direction, x1, 1, 11 + (j + 1) / 3), uv(direction, x0, 0, 11 + (j + 1) / 3)])
            previous = current
        u, v = arm_uv
        face(previous, [((u + 4 + pixel_width + x * pixel_width) / 64, 1 - (v + depth * 4) / 64) for x, depth in [(x0, 0), (x1, 0), (x1, 1), (x0, 1)]])

    digits = {finger: (path, names) for finger, path, names in data['digits']}
    finger_order = ('index', 'middle', 'ring', 'little') if side == 'right' else ('little', 'ring', 'middle', 'index')
    for index, finger in enumerate(finger_order):
        k = 1 + index * 2
        root_indices = [last['front'][k], last['front'][k + 1], last['back'][k + 1], last['back'][k]]
        path, names = digits[finger]
        digit(root_indices, path, names, fractions[k], fractions[k + 1])
    for k in (0, 2, 4, 6, 8):
        face([last['front'][k], last['front'][k + 1], last['back'][k + 1], last['back'][k]],
             [uv('front', fractions[k], 0, 11), uv('front', fractions[k + 1], 0, 11), uv('back', fractions[k + 1], 0, 11), uv('back', fractions[k], 0, 11)])
    thumb_path, thumb_names = digits['thumb']
    digit(data['thumb_root'], thumb_path, thumb_names, 0, .22, True)

    def build(name, coords, polygons, groups, uvs, mat):
        mesh = bpy.data.meshes.new(name + '_mesh')
        mesh.from_pydata(coords, [], polygons)
        mesh.update()
        layer = mesh.uv_layers.new(name='Minecraft_64x64')
        for polygon, values in zip(mesh.polygons, uvs):
            for loop, value in zip(polygon.loop_indices, values):
                layer.data[loop].uv = value
        # UV loop data survives normal recalculation; no smart-project approximation.
        bm = bmesh.new();bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        nonmanifold = sum(not edge.is_manifold for edge in bm.edges)
        bm.to_mesh(mesh);bm.free()
        obj = bpy.data.objects.new(name, mesh);bpy.context.scene.collection.objects.link(obj)
        obj.parent = arm;obj.data.materials.append(mat)
        for bone in arm.data.bones:
            obj.vertex_groups.new(name=bone.name)
        for i, influence in enumerate(groups):
            total = sum(influence.values())
            for bone, value in influence.items():
                if value > 0:
                    obj.vertex_groups[bone].add([i], value / total, 'REPLACE')
        modifier = obj.modifiers.new('Blender_linear_skinning', 'ARMATURE');modifier.object = arm
        modifier.use_deform_preserve_volume = False
        obj['minecraft_arm_pixels'] = pixel_width
        obj['nonmanifold_edges'] = nonmanifold
        obj['weighted_bones'] = sorted({bone for influence in groups for bone, value in influence.items() if value > 0})
        return obj

    body = build('view_arm_' + side, vertices, faces, weights, uv_faces, material)
    # Vanilla sleeve dilation is 0.25 skin pixel. Open cuff: the separate shell
    # follows the same welded elbow weights and does not inflate the bare hand.
    sleeve_vertices, sleeve_weights, sleeve_faces, sleeve_uvs = [], [], [], []
    lookup = {}
    arm_ring_vertices = len(rings) * n * 2
    for polygon, coords in zip(faces, uv_faces):
        if any(i >= arm_ring_vertices for i in polygon):
            continue
        if max(1 - v for _, v in coords) * 64 - arm_uv[1] - 4 > 9 + 1e-4:
            continue
        out = []
        for i in polygon:
            if i not in lookup:
                # Only arm rings are used. Dilation perpendicular to the centreline.
                r = next(r for r in rings if i in r['front'] or i in r['back'])
                delta = vertices[i] - r['center']
                dx = delta.dot(r['x']);dz = delta.dot(r['z'])
                position = vertices[i] + r['x'] * (PIXEL / 4 if dx > 0 else -PIXEL / 4) + r['z'] * (PIXEL / 4 if dz > 0 else -PIXEL / 4)
                if r is rings[0]:
                    position -= (e - s).normalized() * PIXEL / 4
                lookup[i] = len(sleeve_vertices);sleeve_vertices.append(position);sleeve_weights.append(weights[i])
            out.append(lookup[i])
        sleeve_faces.append(out)
        du, dv = sleeve_uv[0] - arm_uv[0], sleeve_uv[1] - arm_uv[1]
        sleeve_uvs.append([(u + du / 64, v - dv / 64) for u, v in coords])
    sleeve = build('view_sleeve_' + side, sleeve_vertices, sleeve_faces, sleeve_weights, sleeve_uvs, overlay_material)
    return body, sleeve
