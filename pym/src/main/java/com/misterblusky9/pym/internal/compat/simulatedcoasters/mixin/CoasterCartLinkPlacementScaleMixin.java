package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterLinkScale;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartLinkPlacement", remap = false)
public abstract class CoasterCartLinkPlacementScaleMixin {
    private static final String PYM$POSE =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartMidTrackPlacement$PlacementPose;";
    private static final String PYM$ADJUSTED =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartLinkPlacement$AdjustedPlacement;";

    private static final String PYM$BEARING_DISTANCE =
            "bearingCenterDistance("
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;"
                    + "Ljava/lang/Double;)D";

    private static final String PYM$SNAP =
            "snapPlacementToLinkPartner("
                    + "Lnet/minecraft/world/level/Level;"
                    + PYM$POSE
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Ljava/lang/Double;)" + PYM$ADJUSTED;

    private static final String PYM$NO_SNAP =
            "placementAtLinkPartnerWithoutSnap("
                    + PYM$POSE
                    + "Lnet/minecraft/world/phys/Vec3;)" + PYM$ADJUSTED;

    private static final String PYM$ADJUSTED_INIT =
            PYM$ADJUSTED + "<init>(" + PYM$POSE + "DZ)V";

    @ModifyReturnValue(
            method = PYM$BEARING_DISTANCE,
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private static double pym$bearingDistanceToNominal(
            final double world,
            @Local(argsOnly = true, index = 0) final SubLevel cartA,
            @Local(argsOnly = true, index = 1) final SubLevel cartB,
            @Local(argsOnly = true, index = 2) final Double partialTick
    ) {
        return CoasterLinkScale.toNominal(
                world, CoasterLinkScale.pairScale(cartA, cartB, partialTick));
    }

    @ModifyExpressionValue(
            method = PYM$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=0.5"),
            remap = false,
            require = 1
    )
    private static double pym$scaleSnapInterval(final double nominal) {
        return pym$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = PYM$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=1.5"),
            remap = false,
            require = 1
    )
    private static double pym$scaleSnapMinimum(final double nominal) {
        return pym$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = PYM$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=6.0"),
            remap = false,
            require = 1
    )
    private static double pym$scaleSnapMaximum(final double nominal) {
        return pym$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = PYM$SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=7.0"),
            remap = false,
            require = 1
    )
    private static double pym$scaleSnapReject(final double nominal) {
        return pym$toWorld(nominal);
    }

    @ModifyExpressionValue(
            method = PYM$NO_SNAP,
            at = @At(value = "CONSTANT", args = "doubleValue=7.0"),
            remap = false,
            require = 1
    )
    private static double pym$scaleFreePlacementReject(final double nominal) {
        return pym$toWorld(nominal);
    }

    @ModifyArg(
            method = {PYM$SNAP, PYM$NO_SNAP},
            at = @At(value = "INVOKE", target = PYM$ADJUSTED_INIT),
            index = 1,
            remap = false,
            require = 1
    )
    private static double pym$reportNominalPlacementDistance(final double world) {
        return CoasterLinkScale.toNominal(
                world, CoasterPlacementScaleContext.remembered());
    }

    private static double pym$toWorld(final double nominal) {
        return CoasterLinkScale.toWorld(
                nominal, CoasterPlacementScaleContext.remembered());
    }
}
