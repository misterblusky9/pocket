#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 TextureMat;
uniform mat4 PortraitInverse;
uniform vec3 ChunkOffset;
uniform float GlintUnit;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec3 plot = (PortraitInverse * ModelViewMat * vec4(pos, 1.0)).xyz;
    vec2 sheet = vec2((plot.x - plot.z) * 0.5, plot.y + (plot.x + plot.z) * 0.25) * GlintUnit;

    vertexDistance = fog_distance(pos, FogShape);
    texCoord0 = (TextureMat * vec4(sheet, 0.0, 1.0)).xy;
}
