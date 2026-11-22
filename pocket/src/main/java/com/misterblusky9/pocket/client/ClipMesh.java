package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import net.createmod.ponder.mixin.client.accessor.RenderSystemAccessor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class ClipMesh {
    private static final float CAP_ALIGNMENT = 0.9F;
    private static final float PARALLEL_TOLERANCE = 1.0E-3F;

    private record Corner(float x, float y, float z, float u, float v) {}

    private record Face(Corner[] corners, Vector3f normal, float alignment) {}

    private final @Nullable Direction.Axis fixedAxis;
    private final Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F);
    private BakedModel source;
    private List<Face> faces = List.of();
    private float min;
    private float max;

    ClipMesh(Direction.Axis axis) {
        this.fixedAxis = axis;
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        this.axis.set(positive.getStepX(), positive.getStepY(), positive.getStepZ());
    }

    ClipMesh() {
        this.fixedAxis = null;
    }

    float min(BakedModel model) {
        faces(model);
        return min;
    }

    float max(BakedModel model) {
        faces(model);
        return max;
    }

    void render(BakedModel model, float cut, PoseStack ms,
                VertexConsumer consumer, int light) {
        render(model, cut, ms, consumer, light, false);
    }

    void render(BakedModel model, float cut, PoseStack ms,
                VertexConsumer consumer, int light, boolean diffuse) {
        List<Face> mesh = faces(model);
        if (mesh.isEmpty()) return;
        PoseStack.Pose pose = ms.last();
        boolean shade = diffuse && !ShadersModHelper.isShaderPackInUse();
        Vector3f[] lights = shade ? RenderSystemAccessor.catnip$getShaderLightDirections() : null;
        Vector3f turned = new Vector3f();

        for (Face face : mesh) {
            float tone = shade ? diffuse(pose.transformNormal(face.normal(), turned), lights) : 1.0F;
            if (Math.abs(face.alignment()) >= CAP_ALIGNMENT) {
                boolean cap = face.alignment() > 0.0F;
                for (Corner corner : face.corners()) {
                    vertex(consumer, pose, light, tone, face, corner, cap ? cut : along(corner), corner.u(), corner.v());
                }
                continue;
            }
            for (Corner corner : face.corners()) {
                if (along(corner) <= cut) {
                    vertex(consumer, pose, light, tone, face, corner, along(corner), corner.u(), corner.v());
                    continue;
                }
                Corner below = below(face, corner);
                float t = below == null || along(corner) == along(below)
                        ? 1.0F
                        : (cut - along(below)) / (along(corner) - along(below));
                vertex(consumer, pose, light, tone, face, corner, cut,
                        below == null ? corner.u() : Mth.lerp(t, below.u(), corner.u()),
                        below == null ? corner.v() : Mth.lerp(t, below.v(), corner.v()));
            }
        }
    }

    private static float diffuse(Vector3f normal, Vector3f[] lights) {
        float light0 = Math.max(0.0F, new Vector3f(lights[0]).normalize().dot(normal));
        float light1 = Math.max(0.0F, new Vector3f(lights[1]).normalize().dot(normal));
        return Math.min(1.0F, (light0 + light1) * 0.6F + 0.4F);
    }

    private float along(Corner corner) {
        return corner.x() * this.axis.x + corner.y() * this.axis.y + corner.z() * this.axis.z;
    }

    private @Nullable Corner below(Face face, Corner corner) {
        for (Corner other : face.corners()) {
            if (other == corner || along(other) >= along(corner)) continue;
            float drop = along(corner) - along(other);
            float ox = corner.x() - other.x() - this.axis.x * drop;
            float oy = corner.y() - other.y() - this.axis.y * drop;
            float oz = corner.z() - other.z() - this.axis.z * drop;
            if (ox * ox + oy * oy + oz * oz < PARALLEL_TOLERANCE * PARALLEL_TOLERANCE) return other;
        }
        return null;
    }

    private void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            float tone,
            Face face,
            Corner corner,
            float at,
            float u,
            float v
    ) {
        float shift = at - along(corner);
        float x = corner.x() + this.axis.x * shift;
        float y = corner.y() + this.axis.y * shift;
        float z = corner.z() + this.axis.z * shift;
        Vector3f normal = face.normal();
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, z - 0.5F)
                .setColor(tone, tone, tone, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    private List<Face> faces(BakedModel model) {
        if (model == source) return faces;
        source = model;

        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
        for (Direction direction : Direction.values()) quads.addAll(model.getQuads(null, direction, random));

        List<Corner[]> parsedCorners = new ArrayList<>(quads.size());
        List<Vector3f> normals = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            Corner[] corners = new Corner[4];
            for (int i = 0; i < 4; i++) {
                int p = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                int t = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                corners[i] = new Corner(
                        Float.intBitsToFloat(data[p]), Float.intBitsToFloat(data[p + 1]), Float.intBitsToFloat(data[p + 2]),
                        Float.intBitsToFloat(data[t]), Float.intBitsToFloat(data[t + 1]));
            }
            parsedCorners.add(corners);
            normals.add(normalOf(corners, quad.getDirection()));
        }

        if (this.fixedAxis == null) {
            Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
            float best = -2.0F;
            for (Vector3f normal : normals) {
                if (normal.y > best) {
                    best = normal.y;
                    up.set(normal);
                }
            }
            this.axis.set(up).normalize();
        }

        List<Face> parsed = new ArrayList<>(quads.size());
        min = Float.POSITIVE_INFINITY;
        max = Float.NEGATIVE_INFINITY;
        for (int q = 0; q < parsedCorners.size(); q++) {
            Corner[] corners = parsedCorners.get(q);
            for (Corner corner : corners) {
                min = Math.min(min, along(corner));
                max = Math.max(max, along(corner));
            }
            Vector3f normal = normals.get(q);
            parsed.add(new Face(corners, normal, normal.dot(this.axis)));
        }
        faces = List.copyOf(parsed);
        return faces;
    }

    private static Vector3f normalOf(Corner[] c, Direction fallback) {
        Vector3f a = new Vector3f(c[1].x() - c[0].x(), c[1].y() - c[0].y(), c[1].z() - c[0].z());
        Vector3f b = new Vector3f(c[2].x() - c[0].x(), c[2].y() - c[0].y(), c[2].z() - c[0].z());
        Vector3f n = a.cross(b);
        if (n.lengthSquared() < 1.0E-10F) {
            return new Vector3f(fallback.getStepX(), fallback.getStepY(), fallback.getStepZ());
        }
        n.normalize();
        Vector3f expected = new Vector3f(fallback.getStepX(), fallback.getStepY(), fallback.getStepZ());
        if (n.dot(expected) < 0.0F) n.negate();
        return n;
    }
}
