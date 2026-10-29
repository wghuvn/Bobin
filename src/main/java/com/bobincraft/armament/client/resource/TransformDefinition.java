package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.validation.Checks;
import java.util.List;

/** Data only: metres, quaternion XYZW, positive scale. No transform evaluation. */
public record TransformDefinition(List<Double> translation, List<Double> rotation, List<Double> scale) {
    public TransformDefinition {
        vector(translation, 3, -1024, 1024, "$.translation");
        vector(rotation, 4, -1, 1, "$.rotation");
        vector(scale, 3, 0, 1024, "$.scale");
        Checks.require(scale.stream().allMatch(v -> v > 0), "$.scale", "components must be positive");
        double norm = rotation.stream().mapToDouble(v -> v * v).sum();
        Checks.require(Math.abs(norm - 1) <= 0.00001, "$.rotation", "quaternion must be normalized (squared norm tolerance 1e-5)");
        translation = List.copyOf(translation);
        rotation = List.copyOf(rotation);
        scale = List.copyOf(scale);
    }

    private static void vector(List<Double> values, int count, double min, double max, String path) {
        Checks.size(values, count, count, path);
        for (int i = 0; i < values.size(); i++)
            Checks.require(Double.isFinite(values.get(i)) && values.get(i) >= min && values.get(i) <= max,
                    path + "[" + i + "]", "expected finite number in " + min + ".." + max);
    }
}
