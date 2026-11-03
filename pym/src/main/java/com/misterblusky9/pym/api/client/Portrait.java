package com.misterblusky9.pym.api.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.misterblusky9.pym.internal.client.PortraitScope;
import com.misterblusky9.pym.internal.mixin.client.LightTextureAccessor;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

import java.util.List;

public final class Portrait {
    private static DynamicTexture lightmap;

    public static boolean canDraw(final ClientSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || !(subLevel.getRenderData() instanceof VanillaChunkedSubLevelRenderData data))
            return false;
        data.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(),
                Minecraft.getInstance().gameRenderer.getMainCamera());
        for (final RenderSection section : data.allRenderSections())
            if (section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) return false;
        return true;
    }

    public static boolean draw(final ClientSubLevel subLevel, final Vector3dc origin, final PoseStack poseStack,
                               final MultiBufferSource buffers, final int light, final int overlay) {
        if (PortraitScope.active() || !canDraw(subLevel)) return false;
        final VanillaChunkedSubLevelRenderData data = (VanillaChunkedSubLevelRenderData) subLevel.getRenderData();
        final Minecraft minecraft = Minecraft.getInstance();

        final Window window = minecraft.getWindow();
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        final List<ClientSubLevel> only = List.of(subLevel);

        final float fogStart = RenderSystem.getShaderFogStart();
        final float fogEnd = RenderSystem.getShaderFogEnd();
        final FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();

        PortraitScope.enter(subLevel, origin, modelView);
        try {
            final int lightmap = lightmap(light);
            for (final RenderType layer : RenderType.chunkBufferLayers()) {
                layer.setupRenderState();
                RenderSystem.setShaderTexture(2, lightmap);
                final ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, window);
                    shader.apply();
                    dispatcher.renderSectionLayer(only, layer, shader, 0.0D, 0.0D, 0.0D,
                            modelView, projection, partialTick);
                    shader.clear();
                }
                layer.clearRenderState();
            }
            VertexBuffer.unbind();

            final BlockEntityRenderDispatcher blockEntities = minecraft.getBlockEntityRenderDispatcher();
            for (final RenderSection section : data.allRenderSections()) {
                for (final BlockEntity blockEntity : section.getCompiled().getRenderableBlockEntities()) {
                    final BlockEntityRenderer<BlockEntity> renderer = blockEntities.getRenderer(blockEntity);
                    if (renderer == null) continue;
                    final BlockPos pos = blockEntity.getBlockPos();
                    poseStack.pushPose();
                    poseStack.translate(pos.getX() - origin.x(), pos.getY() - origin.y(), pos.getZ() - origin.z());
                    renderer.render(blockEntity, partialTick, poseStack, buffers, light, overlay);
                    poseStack.popPose();
                }
            }
        } finally {
            PortraitScope.exit();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
        return true;
    }

    public static int span(final ClientSubLevel subLevel) {
        final dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return Math.max(
                bounds.maxX() - bounds.minX() + 1,
                Math.max(bounds.maxY() - bounds.minY() + 1, bounds.maxZ() - bounds.minZ() + 1));
    }

    public static boolean drawFitted(final ClientSubLevel subLevel, final PoseStack poseStack, final float size) {
        if (!canDraw(subLevel)) return false;
        final dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final float fit = size / Math.max(1, span(subLevel));
        final net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.scale(fit, fit, fit);
        final boolean drawn = draw(subLevel, new org.joml.Vector3d(
                (bounds.minX() + bounds.maxX() + 1) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1) * 0.5D), poseStack, buffers,
                LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        buffers.endBatch();
        return drawn;
    }

    private static int lightmap(final int light) {
        if (lightmap == null) {
            lightmap = new DynamicTexture(16, 16, false);
            lightmap.setFilter(true, false);
        }
        final NativeImage world = ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture())
                .pym$getLightPixels();
        final NativeImage pixels = lightmap.getPixels();
        final int block = LightTexture.block(light);
        final int sky = LightTexture.sky(light);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                pixels.setPixelRGBA(x, y, world.getPixelRGBA(Math.max(x, block), Math.min(y, sky)));
        lightmap.upload();
        return lightmap.getId();
    }

    private Portrait() {}
}
