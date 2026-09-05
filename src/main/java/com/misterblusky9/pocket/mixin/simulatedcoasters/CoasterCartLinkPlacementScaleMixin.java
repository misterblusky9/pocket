package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersLinkScale;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartLinkPlacement", remap = false)
public abstract class CoasterCartLinkPlacementScaleMixin {
    private static final String POCKET$POSE =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartMidTrackPlacement$PlacementPose;";
    private static final String POCKET$ADJUSTED =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartLinkPlacement$AdjustedPlacement;";

    private static final String POCKET$BEARING_DISTANCE =
            "bearingCenterDistance("
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;"
                    + "Ljava/lang/Double;)D";

    private static final String POCKET$SNAP =
            "snapPlacementToLinkPartner("
                    + "Lnet/minecraft/world/level/Level;"
                    + POCKET$POSE
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Ljava/lang/Double;)" + POCKET$ADJUSTED;

    private static final String POCKET$NO_SNAP =
            "placementAtLinkPartnerWithoutSnap("
                    + POCKET$POSE
                    + "Lnet/minecraft/world/phys/Vec3;)" + POCKET$ADJUSTED;

    private static final String POCKET$ADJUSTED_INIT =
            POCKET$ADJUSTED + "<init>(" + POCKET$POSE + "DZ)V";

    @ModifyReturnValue(
            method = POCKET$BEARING_DISTANCE,
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private static double pocket$bearingDistanceToNominal(
            final double world,
            @Local(argsOnly = true, index = 0) final SubLevel cartA,
            @Local(argsOnly = true, index = 1) final SubLevel cartB,
            @Local(argsOnly = true, index = 2) final Double partialTick
    ) {
        return SimulatedCoastersLinkScale.toNominal(
                world, SimulatedCoastersLinkScale.pairScale(cartA, cartB, partialTick));
    }

    @ModifyExpressionValue(
            method = POCKET$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=0.5"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleSnapInterval(final double nominal) {
        return pocket$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = POCKET$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=1.5"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleSnapMinimum(final double nominal) {
        return pocket$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = POCKET$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=6.0"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleSnapMaximum(final double nominal) {
        return pocket$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = POCKET$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=7.0"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleSnapReject(final double nominal) {
        return pocket$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = POCKET$NO_SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=7.0"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleFreePlacementReject(final double nominal) {
        return pocket$toWorld(nominal);
    }

    @ModifyArg(
            method = {POCKET$SNAP, POCKET$NO_SNAP},
            at = @At(value = "INVOKE", target = POCKET$ADJUSTED_INIT),
            index = 1,
            remap = false,
            require = 1
    )
    private static double pocket$reportNominalPlacementDistance(final double world) {
        return SimulatedCoastersLinkScale.toNominal(
                world, SimulatedCoastersPlacementScaleContext.remembered());
    }

    private static double pocket$toWorld(final double nominal) {
        return SimulatedCoastersLinkScale.toWorld(
                nominal, SimulatedCoastersPlacementScaleContext.remembered());
    }
}
