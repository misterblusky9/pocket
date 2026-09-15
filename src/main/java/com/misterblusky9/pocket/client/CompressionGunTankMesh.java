package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

final class CompressionGunTankMesh {
    private record Corner(float x, float y, float z, float u, float v) {}

    private record Face(Corner[] corners, float nx, float ny, float nz) {}

    private static BakedModel source;
    private static List<Face> faces = List.of();
    private static float minY;
    private static float maxY;

    private CompressionGunTankMesh() {}

    static void render(final BakedModel model, final float fill, final PoseStack ms,
                       final MultiBufferSource buffer, final int light) {
        final List<Face> mesh = faces(model);
        if (mesh.isEmpty() || !(fill > 0.001F)) return;

        final float top = Mth.lerp(Math.min(1.0F, fill), minY, maxY);
        final VertexConsumer consumer = buffer.getBuffer(Sheets.translucentItemSheet());
        final PoseStack.Pose pose = ms.last();

        for (final Face face : mesh) {
            if (face.ny() > 0.5F || face.ny() < -0.5F) {
                final boolean surface = face.ny() > 0.5F;
                for (final Corner corner : face.corners()) {
                    vertex(consumer, pose, light, face,
                            corner.x(), surface ? top : corner.y(), corner.z(), corner.u(), corner.v());
                }
                continue;
            }
            for (final Corner corner : face.corners()) {
                if (corner.y() <= top) {
                    vertex(consumer, pose, light, face, corner.x(), corner.y(), corner.z(), corner.u(), corner.v());
                    continue;
                }
                final Corner below = below(face, corner);
                final float t = below == null || corner.y() == below.y()
                        ? 1.0F
                        : (top - below.y()) / (corner.y() - below.y());
                vertex(consumer, pose, light, face,
                        corner.x(), top, corner.z(),
                        below == null ? corner.u() : Mth.lerp(t, below.u(), corner.u()),
                        below == null ? corner.v() : Mth.lerp(t, below.v(), corner.v()));
            }
        }
    }

    private static Corner below(final Face face, final Corner corner) {
        for (final Corner other : face.corners()) {
            if (other != corner && other.y() < corner.y()
                    && Math.abs(other.x() - corner.x()) < 1.0E-4F
                    && Math.abs(other.z() - corner.z()) < 1.0E-4F) {
                return other;
            }
        }
        return null;
    }

    private static void vertex(
            final VertexConsumer consumer,
            final PoseStack.Pose pose,
            final int light,
            final Face face,
            final float x,
            final float y,
            final float z,
            final float u,
            final float v
    ) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, z - 0.5F)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, face.nx(), face.ny(), face.nz());
    }

    private static List<Face> faces(final BakedModel model) {
        if (model == source) return faces;
        source = model;

        final RandomSource random = RandomSource.create(42L);
        final List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
        for (final Direction direction : Direction.values()) quads.addAll(model.getQuads(null, direction, random));

        final List<Face> parsed = new ArrayList<>(quads.size());
        minY = Float.POSITIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        for (final BakedQuad quad : quads) {
            final int[] data = quad.getVertices();
            final Corner[] corners = new Corner[4];
            for (int i = 0; i < 4; i++) {
                final int p = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                final int t = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                corners[i] = new Corner(
                        Float.intBitsToFloat(data[p]), Float.intBitsToFloat(data[p + 1]), Float.intBitsToFloat(data[p + 2]),
                        Float.intBitsToFloat(data[t]), Float.intBitsToFloat(data[t + 1]));
                minY = Math.min(minY, corners[i].y());
                maxY = Math.max(maxY, corners[i].y());
            }
            final var normal = quad.getDirection().getNormal();
            parsed.add(new Face(corners, normal.getX(), normal.getY(), normal.getZ()));
        }
        faces = List.copyOf(parsed);
        return faces;
    }
}
