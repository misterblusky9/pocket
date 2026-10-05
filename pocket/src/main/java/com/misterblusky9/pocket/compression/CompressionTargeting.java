package com.misterblusky9.pocket.compression;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CompressionTargeting {
    public record Target(ServerSubLevel subLevel, BlockPos hitLocalPos) {}

    private CompressionTargeting() {}

    public static Target find(final Player player, final double range) {
        final Level level = player.level();
        final Vec3 eye = player.getEyePosition();
        final Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));

        final BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));

        if (hit == null || hit.getType() == HitResult.Type.MISS) return null;

        final BlockPos pos = hit.getBlockPos();
        final SubLevel found = Sable.HELPER.getContaining(level, pos);
        if (!(found instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) return null;

        return new Target(subLevel, pos);
    }
}
