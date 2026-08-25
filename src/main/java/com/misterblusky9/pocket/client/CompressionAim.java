package com.misterblusky9.pocket.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class CompressionAim {
    public record Aim(ClientSubLevel subLevel, UUID subLevelId, Vec3 plotHit, BlockPos plotPos) {}

    private CompressionAim() {}

    public static Aim of(final Player player, final double range) {
        if (player == null) return null;

        final Level level = player.level();
        if (level == null || !level.isClientSide()) return null;

        final Vec3 eye = player.getEyePosition();
        final Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));

        final BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return null;

        final BlockPos pos = hit.getBlockPos();
        SubLevel found = Sable.HELPER.getContaining(level, hit.getLocation());
        if (found == null) found = Sable.HELPER.getContaining(level, pos);
        if (!(found instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return null;

        final UUID id = subLevel.getUniqueId();
        if (id == null) return null;

        return new Aim(subLevel, id, hit.getLocation(), pos);
    }

    public static Aim ofLocalPlayer(final double range) {
        return of(Minecraft.getInstance().player, range);
    }
}
