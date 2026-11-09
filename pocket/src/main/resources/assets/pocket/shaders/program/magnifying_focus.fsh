#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform vec2 OutSize;
uniform float FocusStart;
uniform float FocusEnd;
uniform float NearPlane;
uniform float FarPlane;
uniform float BlurStrength;

in vec2 texCoord;
out vec4 fragColor;

float distanceAt(vec2 uv) {
    float depth = texture(DiffuseDepthSampler, uv).r;
    return (NearPlane * FarPlane) / max(NearPlane, FarPlane - depth * (FarPlane - NearPlane));
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    float distance = distanceAt(texCoord);
    float blend = smoothstep(FocusStart, FocusEnd, distance) * BlurStrength;
    if (blend < 0.001) {
        fragColor = original;
        return;
    }

    vec2 directions[8] = vec2[](
        vec2(1.0, 0.0), vec2(-1.0, 0.0),
        vec2(0.0, 1.0), vec2(0.0, -1.0),
        vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071),
        vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071)
    );
    vec3 color = original.rgb * 0.25;
    float weight = 0.25;
    float radius = mix(2.0, 7.0, blend);
    vec2 stepSize = vec2(radius) / OutSize;
    for (int i = 0; i < 8; i++) {
        vec2 uv = clamp(texCoord + directions[i] * stepSize, vec2(0.0), vec2(1.0));
        float sampleDistance = distanceAt(uv);
        float visible = smoothstep(distance * 0.75, distance, sampleDistance);
        float w = 0.09375 * visible;
        color += texture(DiffuseSampler, uv).rgb * w;
        weight += w;
    }
    fragColor = vec4(mix(original.rgb, color / weight, blend), original.a);
}
