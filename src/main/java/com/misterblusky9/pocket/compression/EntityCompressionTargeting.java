package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class EntityCompressionTargeting {
    public record Target(LivingEntity entity, Vec3 hitPos) {}

    private EntityCompressionTargeting() {}

    public static @Nullable Target find(final Player player, final double range) {
        if (player == null || range <= 0.0D || !PehkuiScaleBridge.isOperational()) return null;

        final Level level = player.level();
        final Vec3 start = player.getEyePosition(1.0F);
        final Vec3 look = player.getViewVector(1.0F);
        final Vec3 end = start.add(look.scale(range));

        final BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearestDistanceSq = blockHit == null || blockHit.getType() == HitResult.Type.MISS
                ? range * range
                : start.distanceToSqr(blockHit.getLocation());

        LivingEntity nearest = null;
        Vec3 nearestPoint = null;
        final AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

        for (final LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                search,
                entity -> entity != player
                        && entity.isAlive()
                        && !entity.isSpectator()
                        && !(entity instanceof ArmorStand)
        )) {
            final Optional<Vec3> clipped = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (clipped.isEmpty()) continue;

            final double distanceSq = start.distanceToSqr(clipped.get());
            if (distanceSq >= nearestDistanceSq) continue;

            nearestDistanceSq = distanceSq;
            nearest = candidate;
            nearestPoint = clipped.get();
        }

        return nearest == null ? null : new Target(nearest, nearestPoint);
    }

    public static double scale(final LivingEntity entity) {
        return entity == null ? 1.0D : PehkuiScaleBridge.personalScale(entity);
    }
}
