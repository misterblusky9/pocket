package com.misterblusky9.pym.internal.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class CameraSubLevelScale {
    public static double current(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 1.0D;
        return forEntity(minecraft.cameraEntity, partialTick);
    }

    public static double forEntity(Entity entity, float partialTick) {
        if (entity == null) return 1.0D;

        SubLevel subLevel = Sable.HELPER.getContaining(entity);
        if (subLevel == null || subLevel.isRemoved()) {
            subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        }
        if (!(subLevel instanceof ClientSubLevel clientSubLevel) || clientSubLevel.isRemoved()) {
            return 1.0D;
        }

        double scale = clientSubLevel.renderPose(partialTick).scale().x();
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0D;

        return Math.min(scale, 1.0D);
    }

    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private CameraSubLevelScale() {}
}
