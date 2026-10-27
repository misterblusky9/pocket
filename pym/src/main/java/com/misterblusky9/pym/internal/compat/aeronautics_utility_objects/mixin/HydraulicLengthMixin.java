package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageLengthScale;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity", remap = false)
public abstract class HydraulicLengthMixin {
    @WrapOperation(method = "distanceSquaredTo", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/ActiveSableCompanion;distanceSquaredWithSubLevels("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;"
                    + "Lnet/minecraft/core/Position;)D"), remap = false)
    private double pym$nominalLinkDistance(
            ActiveSableCompanion helper, Level level, Position first, Position second,
            Operation<Double> original
    ) {
        double scale = LinkageLengthScale.between(level, first, second);
        return original.call(helper, level, first, second) / (scale * scale);
    }

    @ModifyReturnValue(method = "currentRodControlDistanceTo", at = @At("RETURN"), remap = false)
    private double pym$nominalRodLength(double world) {
        return world / LinkageLengthScale.ofLink((BlockEntity) (Object) this);
    }

    @Inject(method = "applyLengthLimits", at = @At("HEAD"), remap = false)
    private void pym$enterImpulseScale(CallbackInfo ci) {
        LinkageLengthScale.enterImpulseScale(LinkageLengthScale.ofLink((BlockEntity) (Object) this));
    }

    @Inject(method = "applyLengthLimits", at = @At("RETURN"), remap = false)
    private void pym$exitImpulseScale(CallbackInfo ci) {
        LinkageLengthScale.exitImpulseScale();
    }

    @ModifyVariable(method = "applyLengthLimits", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private Vector3d pym$nominalOwnEndpoint(Vector3d world) {
        return this.pym$nominal(world);
    }

    @ModifyVariable(method = "applyLengthLimits", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false)
    private Vector3d pym$nominalOtherEndpoint(Vector3d world) {
        return this.pym$nominal(world);
    }

    @ModifyVariable(method = "applyLengthImpulse", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private Vector3d pym$metricImpulse(Vector3d impulse) {
        double scale = LinkageLengthScale.ofLink((BlockEntity) (Object) this);
        return scale == 1.0D ? impulse : new Vector3d(impulse).mul(scale);
    }

    @WrapOperation(method = {"refreshGiantHydraulicMotor", "refreshRodDampingMotor"}, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;setMotor("
                    + "Ldev/ryanhcode/sable/api/physics/constraint/ConstraintJointAxis;DDDZD)V"), remap = false)
    private void pym$metricMotorTarget(
            GenericConstraintHandle handle, ConstraintJointAxis axis, double target,
            double stiffness, double damping, boolean hasForceLimit, double maxForce,
            Operation<Void> original
    ) {
        double scale = LinkageLengthScale.ofLink((BlockEntity) (Object) this);
        original.call(handle, axis, target * scale, stiffness, damping, hasForceLimit, maxForce);
    }

    @Unique
    private Vector3d pym$nominal(Vector3d world) {
        double scale = LinkageLengthScale.ofLink((BlockEntity) (Object) this);
        return scale == 1.0D ? world : new Vector3d(world).div(scale);
    }
}
