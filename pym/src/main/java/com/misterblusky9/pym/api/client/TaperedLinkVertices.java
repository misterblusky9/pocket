package com.misterblusky9.pym.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class TaperedLinkVertices implements VertexConsumer {
    private final VertexConsumer output;
    private final float ax, ay, az, ux, uy, uz, length;
    private final float startRadius, endRadius;
    private float radialX, radialY, radialZ, radius = 1.0F, derivative;

    public static VertexConsumer wrap(
            VertexConsumer output, PoseStack pose,
            Vec3 start, Vec3 end,
            double startRadius, double endRadius
    ) {
        if (!TaperedLinkProfile.ENABLED) return output;
        if (!Double.isFinite(startRadius) || !Double.isFinite(endRadius)
                || startRadius <= 0 || endRadius <= 0 || startRadius == endRadius) return output;

        Vector3f a = pose.last().pose().transformPosition(
                new Vector3f((float) start.x, (float) start.y, (float) start.z));
        Vector3f b = pose.last().pose().transformPosition(
                new Vector3f((float) end.x, (float) end.y, (float) end.z));
        float distance = b.distance(a);
        if (!(distance > 1.0E-6F) || !Float.isFinite(distance)) return output;
        return new TaperedLinkVertices(output, a, b, (float) startRadius, (float) endRadius, distance);
    }

    private TaperedLinkVertices(
            VertexConsumer output, Vector3f a, Vector3f b,
            float startRadius, float endRadius, float length
    ) {
        this.output = output;
        this.ax = a.x;
        this.ay = a.y;
        this.az = a.z;
        this.ux = (b.x - a.x) / length;
        this.uy = (b.y - a.y) / length;
        this.uz = (b.z - a.z) / length;
        this.length = length;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        float dx = x - ax, dy = y - ay, dz = z - az;
        float along = dx * ux + dy * uy + dz * uz;
        float t = Math.max(0.0F, Math.min(1.0F, along / length));
        this.radius = startRadius + (endRadius - startRadius) * t;
        this.derivative = t > 0.0F && t < 1.0F ? (endRadius - startRadius) / length : 0.0F;
        this.radialX = dx - ux * along;
        this.radialY = dy - uy * along;
        this.radialZ = dz - uz * along;
        output.addVertex(ax + ux * along + radialX * radius,
                ay + uy * along + radialY * radius,
                az + uz * along + radialZ * radius);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        float parallel = x * ux + y * uy + z * uz;
        float px = x - parallel * ux;
        float py = y - parallel * uy;
        float pz = z - parallel * uz;
        float axial = parallel - derivative * (px * radialX + py * radialY + pz * radialZ) / radius;
        float nx = px / radius + axial * ux;
        float ny = py / radius + axial * uy;
        float nz = pz / radius + axial * uz;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-8F) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        output.setNormal(nx, ny, nz);
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
}
