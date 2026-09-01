package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner", remap = false)
public abstract class CoasterCartSpawnerScaleMixin {
    @Inject(
            method = "spawnMinimalContraption(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
            at = @At("RETURN"),
            require = 1
    )
    private static void pocket$inheritRailScaleAfterNativeSnap(
            final ServerLevel level,
            final Vec3 plotOrigin,
            final Quaterniond orientation,
            @Coerce final Object graphHit,
            final boolean initialSnap,
            final CallbackInfoReturnable<ServerSubLevel> cir
    ) {
        final ServerSubLevel cart = cir.getReturnValue();
        if (cart == null || graphHit == null) return;

        final double railScale = SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, null);
        if (Math.abs(railScale - 1.0D) <= PocketSized.EPSILON) return;

        SimulatedCoastersPlacementScaleContext.initializeCartScale(cart, railScale);
    }
}
