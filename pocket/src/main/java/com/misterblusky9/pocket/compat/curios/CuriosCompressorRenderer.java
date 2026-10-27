package com.misterblusky9.pocket.compat.curios;

import com.misterblusky9.pocket.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class CuriosCompressorRenderer implements ICurioRenderer {
    public static void register(FMLClientSetupEvent event) {
        CuriosRendererRegistry.register(ModItems.SELF_RESIZE_DEVICE.get(), CuriosCompressorRenderer::new);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch
    ) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
        LivingEntity entity = slotContext.entity();

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
}
