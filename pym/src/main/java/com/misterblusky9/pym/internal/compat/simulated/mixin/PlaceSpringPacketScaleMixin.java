package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.simulated.SimulatedSpringScaleBoundary;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.simulated_team.simulated.network.packets.PlaceSpringPacket;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlaceSpringPacket.class, remap = false)
public abstract class PlaceSpringPacketScaleMixin {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target =
            "Ldev/ryanhcode/sable/ActiveSableCompanion;distanceSquaredWithSubLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/core/Position;)D"),
            remap = false)
    private double pym$rejectCrossScaleSpring(ActiveSableCompanion helper, Level level,
                                              Position first, Position second,
                                              Operation<Double> original) {
        PlaceSpringPacket packet = (PlaceSpringPacket) (Object) this;
        if (!SimulatedSpringScaleBoundary.canConnect(level, packet.parentPos(), packet.childPos())) {
            return Double.POSITIVE_INFINITY;
        }
        return original.call(helper, level, first, second);
    }
}
