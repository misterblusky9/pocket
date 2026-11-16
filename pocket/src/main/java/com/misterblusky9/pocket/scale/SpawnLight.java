package com.misterblusky9.pocket.scale;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.function.ToIntFunction;

public final class SpawnLight {
    public static int brightness(ServerLevelAccessor accessor, LightLayer layer, BlockPos pos, int plot) {
        BlockPos world = worldCell(accessor, pos);
        if (world == null) return plot;
        int outside = accessor.getLevel().getBrightness(layer, world);
        return layer == LightLayer.SKY ? Math.min(plot, outside) : Math.max(plot, outside);
    }

    public static int raw(ServerLevelAccessor accessor, BlockPos pos, int plot, ToIntFunction<BlockPos> sample) {
        BlockPos world = worldCell(accessor, pos);
        if (world == null) return plot;
        ServerLevel level = accessor.getLevel();
        int block = Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.BLOCK, world));
        return Math.max(Math.min(plot, sample.applyAsInt(world)), block);
    }

    private static BlockPos worldCell(ServerLevelAccessor accessor, BlockPos pos) {
        ServerLevel level = accessor.getLevel();
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null || subLevel.isRemoved()) return null;
        Vec3 world = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos));
        return BlockPos.containing(world);
    }

    private SpawnLight() {}
}
