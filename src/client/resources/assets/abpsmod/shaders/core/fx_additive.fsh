#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

// Additive light for AbpsMod effects. The vanilla particle shader mixes every pixel toward the fog color, which
// turns the black (empty) part of a glow sprite into a faint visible square once it is added to the screen. Light
// should fade toward nothing in fog instead, and should never be cut off at a hard alpha threshold.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd);
    vec3 light = color.rgb * color.a * (1.0 - fog);
    if (max(light.r, max(light.g, light.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(light, 1.0);
}
