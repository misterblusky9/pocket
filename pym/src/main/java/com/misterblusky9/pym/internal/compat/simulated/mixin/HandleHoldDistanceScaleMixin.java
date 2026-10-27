package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulated.HandleHoldScale;
import dev.simulated_team.simulated.content.blocks.handle.ClientHandleHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ClientHandleHandler.class, remap = false)
public abstract class HandleHoldDistanceScaleMixin {
    @ModifyConstant(method = "startHold", constant = @Constant(doubleValue = 5.0D), remap = false)
    private double pym$scaleStartCap(double blocks, @Local(argsOnly = true) Player player) {
        return blocks * HandleHoldScale.body(player);
    }

    @ModifyConstant(method = "deltaRange", constant = {
            @Constant(doubleValue = 1.0D), @Constant(doubleValue = 5.0D)}, remap = false)
    private double pym$scaleRangeBounds(double blocks, @Local(argsOnly = true) Player player) {
        return blocks * HandleHoldScale.body(player);
    }

    @ModifyVariable(method = "deltaRange", at = @At("HEAD"), argsOnly = true, remap = false)
    private float pym$scaleRangeStep(float delta, @Local(argsOnly = true) Player player) {
        return (float) (delta * HandleHoldScale.body(player));
    }

    @WrapOperation(method = {"startHold", "deltaRange"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"), remap = false)
    private double pym$scaleRangeReach(
            AttributeInstance attribute, Operation<Double> original, @Local(argsOnly = true) Player player
    ) {
        return original.call(attribute) * HandleHoldScale.reach(player);
    }

    @ModifyConstant(method = "activeTick", constant = {
            @Constant(doubleValue = 4.0D), @Constant(doubleValue = 2.0D)}, remap = false)
    private double pym$scaleClimbOffsets(double blocks, @Local(argsOnly = true) LocalPlayer player) {
        return blocks * HandleHoldScale.body(player);
    }

    @ModifyConstant(method = "activeTick", constant = @Constant(doubleValue = 0.3D), remap = false)
    private double pym$unscaleClimbPull(double gain, @Local(argsOnly = true) LocalPlayer player) {
        return gain / HandleHoldScale.motion(player);
    }

    @WrapOperation(method = "activeTick", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;max(DD)D"), remap = false)
    private double pym$scaleClimbDrop(
            double a, double b, Operation<Double> original, @Local(argsOnly = true) LocalPlayer player
    ) {
        return original.call(a, b) * HandleHoldScale.body(player);
    }
}
