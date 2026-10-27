package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulated.HandleHoldScale;
import com.misterblusky9.pym.internal.physics.GenericConstraintState;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.UUID;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity$HandleConstraint", remap = false)
public abstract class HandleConstraintScaleMixin {
    @Shadow
    @Final
    private UUID playerId;

    @Shadow
    @Final
    HandleBlockEntity this$0;

    @WrapOperation(method = "physicsTick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintHandle;setMotor("
                    + "Ldev/ryanhcode/sable/api/physics/constraint/ConstraintJointAxis;DDDZD)V"), remap = false)
    private void pym$scaleGripWithPlayer(
            PhysicsConstraintHandle handle, ConstraintJointAxis axis, double target,
            double stiffness, double damping, boolean hasForceLimit, double maxForce,
            Operation<Void> original, @Local Player player
    ) {
        double scale = HandleHoldScale.body(player);
        double strength = scale * scale * scale;
        if (!GenericConstraintState.isLinear(axis)) strength *= scale;
        original.call(handle, axis, target, stiffness, damping, hasForceLimit, maxForce * strength);
    }

    @ModifyConstant(method = "physicsTick", constant = @Constant(doubleValue = 2.0D), remap = false)
    private double pym$scaleHoldOffsets(double blocks, @Local Player player) {
        return blocks * HandleHoldScale.body(player);
    }

    @WrapOperation(method = "physicsTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"), remap = false)
    private double pym$scaleHoldReach(
            AttributeInstance attribute, Operation<Double> original, @Local Player player
    ) {
        return original.call(attribute) * HandleHoldScale.reach(player);
    }

    @ModifyConstant(method = "setScrollDistance", constant = @Constant(doubleValue = 2.5D), remap = false)
    private double pym$scaleMaxHoldDistance(double blocks) {
        Level level = this.this$0.getLevel();
        return blocks * HandleHoldScale.body(level == null ? null : level.getPlayerByUUID(this.playerId));
    }
}
