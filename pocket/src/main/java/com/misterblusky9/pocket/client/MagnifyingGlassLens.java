package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.MagnifyingGlassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MagnifyingGlassLens {
    private static final boolean ENABLED = false;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(
            PocketSized.MOD_ID, "shaders/post/magnifying_lens.json");
    private static final int MAX_WORLD_LENSES = 4;
    private static final float CENTER_X = 8.04F / 16F;
    private static final float CENTER_Y = 10.02F / 16F;
    private static final float CENTER_Z = 8F / 16F;
    private static final float RADIUS = 2.40F / 16F;
    private static final List<Footprint> worldLenses = new ArrayList<>();
    private static final List<Footprint> firstPersonLenses = new ArrayList<>();
    private static PostChain post;
    private static int width, height;
    private static float sceneNear = 0.05F, sceneFar = 256F;
    private static boolean failed;

    private record Projected(float x, float y, float depth, float w) {}
    private record Footprint(float cx, float cy, float ux, float uy,
                             float vx, float vy, float depth, boolean firstPerson) {
        float area() { return Math.abs(ux * vy - uy * vx); }
    }

    public static void capture(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                               PoseStack pose, BakedModel model) {
        if (!ENABLED || !(stack.getItem() instanceof MagnifyingGlassItem)) return;
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ||
                context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean third = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ||
                context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (!first && !third) return;
        if (failed || Minecraft.getInstance().level == null) return;

        PoseStack transformed = new PoseStack();
        transformed.mulPose(pose.last().pose());
        ClientHooks.handleCameraTransforms(transformed, model, context, leftHand);
        transformed.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f clip = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).mul(transformed.last().pose());
        Projected c = project(clip, CENTER_X, CENTER_Y, CENTER_Z);
        Projected u = project(clip, CENTER_X + RADIUS, CENTER_Y, CENTER_Z);
        Projected v = project(clip, CENTER_X, CENTER_Y + RADIUS, CENTER_Z);
        if (c.w <= 0.0F || u.w <= 0.0F || v.w <= 0.0F) return;
        Footprint lens = new Footprint(c.x, c.y, u.x - c.x, u.y - c.y,
                v.x - c.x, v.y - c.y, c.depth, first);
        if (lens.area() < 0.000000005F) return;
        if (c.x < -0.3F || c.x > 1.3F || c.y < -0.3F || c.y > 1.3F) return;
        if (first) {
            if (firstPersonLenses.size() < 2) firstPersonLenses.add(lens);
        } else if (worldLenses.size() < MAX_WORLD_LENSES) {
            worldLenses.add(lens);
        }
    }

    private static Projected project(Matrix4f matrix, float x, float y, float z) {
        Vector4f p = new Vector4f(x, y, z, 1).mul(matrix);
        if (Math.abs(p.w) < 0.00001F) return new Projected(0, 0, 1, -1);
        float iw = 1F / p.w;
        return new Projected(p.x * iw * 0.5F + 0.5F,
                p.y * iw * 0.5F + 0.5F,
                p.z * iw * 0.5F + 0.5F, p.w);
    }

    public static void onWorldStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            worldLenses.clear();
            firstPersonLenses.clear();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            Matrix4f projection = event.getProjectionMatrix();
            float near = projection.m32() / (projection.m22() - 1.0F);
            float far = projection.m32() / (projection.m22() + 1.0F);
            if (Float.isFinite(near) && Float.isFinite(far) && near > 0F && far > near) {
                sceneNear = near;
                sceneFar = far;
            }
            draw(worldLenses, event.getPartialTick().getGameTimeDeltaTicks());
            worldLenses.clear();
        }
    }

    public static void afterFirstPersonHands(float partialTick) {
        if (!firstPersonLenses.isEmpty()) {
            draw(firstPersonLenses, partialTick);
            firstPersonLenses.clear();
        }
    }

    private static void draw(List<Footprint> lenses, float tickDelta) {
        if (lenses.isEmpty() || failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (post == null) post = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                    mc.getMainRenderTarget(), EFFECT);
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (w != width || h != height) {
                post.resize(w, h);
                width = w;
                height = h;
            }
            float scale = (float) MagnifyingGlassZoom.viewerScale(mc.player);
            post.setUniform("NearPlane", sceneNear);
            post.setUniform("FarPlane", sceneFar);
            post.setUniform("FocusStart", 2F * scale);
            post.setUniform("FocusEnd", 5F * scale);
            for (Footprint lens : lenses) {
                float opacity = lens.firstPerson ? 1F - MagnifyingGlassZoom.focusStrength() : 1F;
                if (opacity < 0.02F) continue;
                post.setUniform("LensCX", lens.cx);
                post.setUniform("LensCY", lens.cy);
                post.setUniform("LensUX", lens.ux);
                post.setUniform("LensUY", lens.uy);
                post.setUniform("LensVX", lens.vx);
                post.setUniform("LensVY", lens.vy);
                post.setUniform("LensDepth", lens.depth);
                post.setUniform("DepthOcclusion", lens.firstPerson ? 0F : 1F);
                post.setUniform("LensOpacity", opacity);
                post.setUniform("LensPower", 0.32F);
                post.process(tickDelta);
                mc.getMainRenderTarget().bindWrite(true);
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Failed to render magnifying glass lens optics", error);
            reset();
            failed = true;
        }
    }

    public static void reset() {
        if (post != null) post.close();
        post = null;
        width = height = 0;
        failed = false;
        worldLenses.clear();
        firstPersonLenses.clear();
    }

    private MagnifyingGlassLens() {}
}
