package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterLinkScale;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrainLinkConstraint", remap = false)
public abstract class CoasterCartTrainLinkConstraintScaleMixin {
    private static final String PYM$UPDATE =
            "updateConstraint("
                    + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V";

    private static final String PYM$LINK_CARTS =
            "linkCarts("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Z";

    private static final String PYM$TENSION =
            "isUnderExtremeTension("
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;D)Z";

    private static final String PYM$CENTER_DISTANCE =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;centerDistance:D";

    @ModifyExpressionValue(
            method = PYM$LINK_CARTS,
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;distance(Lorg/joml/Vector3dc;)D"),
            remap = false,
            require = 1
    )
    private static double pym$storeNominalLinkDistance(
            final double world,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return CoasterLinkScale.toNominal(
                world, CoasterLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = PYM$UPDATE,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = PYM$CENTER_DISTANCE),
            remap = false,
            require = 1
    )
    private static double pym$couplingGapToWorld(
            final double nominal,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 3) final ServerSubLevel cartB
    ) {
        return CoasterLinkScale.toWorld(
                nominal, CoasterLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = PYM$TENSION,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = PYM$CENTER_DISTANCE),
            remap = false,
            require = 1
    )
    private static double pym$tensionRestLengthToWorld(
            final double nominal,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return CoasterLinkScale.toWorld(
                nominal, CoasterLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = PYM$TENSION,
            at = @At(value = "CONSTANT", args = "doubleValue=0.5"),
            remap = false,
            require = 1
    )
    private static double pym$scaleTensionSnapStretch(
            final double stretch,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return CoasterLinkScale.toWorld(
                stretch, CoasterLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = PYM$TENSION,
            at = @At(value = "CONSTANT", args = "doubleValue=0.07"),
            remap = false,
            require = 1
    )
    private static double pym$scaleTensionForceStretch(
            final double stretch,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return CoasterLinkScale.toWorld(
                stretch, CoasterLinkScale.pairScale(cartA, cartB, null));
    }
}
