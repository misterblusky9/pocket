package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class EntityRenderScale {
    public static Vec3 pivot(final Entity entity, final float partialTick) {
        final Vec3 feet = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        return switch (Pym.entities().backend(entity)) {
            case PEHKUI -> feet.add(Minecraft.getInstance().getEntityRenderDispatcher()
                    .getRenderer(entity).getRenderOffset(entity, partialTick));
            case NATIVE_STATIC -> feet.add(0.0D, EntityScaleTracker.nativePivotHeight(entity), 0.0D);
            case UNAVAILABLE -> feet;
        };
    }

    public static Matrix4f apply(final Entity entity, final float partialTick, final Vec3 origin, final Matrix4f pose) {
        final Matrix4f scaling = scaling(entity, partialTick, origin);
        return scaling == null ? pose : pose.mulLocal(scaling);
    }

    public static PoseStack apply(final Entity entity, final float partialTick, final Vec3 origin, final PoseStack poseStack) {
        final Matrix4f scaling = scaling(entity, partialTick, origin);
        if (scaling != null) poseStack.last().pose().mulLocal(scaling);
        return poseStack;
    }

    private static Matrix4f scaling(final Entity entity, final float partialTick, final Vec3 origin) {
        if (entity == null) return null;
        final float scale = (float) Pym.entities().renderScale(entity, partialTick);
        if (!ScaleBounds.isValid(scale) || ScaleBounds.same(scale, ScaleBounds.FULL)) return null;
        final Vec3 pivot = pivot(entity, partialTick).subtract(origin);
        final float x = (float) pivot.x;
        final float y = (float) pivot.y;
        final float z = (float) pivot.z;
        return new Matrix4f().translation(x, y, z).scale(scale).translate(-x, -y, -z);
    }

    private EntityRenderScale() {}
}
