#version 150

in float vDistance;
in vec3 vLocalPosition;

uniform float FrontDistance;
uniform float FrontWidth;

uniform vec3 PulseOrigin;
uniform float PulseRadius;
uniform float PulseWidth;

uniform float PulseCell;
uniform float Strain;
uniform vec4 SheenColor;
uniform vec4 FrontColor;

out vec4 fragColor;

void main() {
    // Wavefront
    float behind = FrontDistance - vDistance;
    if (behind < 0.0) discard;

    vec4 color = SheenColor;
    color.a *= 1.0 + Strain;

    // Trailing band
    float edge = 1.0 - clamp(behind / FrontWidth, 0.0, 1.0);
    color = mix(color, FrontColor, edge * edge);

    // Leading rim
    float rim = pow(edge, 12.0);
    color.rgb = mix(color.rgb, vec3(1.0), rim * 0.85);
    color.a = max(color.a, rim * 0.95);

    // Discharge wave
    vec3 cell = (floor(vLocalPosition / PulseCell) + 0.5) * PulseCell;
    float radius = length(cell - PulseOrigin);
    float band = 1.0 - clamp(abs(PulseRadius - radius) / PulseWidth, 0.0, 1.0);
    float discharge = band * band;
    color.rgb = mix(color.rgb, vec3(1.0), discharge);
    color.a = max(color.a, discharge);

    if (color.a <= 0.004) discard;
    fragColor = color;
}
