package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.LiveCaseItem;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LiveCaseItemRenderer extends CustomRenderedItemModelRenderer {
    private static final double TRUE_SCALE = 1.0D / 16.0D;
    private static final double MAX_FOOTPRINT = 12.0D / 16.0D;
    private static final double HAND_SCALE = 1.0D / 8.0D;
    private static final double FLOOR_Y = -0.5D;
    private static final double WORN_SIZE = 1.0D;
    private static final double HEAD_BLOCK = 1.0D / 0.625D * 1.25;
    private static final double HEAD_TOP_Y = 1.5D;
    private static final long MODEL_SEED = 42L;

    private final Matrix4f modelView = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();
    private final float[] savedNormal = new float[9];
    private final RandomSource random = RandomSource.create();

    private record Member(ClientSubLevel subLevel, BoundingBox3ic bounds, BlockPos reference, Matrix4d toRoot) {}

    @Override
    protected void render(
            final ItemStack stack,
            final CustomRenderedItemModel model,
            final PartialItemModelRenderer renderer,
            final ItemDisplayContext displayContext,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final List<Member> members = members(stack);
        if (members.isEmpty()) {
            renderer.renderSolid(model.getOriginalModel(), light);
            return;
        }

        final BoundingBox3ic rootBounds = members.get(0).bounds();
        final double sizeX = rootBounds.maxX() - rootBounds.minX() + 1;
        final double sizeY = rootBounds.maxY() - rootBounds.minY() + 1;
        final double sizeZ = rootBounds.maxZ() - rootBounds.minZ() + 1;

        final boolean hand = inHand(displayContext);
        final boolean head = displayContext == ItemDisplayContext.HEAD;
        final float fit = (float) (head
                ? members.get(0).subLevel().renderPose().scale().x() * WORN_SIZE * HEAD_BLOCK
                : hand ? HAND_SCALE : Math.min(TRUE_SCALE, MAX_FOOTPRINT / Math.max(sizeX, sizeZ)));
        final Vector3d origin = new Vector3d(
                rootBounds.minX() + sizeX * 0.5D,
                rootBounds.minY() + (head ? sizeY : hand ? sizeY * 0.5D : 0.0D),
                rootBounds.minZ() + sizeZ * 0.5D);

        ms.pushPose();
        ms.translate(0.0D, head ? HEAD_TOP_Y : hand ? 0.0D : FLOOR_Y, 0.0D);
        if (head) wear(ms, LiveCaseItem.wornPose(stack));
        ms.scale(fit, fit, fit);

        for (final Member member : members) {
            final Matrix4f local = new Matrix4f(
                    new Matrix4d().translation(-origin.x, -origin.y, -origin.z).mul(member.toRoot()));
            ms.pushPose();
            ms.last().pose().mul(local);
            ms.last().normal().mul(new Matrix3f(local).normal());
            drawMember(member, ms, buffer, light, overlay);
            ms.popPose();
        }

        ms.popPose();
    }

    private static void wear(final PoseStack ms, final float[] pose) {
        final double pixel = HEAD_BLOCK / 16.0D;
        ms.translate(pose[0] * pixel, pose[1] * pixel, pose[2] * pixel);
        ms.mulPose(Axis.YP.rotationDegrees(pose[4]));
        ms.mulPose(Axis.XP.rotationDegrees(pose[3]));
        ms.mulPose(Axis.ZP.rotationDegrees(pose[5]));
    }

    private static boolean inHand(final ItemDisplayContext context) {
        return context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    // Group

    private static List<Member> members(final ItemStack stack) {
        final List<Member> members = new ArrayList<>();
        final ClientLevel level = Minecraft.getInstance().level;
        final UUID rootId = LiveCaseItem.held(stack);
        if (level == null || rootId == null) return members;
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return members;

        final ClientSubLevel root = visible(container, rootId);
        if (root == null) return members;
        final Matrix4d toRoot = plotToWorld(root.renderPose()).invert();

        for (final UUID id : LiveCaseItem.group(stack)) {
            final ClientSubLevel subLevel = visible(container, id);
            if (subLevel == null) continue;
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.equals(BoundingBox3i.EMPTY)) continue;
            final SubLevelRenderData data = subLevel.getRenderData();
            if (!(data instanceof VanillaChunkedSubLevelRenderData) && !(data instanceof VanillaSingleSubLevelRenderData)) {
                continue;
            }

            final BlockPos reference = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
            final Matrix4d local = new Matrix4d(toRoot)
                    .mul(plotToWorld(subLevel.renderPose()))
                    .translate(reference.getX(), reference.getY(), reference.getZ());
            members.add(new Member(subLevel, bounds, reference, local));
        }
        return members;
    }

    @Nullable
    private static ClientSubLevel visible(final ClientSubLevelContainer container, final UUID id) {
        return container.getSubLevel(id) instanceof final ClientSubLevel subLevel
                && !subLevel.isRemoved() && subLevel.isFinalized() ? subLevel : null;
    }

    private static Matrix4d plotToWorld(final Pose3dc pose) {
        return new Matrix4d()
                .translation(pose.position())
                .rotate(new Quaterniond(pose.orientation()))
                .scale(pose.scale())
                .translate(-pose.rotationPoint().x(), -pose.rotationPoint().y(), -pose.rotationPoint().z());
    }

    // Member

    private void drawMember(
            final Member member,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final ClientSubLevel subLevel = member.subLevel();
        if (subLevel.getRenderData() instanceof final VanillaChunkedSubLevelRenderData chunked) {
            terrain(chunked, member.reference(), ms.last().pose(), light);
        } else {
            renderModel(subLevel.getLevel().getBlockState(member.reference()), ms, buffer, light, overlay);
        }
        blockEntities(member, ms, buffer, light, overlay);
        contraptions(member, ms, buffer, light);
    }

    // Terrain

    private void terrain(
            final VanillaChunkedSubLevelRenderData data,
            final BlockPos reference,
            final Matrix4f pose,
            final int light
    ) {
        this.modelView.set(RenderSystem.getModelViewMatrix()).mul(pose);
        this.modelView.get3x3(this.normal);
        final Matrix4f projection = RenderSystem.getProjectionMatrix();
        final Window window = Minecraft.getInstance().getWindow();
        final float skyScale = LightTexture.sky(light) / 15.0F;

        for (final RenderType layer : RenderType.chunkBufferLayers()) {
            if (!hasLayer(data, layer)) continue;

            layer.setupRenderState();
            final ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                layer.clearRenderState();
                continue;
            }

            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, this.modelView, projection, window);
            if (shader.FOG_START != null) shader.FOG_START.set(Float.MAX_VALUE);
            shader.apply();

            final Uniform normalMat = shader.getUniform("NormalMat");
            if (normalMat != null) {
                final FloatBuffer current = normalMat.getFloatBuffer();
                for (int i = 0; i < this.savedNormal.length; i++) this.savedNormal[i] = current.get(i);
                normalMat.set(this.normal);
                normalMat.upload();
            }
            upload(shader, "SableEnableNormalLighting", 1.0F);
            upload(shader, "SableShadowsEnabled", 0.0F);
            upload(shader, "SableSkyLightScale", skyScale);

            final Uniform chunkOffset = shader.CHUNK_OFFSET;
            for (final RenderSection section : data.allRenderSections()) {
                if (section.getCompiled().isEmpty(layer)) continue;
                if (chunkOffset != null) {
                    final BlockPos origin = section.getOrigin();
                    chunkOffset.set(
                            (float) (origin.getX() - reference.getX()),
                            (float) (origin.getY() - reference.getY()),
                            (float) (origin.getZ() - reference.getZ()));
                    chunkOffset.upload();
                }
                final VertexBuffer vertices = section.getBuffer(layer);
                vertices.bind();
                vertices.draw();
            }

            if (chunkOffset != null) chunkOffset.set(0.0F, 0.0F, 0.0F);
            if (normalMat != null) {
                normalMat.set(this.savedNormal);
                normalMat.upload();
            }
            upload(shader, "SableEnableNormalLighting", 0.0F);
            shader.clear();
            layer.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    private static boolean hasLayer(final VanillaChunkedSubLevelRenderData data, final RenderType layer) {
        for (final RenderSection section : data.allRenderSections()) {
            if (!section.getCompiled().isEmpty(layer)) return true;
        }
        return false;
    }

    private static void upload(final ShaderInstance shader, final String name, final float value) {
        final Uniform uniform = shader.getUniform(name);
        if (uniform == null) return;
        uniform.set(value);
        uniform.upload();
    }

    // Block entities

    private void blockEntities(
            final Member member,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final boolean visualized = VisualizationManager.supportsVisualization(member.subLevel().getLevel());
        final BlockPos reference = member.reference();

        for (final PlotChunkHolder holder : member.subLevel().getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                final BlockPos pos = blockEntity.getBlockPos();
                ms.pushPose();
                ms.translate(pos.getX() - reference.getX(), pos.getY() - reference.getY(), pos.getZ() - reference.getZ());
                try {
                    if (visualized && blockEntity instanceof final KineticBlockEntity kinetic) {
                        spinningModel(kinetic, ms, buffer, light, overlay);
                    } else {
                        final BlockEntityRenderer<BlockEntity> renderer =
                                minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
                        if (renderer != null) renderer.render(blockEntity, partialTick, ms, buffer, light, overlay);
                    }
                } catch (final RuntimeException ignored) {
                }
                ms.popPose();
            }
        }
    }

    private void spinningModel(
            final KineticBlockEntity kinetic,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final BlockState state = kinetic.getBlockState();
        if (state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) return;
        if (state.getBlock() instanceof final IRotate rotate) {
            final Direction.Axis axis = rotate.getRotationAxis(state);
            final float angle = KineticBlockEntityRenderer.getAngleForBe(kinetic, kinetic.getBlockPos(), axis);
            ms.translate(0.5D, 0.5D, 0.5D);
            ms.mulPose(switch (axis) {
                case X -> Axis.XP.rotation(angle);
                case Y -> Axis.YP.rotation(angle);
                case Z -> Axis.ZP.rotation(angle);
            });
            ms.translate(-0.5D, -0.5D, -0.5D);
        }
        renderModel(state, ms, buffer, light, overlay);
    }

    private void renderModel(
            final BlockState state,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        if (state.isAir()) return;
        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        final BakedModel model = dispatcher.getBlockModel(state);
        this.random.setSeed(MODEL_SEED);
        for (final RenderType layer : model.getRenderTypes(state, this.random, ModelData.EMPTY)) {
            dispatcher.getModelRenderer().renderModel(
                    ms.last(), buffer.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false)),
                    state, model, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, layer);
        }
    }

    // Contraptions

    private static void contraptions(
            final Member member,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light
    ) {
        final ClientSubLevel subLevel = member.subLevel();
        final BoundingBox3ic bounds = member.bounds();
        final BlockPos reference = member.reference();
        final float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        final AABB area = new AABB(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 2, bounds.maxY() + 2, bounds.maxZ() + 2);

        for (final AbstractContraptionEntity entity
                : subLevel.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, area)) {
            final Contraption contraption = entity.getContraption();
            if (contraption == null || !entity.isReadyForRender()) continue;

            final Vec3 at = entity.getPosition(partialTick);
            ms.pushPose();
            ms.translate(at.x - reference.getX(), at.y - reference.getY(), at.z - reference.getZ());
            entity.applyLocalTransforms(ms, partialTick);
            try {
                final var renderWorld = contraption.getOrCreateClientContraptionLazy().getRenderLevel();
                for (final RenderType layer : RenderType.chunkBufferLayers()) {
                    final SuperByteBuffer mesh = ContraptionEntityRenderer.getBuffer(contraption, renderWorld, layer);
                    if (!mesh.isEmpty()) mesh.light(light).renderInto(ms, buffer.getBuffer(layer));
                }
            } catch (final RuntimeException ignored) {
            }
            ms.popPose();
        }
    }
}
