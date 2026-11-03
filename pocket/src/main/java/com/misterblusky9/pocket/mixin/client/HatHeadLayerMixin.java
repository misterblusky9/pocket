package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.HatRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CustomHeadLayer.class)
public abstract class HatHeadLayerMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
    private void pocket$layerHead(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                  float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                  float netHeadYaw, float headPitch, Operation<Void> original) {
        int mode = HatRendering.headwearMode(entity);
        if (mode == 2) return;
        pose.pushPose();
        if (mode == 1) HatRendering.flatten(pose,
                ((HeadedModel) ((RenderLayer<?, ?>) (Object) this).getParentModel()).getHead(),
                HatRendering.headwearFlat(HatRendering.headwear(entity)));
        original.call(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                netHeadYaw, headPitch);
        pose.popPose();
    }

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pocket$cosmeticHead(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
        return slot == EquipmentSlot.HEAD ? HatRendering.headwear(entity) : original.call(entity, slot);
    }
}
