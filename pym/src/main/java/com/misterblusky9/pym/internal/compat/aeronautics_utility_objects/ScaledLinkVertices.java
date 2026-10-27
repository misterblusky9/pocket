package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ScaledLinkVertices implements VertexConsumer {
    private final VertexConsumer output;
    private final float px, py, pz, scale;

    public static VertexConsumer wrap(VertexConsumer output, PoseStack pose, Vec3 pivot, double scale) {
        if (pivot == null || !Double.isFinite(scale) || scale <= 0.0D || scale == 1.0D) return output;
        Vector3f p = pose.last().pose().transformPosition(
                new Vector3f((float) pivot.x, (float) pivot.y, (float) pivot.z));
        return new ScaledLinkVertices(output, p, (float) scale);
    }

    private ScaledLinkVertices(VertexConsumer output, Vector3f pivot, float scale) {
        this.output = output;
        this.px = pivot.x;
        this.py = pivot.y;
        this.pz = pivot.z;
        this.scale = scale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        output.addVertex(px + (x - px) * scale, py + (y - py) * scale, pz + (z - pz) * scale);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        output.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        output.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        output.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        output.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        output.setNormal(x, y, z);
        return this;
    }
}
