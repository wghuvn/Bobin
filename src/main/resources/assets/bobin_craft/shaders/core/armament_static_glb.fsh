#version 150

uniform sampler2D Sampler0;
uniform vec4 BaseColorFactor;
uniform int AlphaMode;
uniform float AlphaCutoff;

in vec4 vertexColor;
in vec2 texCoord;
in vec3 worldNormal;
in vec3 blockLight;
out vec4 fragColor;

vec3 toLinear(vec3 value) {
    return mix(value / 12.92, pow((value + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), value));
}
vec3 toSrgb(vec3 value) {
    value = max(value, vec3(0.0));
    return mix(value * 12.92, 1.055 * pow(value, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), value));
}
void main() {
    vec4 texel = texture(Sampler0, texCoord);
    float alpha = texel.a * BaseColorFactor.a * vertexColor.a;
    if (AlphaMode == 1 && alpha < AlphaCutoff) discard;
    if (AlphaMode != 2) alpha = 1.0;
    vec3 n = normalize(worldNormal);
    if (!gl_FrontFacing) n = -n;
    // Deliberately small vanilla-style diffuse profile; metallic/roughness metadata
    // is retained by the loader but this shader does not implement physical PBR.
    float diffuse = 0.4 + 0.6 * max(dot(n, normalize(vec3(0.3, 1.0, 0.4))), 0.0);
    vec3 linearColor = toLinear(texel.rgb) * BaseColorFactor.rgb * vertexColor.rgb * diffuse * toLinear(blockLight);
    fragColor = vec4(toSrgb(linearColor), alpha);
}
