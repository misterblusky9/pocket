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

/**
 * Draws a sublevel somewhere other than where it sits in the world, using the same
 * compiled section buffers, lighting and shading Sable uses for the world itself.
 *
 * <p>Portrait space: the origin is the centre of the sublevel's blocks and one unit is
 * one of its blocks at 1x. The model-view you pass maps portrait space into view space,
 * so a hand, a GUI slot or a display case is just a different matrix.
 *
 * <p>Call on the render thread, outside any other portrait, after the world has rendered.
 * Only terrain is drawn for now; block entities and Flywheel visuals are not.
 */
public final class Portrait {
    public static boolean canDraw(final ClientSubLevel subLevel) {
        return subLevel != null
                && !subLevel.isRemoved()
                && subLevel.getRenderData() != null
                && SubLevelRenderDispatcher.get() instanceof VanillaSubLevelRenderDispatcher;
    }

    /** The largest edge of the sublevel's blocks, in blocks. Divide by it to fit a portrait into a box. */
    public static int span(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return Math.max(
                bounds.maxX() - bounds.minX() + 1,
                Math.max(bounds.maxY() - bounds.minY() + 1, bounds.maxZ() - bounds.minZ() + 1));
    }

    /** Draws the sublevel centred on the pose stack's origin, its largest edge {@code size} units long. */
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
