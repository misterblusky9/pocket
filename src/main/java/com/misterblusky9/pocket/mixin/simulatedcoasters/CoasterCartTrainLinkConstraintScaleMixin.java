package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersLinkScale;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrainLinkConstraint", remap = false)
public abstract class CoasterCartTrainLinkConstraintScaleMixin {
    private static final String POCKET$UPDATE =
            "updateConstraint("
                    + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V";

    private static final String POCKET$LINK_CARTS =
            "linkCarts("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Z";

    private static final String POCKET$TENSION =
            "isUnderExtremeTension("
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;D)Z";

    private static final String POCKET$CENTER_DISTANCE =
            "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrainLinkConstraint$ActiveLink;centerDistance:D";

    @ModifyExpressionValue(
            method = POCKET$LINK_CARTS,
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;distance(Lorg/joml/Vector3dc;)D"),
            remap = false,
            require = 1
    )
    private static double pocket$storeNominalLinkDistance(
            final double world,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return SimulatedCoastersLinkScale.toNominal(
                world, SimulatedCoastersLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = POCKET$UPDATE,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = POCKET$CENTER_DISTANCE),
            remap = false,
            require = 1
    )
    private static double pocket$couplingGapToWorld(
            final double nominal,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 3) final ServerSubLevel cartB
    ) {
        return SimulatedCoastersLinkScale.toWorld(
                nominal, SimulatedCoastersLinkScale.pairScale(cartA, cartB, null));
    }

    @WrapOperation(
            method = POCKET$UPDATE,
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;"
                            + "setFrame1(Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V"),
            remap = false,
            require = 1
    )
    private static void pocket$scaleCartAFrame(
            final GenericConstraintHandle handle,
            final Vector3dc position,
            final Quaterniondc orientation,
            final Operation<Void> original,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartA
    ) {
        original.call(handle, cartA == null ? position : ScaleFrame.toBodyMetric(cartA, position), orientation);
    }

    @WrapOperation(
            method = POCKET$UPDATE,
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;"
                            + "setFrame2(Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V"),
            remap = false,
            require = 1
    )
    private static void pocket$scaleCartBFrame(
            final GenericConstraintHandle handle,
            final Vector3dc position,
            final Quaterniondc orientation,
            final Operation<Void> original,
            @Local(argsOnly = true, index = 3) final ServerSubLevel cartB
    ) {
        original.call(handle, cartB == null ? position : ScaleFrame.toBodyMetric(cartB, position), orientation);
    }

    @ModifyExpressionValue(
            method = POCKET$TENSION,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = POCKET$CENTER_DISTANCE),
            remap = false,
            require = 1
    )
    private static double pocket$tensionRestLengthToWorld(
            final double nominal,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return SimulatedCoastersLinkScale.toWorld(
                nominal, SimulatedCoastersLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = POCKET$TENSION,
            at = @At(value = "CONSTANT", args = "doubleValue=0.5"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleTensionSnapStretch(
            final double stretch,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return SimulatedCoastersLinkScale.toWorld(
                stretch, SimulatedCoastersLinkScale.pairScale(cartA, cartB, null));
    }

    @ModifyExpressionValue(
            method = POCKET$TENSION,
            at = @At(value = "CONSTANT", args = "doubleValue=0.07"),
            remap = false,
            require = 1
    )
    private static double pocket$scaleTensionForceStretch(
            final double stretch,
            @Local(argsOnly = true, index = 1) final ServerSubLevel cartA,
            @Local(argsOnly = true, index = 2) final ServerSubLevel cartB
    ) {
        return SimulatedCoastersLinkScale.toWorld(
                stretch, SimulatedCoastersLinkScale.pairScale(cartA, cartB, null));
    }
}
