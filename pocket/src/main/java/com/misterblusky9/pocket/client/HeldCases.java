package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class HeldCases {
    private static final float MAP_WIDTH = 0.38F * 142.0F / 128.0F;
    private static final float CASE_WIDTH = 14.0F / 16.0F;
    private static final float SIZE = MAP_WIDTH / CASE_WIDTH;

    public static boolean is(ItemStack stack) {
        return container(stack) != null;
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack) {
        PocketContainer container = container(stack);
        if (container == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.scale(SIZE, SIZE, SIZE);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.translate(0.0D, -container.floorY(), 0.0D);
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, minecraft.level, 0);
        pose.popPose();
    }

    private static @Nullable PocketContainer container(ItemStack stack) {
        if (!(stack.getItem() instanceof PocketCaseItem)) return null;
        PocketContainer container = PocketContainer.of(stack);
        return container == PocketContainer.BRASS_DISPLAY_PLATE
                || container == PocketContainer.ANDESITE_DISPLAY_PLATE ? container : null;
    }

    private HeldCases() {}
}
