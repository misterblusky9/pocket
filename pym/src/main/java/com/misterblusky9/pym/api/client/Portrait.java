package com.misterblusky9.pym.api.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.misterblusky9.pym.internal.client.PortraitScope;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.util.List;

public final class Portrait {
    public static boolean canDraw(final ClientSubLevel subLevel) {
        return subLevel != null
                && !subLevel.isRemoved()
                && subLevel.getRenderData() != null
                && SubLevelRenderDispatcher.get() instanceof VanillaSubLevelRenderDispatcher;
    }

    public static int span(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return Math.max(
                bounds.maxX() - bounds.minX() + 1,
                Math.max(bounds.maxY() - bounds.minY() + 1, bounds.maxZ() - bounds.minZ() + 1));
    }

    public static boolean drawFitted(final ClientSubLevel subLevel, final PoseStack poseStack, final float size) {
        if (!canDraw(subLevel)) return false;

        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(poseStack.last().pose())
                .scale(size / Math.max(1, span(subLevel)));
        return draw(subLevel, modelView, new Matrix4f(RenderSystem.getProjectionMatrix()));
    }

    public static boolean draw(final ClientSubLevel subLevel, final Matrix4f modelView, final Matrix4f projection) {
        if (!canDraw(subLevel)) return false;

        final Minecraft minecraft = Minecraft.getInstance();
        final Window window = minecraft.getWindow();
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        final List<ClientSubLevel> only = List.of(subLevel);

        final float fogStart = RenderSystem.getShaderFogStart();
        final float fogEnd = RenderSystem.getShaderFogEnd();
        final FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();

        PortraitScope.enter(subLevel, modelView);
        try {
            for (final RenderType layer : RenderType.chunkBufferLayers()) {
                layer.setupRenderState();
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
            if (VanillaSubLevelRenderDispatcher.isSingleBlock(subLevel)) {
                dispatcher.renderAfterSections(only, 0.0D, 0.0D, 0.0D, modelView, projection, partialTick);
            }
        } finally {
            VertexBuffer.unbind();
            PortraitScope.exit();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
        return true;
    }

    private Portrait() {}
}
