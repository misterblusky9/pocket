package com.misterblusky9.pocket.compat.curios;

import com.misterblusky9.pocket.compat.CompressorBeltRender;
import com.misterblusky9.pocket.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
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
        CompressorBeltRender.render(stack, slotContext.entity(), model, pose, buffers, light);
    }
}
