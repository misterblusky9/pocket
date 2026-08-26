package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public record PocketGuiTexture(ResourceLocation sheet, int startX, int startY, int width, int height) {
    public static final PocketGuiTexture CANNON = new PocketGuiTexture(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/gui/creative_shrink_ray.png"),
            0, 0, 234, 103);

    public static final PocketGuiTexture COMPRESSION_GUN = new PocketGuiTexture(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/gui/compression_gun_gui.png"),
            0, 0, 234, 103);

    public static PocketGuiTexture compressionGun() {
        return Minecraft.getInstance().getResourceManager().getResource(COMPRESSION_GUN.sheet()).isPresent()
                ? COMPRESSION_GUN
                : CANNON;
    }

    public void render(final GuiGraphics graphics, final int x, final int y) {
        graphics.blit(this.sheet, x, y, this.startX, this.startY, this.width, this.height);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}
