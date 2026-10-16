package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

final class ClipMesh {
    private record Corner(float x, float y, float z, float u, float v) {}

    private record Face(Corner[] corners, Direction direction) {}

    private final Direction.Axis axis;
    private BakedModel source;
    private List<Face> faces = List.of();
    private float min;
    private float max;

    ClipMesh(Direction.Axis axis) {
        this.axis = axis;
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
        List<Face> mesh = faces(model);
        if (mesh.isEmpty()) return;
        PoseStack.Pose pose = ms.last();

        for (Face face : mesh) {
            if (face.direction().getAxis() == axis) {
                boolean cap = face.direction().getAxisDirection() == Direction.AxisDirection.POSITIVE;
                for (Corner corner : face.corners()) {
                    vertex(consumer, pose, light, face, corner, cap ? cut : along(corner), corner.u(), corner.v());
                }
                continue;
            }
            for (Corner corner : face.corners()) {
                if (along(corner) <= cut) {
                    vertex(consumer, pose, light, face, corner, along(corner), corner.u(), corner.v());
                    continue;
                }
                Corner below = below(face, corner);
                float t = below == null || along(corner) == along(below)
                        ? 1.0F
                        : (cut - along(below)) / (along(corner) - along(below));
                vertex(consumer, pose, light, face, corner, cut,
                        below == null ? corner.u() : Mth.lerp(t, below.u(), corner.u()),
                        below == null ? corner.v() : Mth.lerp(t, below.v(), corner.v()));
            }
        }
    }

    private float along(Corner corner) {
        return switch (axis) {
            case X -> corner.x();
            case Y -> corner.y();
            case Z -> corner.z();
        };
    }

    private Corner below(Face face, Corner corner) {
        for (Corner other : face.corners()) {
            if (other == corner || along(other) >= along(corner)) continue;
            boolean sameX = axis == Direction.Axis.X || Math.abs(other.x() - corner.x()) < 1.0E-4F;
            boolean sameY = axis == Direction.Axis.Y || Math.abs(other.y() - corner.y()) < 1.0E-4F;
            boolean sameZ = axis == Direction.Axis.Z || Math.abs(other.z() - corner.z()) < 1.0E-4F;
            if (sameX && sameY && sameZ) return other;
        }
        return null;
    }

    private void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Face face,
            Corner corner,
            float at,
            float u,
            float v
    ) {
        float x = axis == Direction.Axis.X ? at : corner.x();
        float y = axis == Direction.Axis.Y ? at : corner.y();
        float z = axis == Direction.Axis.Z ? at : corner.z();
        var normal = face.direction().getNormal();
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, z - 0.5F)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal.getX(), normal.getY(), normal.getZ());
    }

    private List<Face> faces(BakedModel model) {
        if (model == source) return faces;
        source = model;

        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
        for (Direction direction : Direction.values()) quads.addAll(model.getQuads(null, direction, random));

        List<Face> parsed = new ArrayList<>(quads.size());
        min = Float.POSITIVE_INFINITY;
        max = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            Corner[] corners = new Corner[4];
            for (int i = 0; i < 4; i++) {
                int p = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                int t = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                corners[i] = new Corner(
                        Float.intBitsToFloat(data[p]), Float.intBitsToFloat(data[p + 1]), Float.intBitsToFloat(data[p + 2]),
                        Float.intBitsToFloat(data[t]), Float.intBitsToFloat(data[t + 1]));
                min = Math.min(min, along(corners[i]));
                max = Math.max(max, along(corners[i]));
            }
            parsed.add(new Face(corners, quad.getDirection()));
        }
        faces = List.copyOf(parsed);
        return faces;
    }
}
