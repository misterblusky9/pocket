package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.HeldCases;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldCaseMixin {
    @ModifyExpressionValue(method = "renderArmWithItem",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/world/item/MapItem"))
    private boolean pocket$holdCaseLikeMap(boolean map, @Local(argsOnly = true) ItemStack stack) {
        return map || HeldCases.is(stack);
    }

    @WrapOperation(method = {"renderOneHandedMap", "renderTwoHandedMap"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;)V"))
    private void pocket$drawCase(ItemInHandRenderer renderer, PoseStack pose, MultiBufferSource buffers, int light,
                                 ItemStack stack, Operation<Void> original) {
        if (HeldCases.is(stack)) HeldCases.render(pose, buffers, light, stack);
        else original.call(renderer, pose, buffers, light, stack);
    }
}
