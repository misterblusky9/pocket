package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the single JSON model, routing its End Portal quads to vanilla’s portal shader. */
public final class CreativePocketCaseItemRenderer extends CustomRenderedItemModelRenderer {
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer, ItemDisplayContext context,
                          PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        CreativeCasePortal.renderShell(stack, poseStack, buffer, light, overlay, model.getOriginalModel());
        CreativeCasePortal.renderPortal(poseStack, buffer, model.getOriginalModel());
    }
}
