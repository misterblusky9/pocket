package com.misterblusky9.pym.api.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.misterblusky9.pym.internal.client.PortraitGlint;
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
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class Portrait {
    private static DynamicTexture lightmap;
    private static final List<Translucent> PENDING = new ArrayList<>();
    private static int batches;
    private static final float ITEM_SPRITE_PIXELS = 16.0F;
    private static final List<ResourceLocation> GLINTS = new ArrayList<>();

    private record Translucent(ClientSubLevel subLevel, Vector3d origin, Matrix4f modelView, Matrix4f projection,
                               int light, @Nullable ResourceLocation glint) {}

    public static boolean drawing() {
        return PortraitScope.active();
    }

    public static boolean canDraw(final ClientSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || !(subLevel.getRenderData() instanceof VanillaChunkedSubLevelRenderData data))
            return false;
        data.compileSections(PrioritizeChunkUpdates.NONE, new RenderRegionCache(),
                Minecraft.getInstance().gameRenderer.getMainCamera());
        for (final RenderSection section : data.allRenderSections())
            if (section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED) return true;
        return data.allRenderSections().isEmpty();
    }

    public static void compileNow(final ClientSubLevel subLevel) {
        if (!(subLevel.getRenderData() instanceof VanillaChunkedSubLevelRenderData data)) return;
        data.rebuild();
        data.compileSections(PrioritizeChunkUpdates.PLAYER_AFFECTED, new RenderRegionCache(),
                Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    public static boolean draw(final ClientSubLevel subLevel, final Vector3dc origin, final PoseStack poseStack,
                               final MultiBufferSource buffers, final int light, final int overlay) {
        return draw(subLevel, origin, poseStack, buffers, light, overlay, entity -> false);
    }

    public static boolean draw(final ClientSubLevel subLevel, final Vector3dc origin, final PoseStack poseStack,
                               final MultiBufferSource buffers, final int light, final int overlay,
                               final Predicate<Entity> skip) {
        if (PortraitScope.full() || !canDraw(subLevel)) return false;
        final VanillaChunkedSubLevelRenderData data = (VanillaChunkedSubLevelRenderData) subLevel.getRenderData();
        final Minecraft minecraft = Minecraft.getInstance();

        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        final float fogStart = RenderSystem.getShaderFogStart();
        final float fogEnd = RenderSystem.getShaderFogEnd();
        final FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();

        flush(buffers);
        begin();
        PortraitScope.enter(subLevel, origin, modelView);
        try {
            drawLayers(subLevel, light, modelView, projection, partialTick, false, glint());

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

            final EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
            entities.setRenderShadow(false);
            try {
                for (final Entity entity : Mirrors.entities(subLevel)) {
                    if (skip.test(entity)) continue;
                    final Vec3 at = entity.getPosition(partialTick);
                    entities.render(entity, at.x - origin.x(), at.y - origin.y(), at.z - origin.z(),
                            Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick,
                            poseStack, buffers, light);
                }
            } finally {
                entities.setRenderShadow(minecraft.options.entityShadows().get());
            }
            PENDING.add(new Translucent(subLevel, new Vector3d(origin), modelView, projection, light, glint()));
        } finally {
            PortraitScope.exit();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            end(buffers);
        }
        return true;
    }

    public static void begin() {
        begin(false);
    }

    public static void begin(final boolean glint) {
        begin(glint ? ItemRenderer.ENCHANTED_GLINT_ITEM : null);
    }

    public static void begin(@Nullable final ResourceLocation glint) {
        batches++;
        GLINTS.add(glint);
    }

    private static @Nullable ResourceLocation glint() {
        for (int i = GLINTS.size() - 1; i >= 0; i--)
            if (GLINTS.get(i) != null) return GLINTS.get(i);
        return null;
    }

    public static void end(final MultiBufferSource buffers) {
        GLINTS.remove(GLINTS.size() - 1);
        if (--batches > 0 || PENDING.isEmpty()) return;
        flush(buffers);
        final List<Translucent> pending = new ArrayList<>(PENDING);
        PENDING.clear();
        final float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        final float fogStart = RenderSystem.getShaderFogStart();
        final float fogEnd = RenderSystem.getShaderFogEnd();
        final FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();
        try {
            for (final Translucent pass : pending) {
                if (pass.subLevel().isRemoved()) continue;
                PortraitScope.enter(pass.subLevel(), pass.origin(), pass.modelView());
                try {
                    drawLayers(pass.subLevel(), pass.light(), pass.modelView(), pass.projection(), partialTick, true,
                            pass.glint());
                } finally {
                    PortraitScope.exit();
                }
            }
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
    }

    private static void flush(final MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
    }

    private static boolean translucent(final RenderType layer) {
        return layer == RenderType.translucent() || layer == RenderType.tripwire();
    }

    private static void drawLayers(final ClientSubLevel subLevel, final int light, final Matrix4f modelView,
                                   final Matrix4f projection, final float partialTick, final boolean translucent,
                                   @Nullable final ResourceLocation glint) {
        final Window window = Minecraft.getInstance().getWindow();
        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        final List<ClientSubLevel> only = List.of(subLevel);
        final int lightmap = lightmap(light);
        for (final RenderType layer : RenderType.chunkBufferLayers()) {
            if (translucent(layer) != translucent) continue;
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
            if (glint != null && PortraitGlint.ready())
                drawGlint(PortraitGlint.of(glint), subLevel, dispatcher, only, layer, modelView, projection, partialTick,
                        window);
        }
        VertexBuffer.unbind();
    }

    private static void drawGlint(final RenderType glint, final ClientSubLevel subLevel,
                                  final SubLevelRenderDispatcher dispatcher, final List<ClientSubLevel> only,
                                  final RenderType layer, final Matrix4f modelView, final Matrix4f projection,
                                  final float partialTick, final Window window) {
        glint.setupRenderState();
        final ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, window);
            shader.safeGetUniform("PortraitInverse").set(new Matrix4f(modelView).invert());
            shader.safeGetUniform("GlintUnit").set(itemSpriteUv() / Math.max(1, span(subLevel)));
            shader.apply();
            dispatcher.renderSectionLayer(only, layer, shader, 0.0D, 0.0D, 0.0D, modelView, projection, partialTick);
            shader.clear();
        }
        glint.clearRenderState();
    }

    private static float itemSpriteUv() {
        final TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(MissingTextureAtlasSprite.getLocation());
        return ITEM_SPRITE_PIXELS * (sprite.getU1() - sprite.getU0()) / sprite.contents().width();
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
