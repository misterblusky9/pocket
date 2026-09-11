package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner", remap = false)
public abstract class CoasterCartSpawnerScaleMixin {
    @Inject(
            method = "spawnMinimalContraption(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
            at = @At("HEAD"),
            require = 1
    )
    private static void pocket$captureRailScaleForSpawn(
            final ServerLevel level,
            final Vec3 plotOrigin,
            final Quaterniond orientation,
            @Coerce final Object graphHit,
            final boolean initialSnap,
            final CallbackInfoReturnable<ServerSubLevel> cir
    ) {
        if (graphHit == null) return;
        SimulatedCoastersPlacementScaleContext.remember(
                SimulatedCoastersScaleLookup.scaleForGraphHit(
                        level,
                        graphHit,
                        null,
                        SimulatedCoastersPlacementScaleContext.remembered()
                )
        );
    }

    @ModifyArg(
            method = "spawnMinimalContraption(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;allocateNewSubLevel(Ldev/ryanhcode/sable/companion/math/Pose3d;)Ldev/ryanhcode/sable/sublevel/SubLevel;"
            ),
            index = 0,
            require = 1
    )
    private static Pose3d pocket$seedRailScaleBeforeAllocation(final Pose3d pose) {
        if (pose == null) return null;
        final double railScale = SimulatedCoastersPlacementScaleContext.remembered();
        if (!PocketSized.isValidScale(railScale) || Math.abs(railScale - 1.0D) <= PocketSized.EPSILON) {
            return pose;
        }
        pose.scale().set(railScale, railScale, railScale);
        return pose;
    }

    @Inject(
            method = "spawnMinimalContraption(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;onStatsChanged(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private static void pocket$inheritRailScaleBeforeNativeSnap(
            final ServerLevel level,
            final Vec3 plotOrigin,
            final Quaterniond orientation,
            @Coerce final Object graphHit,
            final boolean initialSnap,
            final CallbackInfoReturnable<ServerSubLevel> cir,
            @Local(index = 11) final SubLevel subLevel
    ) {
        if (!(subLevel instanceof final ServerSubLevel cart) || graphHit == null) return;

        final double railScale = SimulatedCoastersScaleLookup.scaleForGraphHit(
                level,
                graphHit,
                null,
                SimulatedCoastersPlacementScaleContext.remembered()
        );
        if (Math.abs(railScale - 1.0D) <= PocketSized.EPSILON) return;

        SimulatedCoastersPlacementScaleContext.initializeCartScale(cart, railScale);
    }
}
