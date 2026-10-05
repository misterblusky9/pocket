package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.createmod.catnip.gui.TextureSheetSegment;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum PocketValueSettingsTexture implements ScreenElement, TextureSheetSegment {
    FRAME_TL(65, 9, 4, 4),
    FRAME_TR(70, 9, 4, 4),
    FRAME_BL(65, 19, 4, 4),
    FRAME_BR(70, 19, 4, 4),
    FRAME_LEFT(65, 14, 3, 4),
    FRAME_RIGHT(71, 14, 3, 4),
    FRAME_TOP(0, 24, 256, 3),
    FRAME_BOTTOM(0, 27, 256, 3),
    MILESTONE(0, 0, 7, 8),
    BAR(7, 0, 249, 8),
    BAR_BG(75, 9, 1, 1),
    CURSOR_LEFT(0, 9, 3, 14),
    CURSOR(4, 9, 56, 14),
    CURSOR_RIGHT(61, 9, 3, 14);

    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(
            PocketSized.MOD_ID, "textures/gui/value_settings_creative.png");
    public static final ResourceLocation PEHKUI = ResourceLocation.fromNamespaceAndPath(
            PocketSized.MOD_ID, "textures/gui/value_settings_pehkui.png");

    private final int startX;
    private final int startY;
    private final int width;
    private final int height;

    PocketValueSettingsTexture(final int startX, final int startY, final int width, final int height) {
        this.startX = startX;
        this.startY = startY;
        this.width = width;
        this.height = height;
    }

    @Override
    public ResourceLocation getLocation() {
        return LOCATION;
    }

    @Override
    public int getStartX() {
        return this.startX;
    }

    @Override
    public int getStartY() {
        return this.startY;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void render(final GuiGraphics graphics, final int x, final int y) {
        graphics.blit(LOCATION, x, y, this.startX, this.startY, this.width, this.height);
    }

    public Sheet on(final ResourceLocation sheet) {
        return new Sheet(this, sheet);
    }

    public record Sheet(PocketValueSettingsTexture part, ResourceLocation sheet) implements ScreenElement, TextureSheetSegment {
        @Override
        public ResourceLocation getLocation() {
            return this.sheet;
        }

        @Override
        public int getStartX() {
            return this.part.startX;
        }

        @Override
        public int getStartY() {
            return this.part.startY;
        }

        @Override
        public int getWidth() {
            return this.part.width;
        }

        @Override
        public int getHeight() {
            return this.part.height;
        }

        @Override
        public void render(final GuiGraphics graphics, final int x, final int y) {
            graphics.blit(this.sheet, x, y, this.part.startX, this.part.startY, this.part.width, this.part.height);
        }
    }
}
