package com.misterblusky9.pocket.moon;

import net.minecraft.network.chat.Component;
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

    public static final Component NOT_FULL_MESSAGE = Component.translatable("pocket.message.moon_not_full");

    public record Hit(float surfaceX, float surfaceZ, Vec3 worldPoint) {}

    public static Hit hit(
            Player player,
            float scale,
            float partialTick,
            double obstructionRange
    ) {
        if (player == null || player.level().getMoonPhase() != 0) return null;
        return anyPhaseHit(player, scale, partialTick, obstructionRange);
    }

    public static boolean aimedAtNonFullMoon(
            Player player,
            float scale,
            double obstructionRange
    ) {
        return player != null && player.level().getMoonPhase() != 0
                && anyPhaseHit(player, scale, 1.0F, obstructionRange) != null;
    }

    private static Hit anyPhaseHit(
            Player player,
            float scale,
            float partialTick,
            double obstructionRange
    ) {
        if (player == null || !Float.isFinite(scale) || scale <= 0.0F) return null;
        if (!player.level().dimensionType().hasSkyLight()) return null;

        Vec3 look = player.getViewVector(partialTick);
        Vector3d local = new Vector3d(look.x, look.y, look.z)
                .rotateY(Math.PI * 0.5D)
                .rotateX(-player.level().getSunAngle(partialTick));

        if (local.y >= -EPSILON) return null;

        double distance = -PLANE_DISTANCE / local.y;
        double x = local.x * distance;
        double z = local.z * distance;
        double halfSize = SURFACE_HALF_SIZE * scale;
        if (Math.abs(x) > halfSize || Math.abs(z) > halfSize) return null;

        if (obstructionRange > 0.0D) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(look.scale(obstructionRange));
            BlockHitResult obstruction = player.level().clip(new ClipContext(
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
            Player player,
            float scale,
            float partialTick,
            double obstructionRange
    ) {
        return hit(player, scale, partialTick, obstructionRange) != null;
    }

    public static Vec3 visualHitPoint(Player player, float partialTick) {
        if (player == null) return Vec3.ZERO;
        Vec3 look = player.getViewVector(partialTick);
        Vector3d local = new Vector3d(look.x, look.y, look.z)
                .rotateY(Math.PI * 0.5D)
                .rotateX(-player.level().getSunAngle(partialTick));
        double distance = local.y < -EPSILON ? -PLANE_DISTANCE / local.y : PLANE_DISTANCE;
        return player.getEyePosition().add(look.scale(distance));
    }

    private MoonTargeting() {}
}
