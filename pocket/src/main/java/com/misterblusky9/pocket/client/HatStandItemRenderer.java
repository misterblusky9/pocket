package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.TallHatStandBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

public final class HatStandItemRenderer extends CustomRenderedItemModelRenderer {
    private static final float HEAD_CENTRE = 9.0F / 16.0F;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                          int light, int overlay) {
        renderer.renderSolid(model.getOriginalModel(), light);
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(profile);
        if (profile != null && !profile.properties().containsKey("textures")
                && egg != HatStandBlockEntity.NameEgg.HEROBRINE && egg != HatStandBlockEntity.NameEgg.RAINBOW) profile = null;
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        float rise = stack.is(ModItems.TALL_HAT_STAND.get()) ? TallHatStandBlock.RISE : 0.0F;
        if (egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) {
            pose.translate(0.5F, HEAD_CENTRE + rise, 0.5F);
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.translate(-0.5F, -HEAD_CENTRE - rise, -0.5F);
        }
        HatStandRenderer.renderHead(profile, stack.getOrDefault(ModDataComponents.SKIN_LAYER.get(), 0),
                0.0F, pose, buffers, light, rise);
        pose.popPose();
    }
}
