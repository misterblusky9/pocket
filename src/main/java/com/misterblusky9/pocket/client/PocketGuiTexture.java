package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public record PocketGuiTexture(
        ResourceLocation sheet,
        int startX,
        int startY,
        int width,
        int height,
        int textureWidth,
        int textureHeight
) {
    public static final PocketGuiTexture POTATO_CANNON = new PocketGuiTexture(
            ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID,
                    "textures/gui/potato_cannon_gui.png"
            ),
            0, 0,
            234, 113,
            256, 113
    );

    public static final PocketGuiTexture SHRINKRAY = new PocketGuiTexture(
            ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID,
                    "textures/gui/creative_shrinkray_gui.png"
            ),
            0, 0,
            234, 103,
            256, 103
    );

    public void render(final GuiGraphics graphics, final int x, final int y) {
        graphics.blit(
                this.sheet,
                x,
                y,
                this.startX,
                this.startY,
                this.width,
                this.height,
                this.textureWidth,
                this.textureHeight
        );
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}