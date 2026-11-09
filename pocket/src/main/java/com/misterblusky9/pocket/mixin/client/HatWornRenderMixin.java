package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.HatRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class HatWornRenderMixin {
    @WrapMethod(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V")
    private void pocket$layerHelmet(PoseStack pose, MultiBufferSource buffers, LivingEntity entity,
                                    EquipmentSlot slot, int light, HumanoidModel<?> model, float limbSwing,
                                    float limbSwingAmount, float partialTick, float ageInTicks,
                                    float netHeadYaw, float headPitch, Operation<Void> original) {
        int mode = slot == EquipmentSlot.HEAD ? HatRendering.headwearMode(entity) : 0;
        if (mode == 2) return;
        pose.pushPose();
        if (mode == 1) HatRendering.flatten(pose,
                ((HumanoidModel<?>) ((RenderLayer<?, ?>) (Object) this).getParentModel()).head,
                HatRendering.headwearFlat(HatRendering.headwear(entity)));
        if (slot == EquipmentSlot.HEAD) HatRendering.pullSkullCap(pose, HatRendering.headwear(entity));
        original.call(pose, buffers,
                entity, slot, light, model, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
        pose.popPose();
    }

    @WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pocket$cosmeticHelmet(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
        return slot == EquipmentSlot.HEAD ? HatRendering.headwear(entity) : original.call(entity, slot);
    }
}
