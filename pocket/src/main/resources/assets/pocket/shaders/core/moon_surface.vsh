#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 vUv;
out vec3 vLocalPosition;
out vec3 vLocalNormal;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
    vLocalPosition = Position;
    vLocalNormal = normalize(Color.rgb * 2.0 - 1.0);
}
