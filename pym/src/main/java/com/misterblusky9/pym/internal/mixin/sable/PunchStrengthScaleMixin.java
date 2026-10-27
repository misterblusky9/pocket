package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerboundPunchSubLevelPacket.class, remap = false)
public abstract class PunchStrengthScaleMixin {
    @Unique
    private static final ThreadLocal<Double> pym$puncherScale = ThreadLocal.withInitial(() -> 1.0D);

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/network/packets/tcp/ServerboundPunchSubLevelPacket;computeStrengthScalar("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)D"),
            remap = false)
    private double pym$scaleWithPuncher(
            ServerSubLevel subLevel, Vector3dc position, Vector3dc direction,
            Operation<Double> original, @Local Player player
    ) {
        double scale = player == null ? 1.0D : Pym.entities().scaleOf(player);
        pym$puncherScale.set(scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D);
        try {
            return original.call(subLevel, position, direction);
        } finally {
            pym$puncherScale.set(1.0D);
        }
    }

    @WrapOperation(method = "computeStrengthScalar", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/network/packets/tcp/ServerboundPunchSubLevelPacket;punchCurve(D)D"),
            remap = false)
    private static double pym$punchAsPuncherSize(double mass, Operation<Double> original) {
        double scale = pym$puncherScale.get();
        if (scale == 1.0D) return original.call(mass);
        double strength = scale * scale * scale;
        return strength * original.call(mass / strength);
    }
}
