"""Conservative double-precision narrow phase for Blender BVH candidates.

Blender's float BVH can report separated, nearly coplanar strips on a palm as
contacts after a common rigid transform. Keep it as the broad phase, then prove
separation with triangle SAT. All axes are tested in metres after normalization;
uncertainty within 0.1 micrometre remains a contact, never a silently ignored hit.
This module has no Blender dependency so its positive/negative controls run on CPU.
"""
import math

SEPARATION_TOLERANCE_METRES = 1e-7


def _subtract(a, b):
    return tuple(float(a[i]) - float(b[i]) for i in range(3))


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return math.fsum(float(x) * float(y) for x, y in zip(a, b))


def triangles_intersect(first, second):
    """Return False only when a separating axis proves the triangles disjoint.

    The usual two face normals and nine edge cross-products cover noncoplanar
    triangles. Six additional in-plane edge normals distinguish separated
    coplanar triangles that a BVH candidate query may conservatively overlap.
    Touching and sub-tolerance separation conservatively count as contact.
    """
    a = [tuple(float(c) for c in point) for point in first]
    b = [tuple(float(c) for c in point) for point in second]
    if len(a) != 3 or len(b) != 3 or any(len(p) != 3 or not all(map(math.isfinite, p)) for p in a + b):
        raise ValueError('triangle contact requires two finite 3D triangles')
    ae = [_subtract(a[(i + 1) % 3], a[i]) for i in range(3)]
    be = [_subtract(b[(i + 1) % 3], b[i]) for i in range(3)]
    an, bn = _cross(ae[0], ae[1]), _cross(be[0], be[1])
    axes = [an, bn] + [_cross(x, y) for x in ae for y in be]
    axes += [_cross(an, edge) for edge in ae] + [_cross(bn, edge) for edge in be]
    for axis in axes:
        length = math.sqrt(_dot(axis, axis))
        if length == 0:
            continue
        pa, pb = [_dot(axis, p) for p in a], [_dot(axis, p) for p in b]
        gap = max(min(pb) - max(pa), min(pa) - max(pb))
        if gap > SEPARATION_TOLERANCE_METRES * length:
            return False
    return True


def nonadjacent_contacts(points, triangles, candidates):
    """Retain actual nonadjacent contacts from BVH triangle-index pairs."""
    return [(a, b) for a, b in candidates
            if a < b and not set(triangles[a]).intersection(triangles[b])
            and triangles_intersect([points[i] for i in triangles[a]],
                                    [points[i] for i in triangles[b]])]
