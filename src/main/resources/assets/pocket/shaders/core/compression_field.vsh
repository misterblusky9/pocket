#version 150

in vec3 Position;   // Local
in vec2 UV0;        // X = surface distance from contact point
in vec4 Color;      // RGB = face normal, encoded 0..1

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FaceOffset;
out float vDistance;
out vec3 vLocalPosition;

void main() {
    vec3 normal = Color.rgb * 2.0 - 1.0;
    vec3 offsetPosition = Position + normal * FaceOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(offsetPosition, 1.0);
    vDistance = UV0.x;
    vLocalPosition = Position;
}
