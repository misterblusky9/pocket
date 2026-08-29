package com.misterblusky9.pocket.moon;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class MoonTargeting {
    public static final double PLANE_DISTANCE = 100.0D;
    public static final double BASE_HALF_SIZE = 20.0D;
    public static final double SURFACE_HALF_SIZE = 5.0D;
    private static final double EPSILON = 1.0E-8D;

    public record Hit(float surfaceX, float surfaceZ, Vec3 worldPoint) {}

    public static Hit hit(
            final Player player,
            final float scale,
            final float partialTick,
            final double obstructionRange
    ) {
        if (player == null || !Float.isFinite(scale) || scale <= 0.0F) return null;
        if (player.level().getMoonPhase() != 0) return null;
        if (!player.level().dimensionType().hasSkyLight()) return null;

        final Vec3 look = player.getViewVector(partialTick);
        final Vector3d local = new Vector3d(look.x, look.y, look.z)
                .rotateY(Math.PI * 0.5D)
                .rotateX(-player.level().getSunAngle(partialTick));

        if (local.y >= -EPSILON) return null;

        final double distance = -PLANE_DISTANCE / local.y;
        final double x = local.x * distance;
        final double z = local.z * distance;
        final double halfSize = SURFACE_HALF_SIZE * scale;
        if (Math.abs(x) > halfSize || Math.abs(z) > halfSize) return null;

        if (obstructionRange > 0.0D) {
            final Vec3 eye = player.getEyePosition();
            final Vec3 end = eye.add(look.scale(obstructionRange));
            final BlockHitResult obstruction = player.level().clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (obstruction != null && obstruction.getType() != HitResult.Type.MISS) return null;
        }

        return new Hit(
                (float) (x / halfSize),
                (float) (z / halfSize),
                player.getEyePosition().add(look.scale(distance))
        );
    }

    public static boolean isLookingAtMoon(
            final Player player,
            final float scale,
            final float partialTick,
            final double obstructionRange
    ) {
        return hit(player, scale, partialTick, obstructionRange) != null;
    }

    public static Vec3 visualHitPoint(final Player player, final float partialTick) {
        if (player == null) return Vec3.ZERO;
        final Vec3 look = player.getViewVector(partialTick);
        final Vector3d local = new Vector3d(look.x, look.y, look.z)
                .rotateY(Math.PI * 0.5D)
                .rotateX(-player.level().getSunAngle(partialTick));
        final double distance = local.y < -EPSILON ? -PLANE_DISTANCE / local.y : PLANE_DISTANCE;
        return player.getEyePosition().add(look.scale(distance));
    }

    private MoonTargeting() {}
}
