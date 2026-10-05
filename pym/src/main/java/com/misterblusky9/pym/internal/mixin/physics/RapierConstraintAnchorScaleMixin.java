package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterRivets;
import com.misterblusky9.pym.internal.physics.ConstraintRefresh;
import com.misterblusky9.pym.internal.physics.ScaleFrame;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierConstraintAnchorScaleMixin {
    @Inject(method = "addConstraint", at = @At("RETURN"), remap = false)
    private void pym$trackConstraint(
            final PhysicsPipelineBody body1,
            final PhysicsPipelineBody body2,
            final PhysicsConstraintConfiguration<?> configuration,
            final CallbackInfoReturnable<PhysicsConstraintHandle> cir
    ) {
        final PhysicsConstraintConfiguration<?> original = pym$originalConfiguration.get();
        pym$originalConfiguration.remove();

        ConstraintRefresh.record(
                cir.getReturnValue(), body1, body2, original != null ? original : configuration);
    }

    @Unique
    private static final ThreadLocal<PhysicsConstraintConfiguration<?>> pym$originalConfiguration =
            new ThreadLocal<>();

    @ModifyVariable(method = "addConstraint", at = @At("HEAD"), argsOnly = true, index = 3, remap = false)
    private PhysicsConstraintConfiguration<?> pym$scaleConstraintAnchors(
            final PhysicsConstraintConfiguration<?> configuration,
            @Local(argsOnly = true, index = 1) final PhysicsPipelineBody body1,
            @Local(argsOnly = true, index = 2) final PhysicsPipelineBody body2
    ) {
        pym$originalConfiguration.remove();

        if (configuration == null) return null;
        CoasterRivets.initializePlacementScale(body1, body2);
        if (!ScaleFrame.isScaled(body1) && !ScaleFrame.isScaled(body2)) return configuration;

        pym$originalConfiguration.set(configuration);

        if (configuration instanceof final FixedConstraintConfiguration fixed) {
            final FixedConstraintConfiguration rivet =
                    CoasterRivets.transform(body1, body2, fixed);
            if (rivet != null) return rivet;

            return new FixedConstraintConfiguration(
                    anchor(body1, fixed.pos1()),
                    anchor(body2, fixed.pos2()),
                    fixed.orientation()
            );
        }

        if (configuration instanceof final FreeConstraintConfiguration free) {
            return new FreeConstraintConfiguration(
                    anchor(body1, free.pos1()),
                    anchor(body2, free.pos2()),
                    free.orientation()
            );
        }

        if (configuration instanceof final GenericConstraintConfiguration generic) {
            return new GenericConstraintConfiguration(
                    anchor(body1, generic.pos1()),
                    anchor(body2, generic.pos2()),
                    generic.orientation1(),
                    generic.orientation2(),
                    generic.lockedAxes()
            );
        }

        if (configuration instanceof final RotaryConstraintConfiguration rotary) {
            return new RotaryConstraintConfiguration(
                    anchor(body1, rotary.pos1()),
                    anchor(body2, rotary.pos2()),
                    rotary.normal1(),
                    rotary.normal2()
            );
        }

        return configuration;
    }

    private static Vector3dc anchor(final PhysicsPipelineBody body, final Vector3dc plotPoint) {
        return ScaleFrame.toBodyMetric(body, plotPoint);
    }
}
