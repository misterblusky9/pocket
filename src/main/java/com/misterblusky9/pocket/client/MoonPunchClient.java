package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compat.aeronautics.AeronauticsPhysicsStaffCompat;
import com.misterblusky9.pocket.moon.MoonPhysicsState;
import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MoonPunchClient {
    private static final double REACH = 4.5D;

    public static boolean tryPunch() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return false;
        if (AeronauticsPhysicsStaffCompat.isHolding(minecraft.player)) return false;

        final Vec3 from = minecraft.player.getEyePosition();
        final Vec3 to = from.add(minecraft.player.getLookAngle().scale(REACH));
        final MoonPhysicsState.RayHit moonHit = MoonPhysicsState.raycast(minecraft.level, from, to);
        if (moonHit == null) return false;

        final HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit != null
                && vanillaHit.getType() != HitResult.Type.MISS
                && from.distanceToSqr(vanillaHit.getLocation()) + 1.0E-4D < from.distanceToSqr(moonHit.worldPoint())) {
            return false;
        }

        PacketDistributor.sendToServer(MoonScaleNetwork.MoonPunchPayload.INSTANCE);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private MoonPunchClient() {}
}
