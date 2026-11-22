#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform vec2 OutSize;
uniform float FocusStart;
uniform float FocusEnd;
uniform float NearPlane;
uniform float FarPlane;
uniform float BlurStrength;
uniform float ReverseDepth;

in vec2 texCoord;
out vec4 fragColor;

const int TAPS = 36;
const float GOLDEN_ANGLE = 2.39996323;
const float MAX_RADIUS_AT_1080 = 14.0;

float depth01(vec2 uv) {
    float rawDepth = texture(DiffuseDepthSampler, uv).r;
    return clamp(mix(rawDepth, 1.0 - rawDepth, ReverseDepth), 0.0, 1.0);
}

float viewDistance(float depth) {
    if (depth >= 0.999999) return FarPlane;
    float denom = FarPlane - depth * (FarPlane - NearPlane);
    return NearPlane * FarPlane / max(denom, NearPlane);
}

float defocus(float distance) {
    return smoothstep(FocusStart, FocusEnd, distance);
}

float sampleWeight(float sampleDistance, float centerDistance,
                   float sampleDefocus, float centerDefocus, float fraction) {
    float separation = abs(sampleDistance - centerDistance) /
                       max(min(sampleDistance, centerDistance), FocusEnd * 0.5);
    float depthGate = 1.0 - smoothstep(0.12, 0.48, separation);

    float focusGate = 1.0 - smoothstep(0.18, 0.72,
                                     abs(sampleDefocus - centerDefocus));
    float spatial = exp(-1.8 * fraction);
    return spatial * depthGate * focusGate;
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    float centerDistance = viewDistance(depth01(texCoord));
    float centerDefocus = defocus(centerDistance);
    float strength = clamp(BlurStrength, 0.0, 1.0);
    float radius = MAX_RADIUS_AT_1080 * (OutSize.y / 1080.0) *
                   centerDefocus * strength;

    if (radius < 0.25) {
        fragColor = original;
        return;
    }

    vec3 sumColor = original.rgb * 0.75;
    float sumWeight = 0.75;
    for (int i = 0; i < TAPS; ++i) {
        float fraction = (float(i) + 0.5) / float(TAPS);
        float r = sqrt(fraction) * radius;
        float angle = (float(i) + 1.0) * GOLDEN_ANGLE;
        vec2 delta = vec2(cos(angle), sin(angle)) * (r / OutSize);
        vec2 uv = clamp(texCoord + delta, vec2(0.0), vec2(1.0));
        float sampleDistance = viewDistance(depth01(uv));
        float sampleDefocus = defocus(sampleDistance);
        float weight = sampleWeight(sampleDistance, centerDistance,
                                    sampleDefocus, centerDefocus, fraction);
        if (weight > 0.0001) {
            sumColor += texture(DiffuseSampler, uv).rgb * weight;
            sumWeight += weight;
        }
    }

    float blend = smoothstep(0.2, 1.4, radius);
    fragColor = vec4(mix(original.rgb, sumColor / max(sumWeight, 0.0001), blend),
                     original.a);
}
