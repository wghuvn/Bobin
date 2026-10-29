package com.bobincraft.armament.client.model.glb;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.WeaponModelAsset;
import java.util.List;

/** Immutable, CPU-only static scene. Coordinates are glTF Y-up metres, one metre per block. */
public record GlbModelAsset(WeaponId id, List<Primitive> primitives, List<Material> materials,
        List<Image> images, List<Texture> textures, Bounds bounds) implements WeaponModelAsset {
    public GlbModelAsset {
        primitives = List.copyOf(primitives); materials = List.copyOf(materials);
        images = List.copyOf(images); textures = List.copyOf(textures);
    }

    public record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {}
    public record Primitive(String nodeName, List<Vertex> vertices, List<Integer> indices, int material) {
        public Primitive { vertices = List.copyOf(vertices); indices = List.copyOf(indices); }
        public int triangles() { return indices.size() / 3; }
    }
    public enum Alpha { OPAQUE, MASK, BLEND }
    public record Color(float r, float g, float b, float a) {}
    public record Material(String name, Color baseColor, int texture, Alpha alpha, float cutoff,
            boolean doubleSided, float metallic, float roughness) {}
    public record Texture(int image, int wrapS, int wrapT, int minFilter, int magFilter) {}
    public record Image(byte[] encoded, int width, int height, String mimeType) {
        public Image { encoded = encoded.clone(); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float width() { return maxX - minX; }
        public float height() { return maxY - minY; }
        public float depth() { return maxZ - minZ; }
    }
    public int triangles() { return primitives.stream().mapToInt(Primitive::triangles).sum(); }
}
