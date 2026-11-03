package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class HatStandItemRenderer extends CustomRenderedItemModelRenderer {
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                          int light, int overlay) {
        renderer.renderSolid(model.getOriginalModel(), light);
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile != null && !profile.properties().containsKey("textures")) profile = null;
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        HatStandRenderer.renderHead(profile, stack.getOrDefault(ModDataComponents.SKIN_LAYER.get(), 0),
                0.0F, pose, buffers, light);
        pose.popPose();
    }
}
