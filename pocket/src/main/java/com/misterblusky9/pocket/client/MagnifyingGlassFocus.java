package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;

public final class MagnifyingGlassFocus {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "shaders/post/magnifying_focus.json");
    private static PostChain chain;
    private static int width;
    private static int height;
    private static boolean failed;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        float strength = MagnifyingGlassZoom.focusStrength();
        if (strength <= 0.001F) return;

        try {
            if (chain == null) {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), EFFECT);
            }
            int w = minecraft.getWindow().getWidth();
            int h = minecraft.getWindow().getHeight();
            if (width != w || height != h) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            Matrix4f projection = event.getProjectionMatrix();
            float a = projection.m32() / (projection.m22() - 1.0F);
            float b = projection.m32() / (projection.m22() + 1.0F);
            boolean reverseZ = a > b;
            float near = reverseZ ? b : a;
            float far = reverseZ ? a : b;
            if (!Float.isFinite(near) || !Float.isFinite(far)
                    || near <= 0.0F || far <= near) return;

            float scale = (float) MagnifyingGlassZoom.viewerScale(minecraft.player);
            float focusStart = Math.max(near * 1.01F, 2.0F * scale);
            float focusEnd = Math.max(focusStart + near * 0.1F, 5.0F * scale);
            chain.setUniform("FocusStart", focusStart);
            chain.setUniform("FocusEnd", focusEnd);
            chain.setUniform("NearPlane", near);
            chain.setUniform("FarPlane", far);
            chain.setUniform("ReverseDepth", reverseZ ? 1.0F : 0.0F);
            chain.setUniform("BlurStrength", strength);
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
            minecraft.getMainRenderTarget().bindWrite(true);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not render magnifying glass focus", exception);
            reset();
            failed = true;
        }
    }

    public static void reset() {
        if (chain != null) chain.close();
        chain = null;
        width = 0;
        height = 0;
        failed = false;
    }

    private MagnifyingGlassFocus() {}
}
