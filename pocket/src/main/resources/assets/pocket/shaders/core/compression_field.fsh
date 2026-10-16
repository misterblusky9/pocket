#version 150
in float vDistance;
in vec3 vLocalPosition;
uniform float FrontDistance;
uniform float FrontWidth;
uniform vec3 GridOrigin;
uniform float Halted;
uniform vec3 PulseOrigin;
uniform float PulseRadius;
uniform float PulseWidth;
uniform float PulseCell;
uniform float Strain;
uniform float ShimmerTime;
uniform vec4 SheenColor;
uniform vec4 FrontColor;
out vec4 fragColor;
void main() {
    float behind = FrontDistance - vDistance;
    if (behind < 0.0) discard;
    float edgeActivity = Strain < 0.0 ? clamp(1.0 + Strain, 0.0, 1.0) : 1.0;
    float visibleStrain = max(Strain, 0.0);
    float keepalive = (min(abs(ShimmerTime), 1.0) + abs(Halted) + length(GridOrigin) * 0.000001) * 0.00000001;
    vec4 color = SheenColor;
    color.a *= 1.0 + visibleStrain;
    color.a += keepalive;
    float edge = 1.0 - clamp(behind / max(FrontWidth, 0.001), 0.0, 1.0);
    float frontStrength = 0.72 + 0.28 * edgeActivity;
    color = mix(color, FrontColor, edge * edge * frontStrength);
    float rim = pow(edge, 12.0) * 0.92;
    color.rgb = mix(color.rgb, vec3(1.0), rim * 0.92);
    color.a = max(color.a, rim);
    if (PulseRadius >= 0.0) {
        float pulseCell = max(PulseCell, 0.001);
        vec3 cell = (floor(vLocalPosition / pulseCell) + 0.5) * pulseCell;
        float radius = length(cell - PulseOrigin);
        float band = 1.0 - clamp(abs(PulseRadius - radius) / max(PulseWidth, 0.001), 0.0, 1.0);
        float discharge = band * band;
        color.rgb = mix(color.rgb, vec3(1.0), discharge);
        color.a = max(color.a, discharge);
    }
    if (color.a <= 0.004) discard;
    fragColor = color;
}
