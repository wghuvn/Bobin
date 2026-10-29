#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 GlbViewProjection;
uniform sampler2D Sampler2;

out vec4 vertexColor;
out vec2 texCoord;
out vec3 worldNormal;
out vec3 blockLight;

void main() {
    gl_Position = GlbViewProjection * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0; // glTF UV0 already matches the top-left image origin; no V flip.
    worldNormal = Normal;
    blockLight = texture(Sampler2, clamp(vec2(UV2) / 256.0 + vec2(0.03125), vec2(0.03125), vec2(0.96875))).rgb;
}
