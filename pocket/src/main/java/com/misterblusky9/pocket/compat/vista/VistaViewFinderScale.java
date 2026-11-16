package com.misterblusky9.pocket.compat.vista;

import com.misterblusky9.pym.internal.client.ProjectionNearPlanePolicy;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class VistaViewFinderScale {
    private VistaViewFinderScale() {}

    public static double of(BlockEntity camera) {
        if (!(camera instanceof ViewFinderBlockEntity) || camera.getLevel() == null) return 1.0D;
        SubLevel part = Sable.HELPER.getContaining(camera.getLevel(), camera.getBlockPos());
        if (part == null || part.isRemoved()) return 1.0D;
        double scale = part instanceof ClientSubLevel client
                ? client.renderPose(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)).scale().x()
                : part.logicalPose().scale().x();
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    public static float near(float near, double scale) {
        return ProjectionNearPlanePolicy.compose(near, scale);
    }
}
