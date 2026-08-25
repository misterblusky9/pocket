package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.item.TweezersItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TweezerBeamRenderer {
    private static final String HOVER_SLOT = "pocketTweezerSelection";

    private static final int HOVER_COLOUR = 0xBFBFBF;
    private static final float HOVER_LINE_WIDTH = 1.0F / 32.0F;

    private static final Map<UUID, Beam> BEAMS = new HashMap<>();

    // Beams

    public static void updateBeam(
            final Level level, final UUID playerId, final Vec3 start, final Vec3 endPlot
    ) {
        final Beam beam = BEAMS.get(playerId);
        if (beam == null) {
            BEAMS.put(playerId, new Beam(start, endPlot,
                    Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, start, endPlot))));
            return;
        }

        beam.serverStart = start;
        beam.serverEnd = endPlot;
        beam.intensity = 1.0F;
    }

    public static void dropBeam(final UUID playerId) {
        BEAMS.remove(playerId);
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static void tick() {
        BEAMS.values().removeIf(beam -> beam.intensity < 0.4F);
        for (final Beam beam : BEAMS.values()) {
            beam.previousStart = beam.start;
            beam.previousEnd = beam.end;
            beam.start = beam.serverStart;
            beam.end = beam.serverEnd;
            beam.intensity *= 0.6F;
            beam.update();
        }
    }

    // Frame
    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui) return;

        final float partialTick = AnimationTickHolder.getPartialTicks();
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poses = event.getPoseStack();

        renderBeams(minecraft, poses, camera, partialTick);

        if (!TweezerDrag.holdingTweezers()) return;

        renderLocks(minecraft, poses, camera);
        showHover(minecraft, player, partialTick);
    }

    private static void renderBeams(
            final Minecraft minecraft, final PoseStack poses, final Vec3 camera, final float partialTick
    ) {
        if (BEAMS.isEmpty()) return;

        final SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        boolean drew = false;

        for (final Map.Entry<UUID, Beam> entry : BEAMS.entrySet()) {
            final Player owner = minecraft.level.getPlayerByUUID(entry.getKey());
            if (owner == null) continue;

            final Beam beam = entry.getValue();

            final Vec3 endPlot = beam.previousEnd.lerp(beam.end, partialTick);
            final ClientSubLevel craft = Sable.HELPER.getContainingClient(endPlot);
            if (craft == null || craft.isRemoved()) continue;

            final Vec3 end = craft.renderPose(partialTick).transformPosition(endPlot);
            final Vec3 start = TweezerDrag.focusPos(owner, TweezerDrag.mainHand(owner), partialTick);

            beam.render(start, end, poses, buffer, camera, partialTick);
            drew = true;
        }

        if (drew) buffer.draw();
    }

    private static void renderLocks(
            final Minecraft minecraft, final PoseStack poses, final Vec3 camera
    ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer consumer = buffers.getBuffer(PocketRenderTypes.LOCK_TAG);
        boolean drew = false;

        for (final var raw : container.getAllSubLevels()) {
            if (!(raw instanceof final ClientSubLevel pinned) || pinned.isRemoved()) continue;
            if (!TweezerDrag.isLocked(pinned.getUniqueId())) continue;

            final Vector3dc at = pinned.renderPose().position();

            poses.pushPose();
            poses.translate(at.x() - camera.x, at.y() - camera.y, at.z() - camera.z);
            poses.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());

            final PoseStack.Pose pose = poses.last();
            consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(15728880);
            consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(15728880);
            consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(15728880);
            consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(15728880);

            poses.popPose();
            drew = true;
        }

        if (drew) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            try {
                buffers.endBatch(PocketRenderTypes.LOCK_TAG);
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        }
    }

    private static void showHover(
            final Minecraft minecraft, final LocalPlayer player, final float partialTick
    ) {
        final BlockPos hover = hoverPos(minecraft, player, partialTick);
        if (hover == null) return;

        Outliner.getInstance()
                .showCluster(HOVER_SLOT, List.of(hover))
                .colored(HOVER_COLOUR)
                .disableLineNormals()
                .lineWidth(HOVER_LINE_WIDTH)
                .withFaceTexture(AllSpecialTextures.CHECKERED);
    }

    private static final ThreadLocal<Boolean> RENDER_POSE_PICK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Set<LevelPoseProviderExtension> POSE_SUPPLIER_INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static void installRenderPoseSupplier(final LevelPoseProviderExtension poses) {
        if (!POSE_SUPPLIER_INSTALLED.add(poses)) return;

        poses.sable$pushPoseSupplier(subLevel -> RENDER_POSE_PICK.get() && subLevel instanceof final ClientSubLevel client
                ? client.renderPose()
                : ((SubLevel) subLevel).logicalPose());
    }

    private static BlockPos hoverPos(
            final Minecraft minecraft, final LocalPlayer player, final float partialTick
    ) {
        final Vector3d held = TweezerDrag.anchorPlot();
        if (held != null) return BlockPos.containing(held.x, held.y, held.z);

        final ClientLevel level = minecraft.level;
        if (!(level instanceof final LevelPoseProviderExtension poses)) return null;

        installRenderPoseSupplier(poses);
        final HitResult hit;
        RENDER_POSE_PICK.set(Boolean.TRUE);
        try {
            hit = player.pick(TweezersItem.RANGE, partialTick, false);
        } finally {
            RENDER_POSE_PICK.set(Boolean.FALSE);
        }

        if (!(hit instanceof final BlockHitResult block) || block.getType() == HitResult.Type.MISS) {
            return null;
        }
        return Sable.HELPER.getContainingClient(hit.getLocation()) != null ? block.getBlockPos() : null;
    }

    // Beam

    private static final class Beam {
        private final LineOutline line = new LineOutline();
        private final List<Node> nodes = new ArrayList<>();

        private float intensity;
        private Vec3 start;
        private Vec3 end;
        private Vec3 previousStart;
        private Vec3 previousEnd;
        private Vec3 serverStart;
        private Vec3 serverEnd;
        private double length;
        private double nodeRadius;

        private Beam(final Vec3 start, final Vec3 end, final double length) {
            this.start = start;
            this.previousStart = start;
            this.serverStart = start;
            this.end = end;
            this.previousEnd = end;
            this.serverEnd = end;
            this.intensity = 1.0F;
            this.length = length;

            this.line.getParams().colored(0xFFFFFF).disableLineNormals().lineWidth(0.0375F);
            update();
        }

        private void update() {
            final double scaledLength = this.length / 1.5D;
            final double targetCount = 64.0D / (scaledLength + 8.0D) + scaledLength;
            if (targetCount > 4096.0D) return;

            this.nodeRadius = 0.2D * Math.sqrt(scaledLength / targetCount);

            while (this.nodes.size() < targetCount - 0.7D) this.nodes.add(new Node());
            while (this.nodes.size() > targetCount + 0.7D) this.nodes.remove(0);

            for (int i = 1; i < this.nodes.size() - 1; i++) this.nodes.get(i).update();
        }

        private void render(
                final Vec3 start,
                final Vec3 end,
                final PoseStack poses,
                final SuperRenderTypeBuffer buffer,
                final Vec3 camera,
                final float partialTick
        ) {
            if (this.nodes.isEmpty()) return;

            final Vec3 relative = end.subtract(start);
            this.length = relative.length();

            Vec3 last = start;
            for (int i = 1; i < this.nodes.size(); i++) {
                final Vec3 offset = this.nodes.get(i).interpolated(partialTick);
                final Vec3 current = start.add(
                        relative.scale((float) i / (float) this.nodes.size())
                                .add(offset.scale(this.nodeRadius)));
                this.line.set(last, current).render(poses, buffer, camera, partialTick);
                last = current;
            }
        }
    }

    private static final class Node {
        private Vec3 position = Vec3.ZERO;
        private Vec3 previousPosition = Vec3.ZERO;

        private void update() {
            final RandomSource random = Minecraft.getInstance().level.random;
            this.previousPosition = this.position;
            this.position = this.position.offsetRandom(random, 3.0F).scale(0.5D);
        }

        private Vec3 interpolated(final float partialTick) {
            return this.previousPosition.lerp(this.position, partialTick);
        }
    }

    private TweezerBeamRenderer() {}
}
