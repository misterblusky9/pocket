package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.PocketSized;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PocketTabBanner {
    public static final ResourceLocation SPRITE = ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "banner");
    public static final int PADDING_SLOTS = 9;

    private static final int BACKGROUND = 0xAA39231C;
    private static final int COLOR = 0xFFFFEB8C;
    private static final int SECONDARY_COLOR = 0xFFCEA05A;
    private static final int W = 162;
    private static final int H = 18;

    public static int currentRow = 0;

    public interface PausableTicker {
        void pocket$setPlaying(boolean playing);
    }

    public interface TickerHolder {
        SpriteTicker pocket$getTicker();
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentRow != 0) return;

        PoseStack ps = graphics.pose();
        ps.pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int left = screen.getGuiLeft() + 8;
        int top = screen.getGuiTop() + 17;
        ps.translate(left, top, 0.0F);

        boolean hovering = mouseX >= left && mouseX <= left + W && mouseY >= top && mouseY <= top + H;
        setPlaying(SPRITE, hovering);
        graphics.blitSprite(SPRITE, 0, 0, W, H);

        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("pocket.tab_banner");
        graphics.fill(2, 2, font.width(text) + 8, H - 2, BACKGROUND);
        drawAuraText(graphics, text, SECONDARY_COLOR, COLOR, 5, 5);

        ps.popPose();
        RenderSystem.disableDepthTest();
    }

    private static void drawAuraText(GuiGraphics graphics, Component text, int color1, int color2, int x, int y) {
        Font font = Minecraft.getInstance().font;
        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale();
        graphics.drawString(font, text, x, y, color1, true);

        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 1.0F);
        Matrix4f pose = new Matrix4f(ps.last().pose());
        Vector3f position = pose.transformPosition(new Vector3f(x, y, 0.0F)).mul(scale);
        Vector3f corner = pose.transformPosition(new Vector3f(x + font.width(text), y + 9.0F / 1.8F, 0.0F)).mul(scale);
        int height = (int) (corner.y - position.y);
        int width = (int) (corner.x - position.x);
        RenderSystem.enableScissor((int) position.x, window.getHeight() - (int) position.y - height, width, height);
        graphics.drawString(font, text, x, y, color2, false);
        RenderSystem.disableScissor();
        ps.popPose();
    }

    private static void setPlaying(ResourceLocation id, boolean playing) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
        if (((TickerHolder) sprite.contents()).pocket$getTicker() instanceof PausableTicker ticker) {
            ticker.pocket$setPlaying(playing);
        }
    }

    private PocketTabBanner() {}
}
