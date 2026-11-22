#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform vec2 OutSize;

uniform float LensCX;
uniform float LensCY;
uniform float LensUX;
uniform float LensUY;
uniform float LensVX;
uniform float LensVY;
uniform float LensDepth;
uniform float DepthOcclusion;
uniform float LensOpacity;
uniform float LensPower;
uniform float FocusStart;
uniform float FocusEnd;
uniform float NearPlane;
uniform float FarPlane;

in vec2 texCoord;
out vec4 fragColor;

const int TAPS = 20;
const float GOLDEN_ANGLE = 2.39996323;
const float MAX_RADIUS_AT_1080 = 5.0;

float sceneDistance(vec2 uv) {
    float depth = texture(DiffuseDepthSampler, uv).r;
    return (NearPlane * FarPlane) / max(NearPlane, FarPlane - depth * (FarPlane - NearPlane));
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    vec2 delta = texCoord - vec2(LensCX, LensCY);
    vec2 u = vec2(LensUX, LensUY);
    vec2 v = vec2(LensVX, LensVY);
    float det = u.x * v.y - u.y * v.x;
    if (abs(det) < 0.0000001 || LensOpacity < 0.001) {
        fragColor = scene;
        return;
    }

    vec2 aperture = vec2(delta.x * v.y - delta.y * v.x,
                         u.x * delta.y - u.y * delta.x) / det;
    float r = length(aperture);
    if (r >= 0.91) {
        fragColor = scene;
        return;
    }

    if (DepthOcclusion > 0.5 &&
        texture(DiffuseDepthSampler, texCoord).r + 0.0007 < LensDepth) {
        fragColor = scene;
        return;
    }

    float feather = 1.0 - smoothstep(0.84, 0.91, r);
    float mixAmount = feather * LensOpacity;
    float curvature = 1.0 - 0.26 * r * r;
    float magnification = 1.0 + LensPower * curvature;
    vec2 src = vec2(LensCX, LensCY) + delta / magnification;
    src = clamp(src, vec2(0.0), vec2(1.0));

    float defocus = smoothstep(FocusStart, FocusEnd, sceneDistance(src));
    float radius = defocus * MAX_RADIUS_AT_1080 * OutSize.y / 1080.0;
    vec3 colour = texture(DiffuseSampler, src).rgb;
    if (radius >= 0.5) {
        float total = 1.0;
        for (int i = 1; i <= TAPS; i++) {
            float a = float(i) * GOLDEN_ANGLE;
            float stepRadius = sqrt(float(i) / float(TAPS)) * radius;
            vec2 tapUV = clamp(src + vec2(cos(a), sin(a)) * stepRadius / OutSize,
                               vec2(0.0), vec2(1.0));
            float reach = smoothstep(FocusStart, FocusEnd, sceneDistance(tapUV)) * radius;
            float w = clamp(reach - stepRadius + 1.0, 0.0, 1.0);
            colour += texture(DiffuseSampler, tapUV).rgb * w;
            total += w;
        }
        colour /= total;
    }
    float fringe = 0.65 * r * r / OutSize.y;
    vec2 rimVector = normalize(delta + vec2(0.00001)) * fringe;
    vec3 fringeColour = vec3(texture(DiffuseSampler, clamp(src + rimVector, vec2(0), vec2(1))).r,
                             colour.g,
                             texture(DiffuseSampler, clamp(src - rimVector, vec2(0), vec2(1))).b);
    colour = mix(colour, fringeColour, 0.22 * r);
    fragColor = vec4(mix(scene.rgb, colour, mixAmount), scene.a);
}
