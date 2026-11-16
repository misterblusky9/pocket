package com.misterblusky9.pocket.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CompressorBeltRender {
    public static void render(ItemStack stack, LivingEntity entity, HumanoidModel<?> model,
                              PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.translate(-0.3F + 0.5F / 16.0F, 0.65F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(15.0F));
        pose.scale(0.375F, -0.375F, -0.375F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
        pose.popPose();
    }

    private CompressorBeltRender() {}
}
