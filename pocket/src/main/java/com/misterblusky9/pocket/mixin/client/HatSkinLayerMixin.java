package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.misterblusky9.pocket.client.HatEggs;
import com.misterblusky9.pocket.client.HatRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HatSkinLayerMixin {
    @Shadow
    protected EntityModel<?> model;

    @Unique
    private PartPose pocket$restoreHead;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void pocket$flipHead(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(model instanceof HumanoidModel<?> humanoid) || HatEggs.worn(entity) != NameEgg.UPSIDE_DOWN) return;
        pocket$restoreHead = humanoid.head.storePose();
        HatEggs.flipInPlace(humanoid.head);
        humanoid.hat.copyFrom(humanoid.head);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void pocket$restoreHead(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (pocket$restoreHead == null || !(model instanceof HumanoidModel<?> humanoid)) return;
        humanoid.head.loadPose(pocket$restoreHead);
        humanoid.hat.copyFrom(humanoid.head);
        pocket$restoreHead = null;
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void pocket$layerSkin(EntityModel<?> model, PoseStack pose, VertexConsumer consumer, int light,
                                  int overlay, int color, Operation<Void> original, LivingEntity entity,
                                  float yaw, float partialTick, PoseStack entityPose, MultiBufferSource buffers,
                                  int entityLight) {
        int mode = model instanceof HumanoidModel<?> humanoid && humanoid.hat.visible
                ? HatRendering.layerMode(entity) : 0;
        if (mode == 0) {
            original.call(model, pose, consumer, light, overlay, color);
            return;
        }
        HumanoidModel<?> humanoid = (HumanoidModel<?>) model;
        humanoid.hat.visible = false;
        original.call(model, pose, consumer, light, overlay, color);
        humanoid.hat.visible = true;
        if (mode != 1) return;
        pose.pushPose();
        HatRendering.flatten(pose, humanoid.head, HatRendering.skinFlat(entity));
        humanoid.hat.render(pose, consumer, light, overlay, color);
        pose.popPose();
    }
}
