package com.misterblusky9.pocket.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

public final class ItemValueBoxIcon extends AllIcons {
    private static final float ITEM_SCALE = 1.0F;
    private static final float ITEM_DEPTH = 0.0F;

    private final Supplier<? extends ItemLike> item;

    public ItemValueBoxIcon(final Supplier<? extends ItemLike> item) {
        super(0, 0);
        this.item = item;
    }

    private ItemStack stack() {
        final ItemLike resolved = item == null ? null : item.get();
        return resolved == null ? ItemStack.EMPTY : new ItemStack(resolved);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(final GuiGraphics graphics, final int x, final int y) {
        final ItemStack stack = stack();
        if (stack.isEmpty()) {
            super.render(graphics, x, y);
            return;
        }
        graphics.renderFakeItem(stack, x, y);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(final PoseStack ms, final MultiBufferSource buffer, final int color) {
        final ItemStack stack = stack();
        if (stack.isEmpty()) {
            super.render(ms, buffer, color);
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        ms.pushPose();
        ms.translate(0.5F, 0.5F, ITEM_DEPTH);
        ms.scale(ITEM_SCALE, -ITEM_SCALE, ITEM_SCALE);
        minecraft.getItemRenderer()
                .renderStatic(
                        stack,
                        ItemDisplayContext.GUI,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        ms,
                        buffer,
                        minecraft.level,
                        0
                );
        ms.popPose();
    }
}
