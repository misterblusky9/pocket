package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartMidTrackPlacement", remap = false)
public abstract class CoasterCartMidTrackPlacementScaleMixin {
    @Inject(
            method = "plotOriginForGraphHit(Lnet/minecraft/world/level/Level;Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Lorg/joml/Quaterniond;Ljava/lang/Double;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            require = 1
    )
    private static void pocket$captureTargetRailScale(
            final Level level,
            @Coerce final Object graphHit,
            final Quaterniond orientation,
            final Double partialTick,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersPlacementScaleContext.remember(
                SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, partialTick)
        );
    }
}
