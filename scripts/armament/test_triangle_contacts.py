"""Independent positive/negative narrow-phase controls; no authored JSON expected values.
Run: python3 scripts/armament/test_triangle_contacts.py
"""
import math
import unittest
from triangle_contacts import triangles_intersect, nonadjacent_contacts


class TriangleContactTest(unittest.TestCase):
    def test_coplanar_separated_strips_remain_separate_after_rigid_rotation(self):
        a = [(0, 0, 0), (0, .03, 0), (0, .03, .035)]
        b = [(0, .030703125, 0), (0, .06, .035), (0, .030703125, .035)]
        for angle in (0, .0793, 1.2, 2.7):
            def transform(p):
                x, y, z = p
                return (x + .09475, math.cos(angle) * y - math.sin(angle) * z - .06,
                        math.sin(angle) * y + math.cos(angle) * z - .33)
            self.assertFalse(triangles_intersect(list(map(transform, a)), list(map(transform, b))))

    def test_actual_blender_alex_false_positive_has_positive_separating_gap(self):
        # Copied evaluated positions from Alex lower@1.09375, BVH pair 282/287.
        a = [(.09474996477365494, -.05643582344055176, -.32841917872428894),
             (.09474996477365494, -.05859053507447243, -.35552114248275757),
             (.09474995732307434, -.02370063215494156, -.3582950234413147)]
        b = [(.09474996477365494, -.05864626169204712, -.3562220633029938),
             (.09474995732307434, -.026022523641586304, -.38749974966049194),
             (.09474995732307434, -.0237563569098711, -.3589959442615509)]
        self.assertFalse(triangles_intersect(a, b))

    def test_coplanar_overlapping_and_touching_triangles_are_contacts(self):
        a = [(0, 0, 0), (1, 0, 0), (0, 1, 0)]
        for b in [[(.2, .2, 0), (.8, .2, 0), (.2, .8, 0)],
                  [(1, 0, 0), (2, 0, 0), (1, 1, 0)],
                  [(0, 0, 0), (0, 1, 0), (1, 0, 0)]]:
            self.assertTrue(triangles_intersect(a, b))

    def test_non_coplanar_penetration_is_retained_and_parallel_planes_separate(self):
        a = [(0, 0, 0), (1, 0, 0), (0, 1, 0)]
        self.assertTrue(triangles_intersect(a, [(.25, .25, -1), (.25, .25, 1), (.75, .25, 0)]))
        self.assertFalse(triangles_intersect(a, [(0, 0, .001), (1, 0, .001), (0, 1, .001)]))

    def test_numerical_uncertainty_counts_as_contact(self):
        a = [(0, 0, 0), (1, 0, 0), (0, 1, 0)]
        self.assertTrue(triangles_intersect(a, [(0, 0, 5e-8), (1, 0, 5e-8), (0, 1, 5e-8)]))

    def test_broadphase_filter_keeps_real_hit_and_preserves_adjacency_exclusion(self):
        points = [(0, 0, 0), (1, 0, 0), (0, 1, 0),
                  (.25, .25, -1), (.25, .25, 1), (.75, .25, 0),
                  (2, 0, 0), (3, 0, 0), (2, 1, 0)]
        triangles = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 1, 5]]
        self.assertEqual([(0, 1)], nonadjacent_contacts(points, triangles, [(0, 1), (1, 0), (0, 2), (0, 3)]))


if __name__ == '__main__':
    unittest.main()
