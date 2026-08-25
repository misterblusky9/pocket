package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.TweezersItem;
import com.misterblusky9.pocket.network.TweezerCommandPayload;
import com.misterblusky9.pocket.network.TweezerDragPayload;
import com.misterblusky9.pocket.network.TweezerGrabPayload;
import com.misterblusky9.pocket.network.TweezerGripsPayload;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.CreateClient;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TweezerDrag {
    private static final float RANGE = TweezersItem.RANGE;
    private static final double MIN_HOLD = 0.35D;
    private static final double MAX_HOLD = 12.0D;

    private static final double MIN_STEP = 0.02D;

    private static final double SCROLL_FAST = 10.0D;

    private static final double ROTATE_SENSITIVITY = 0.35D;

    private static final SoundEvent IGNITE = sound("item.physics_staff.ignite");
    private static final SoundEvent EXTINGUISH = sound("item.physics_staff.extinguish");
    private static final SoundEvent LOCK = sound("item.physics_staff.lock");
    private static final SoundEvent UNLOCK = sound("item.physics_staff.unlock");
    private static final SoundEvent IDLE = sound("item.physics_staff.idle");

    private static SoundEvent sound(final String path) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath("simulated", path));
    }

    private static UUID subLevelId;

    private static ClientSubLevel heldCraft;

    private static final Vector3d plotAnchor = new Vector3d();
    private static double distance;
    private static final Quaterniond orientation = new Quaterniond();

    private static final Set<UUID> locked = new HashSet<>();
    private static List<TweezerGripsPayload.Grip> grips = List.of();

    private static IdleSound idleSound;

    // State
    public static boolean dragging() {
        return subLevelId != null;
    }

    public static boolean isLocked(final UUID id) {
        return id != null && locked.contains(id);
    }

    public static void acceptLocks(final List<UUID> ids) {
        locked.clear();
        if (ids != null) locked.addAll(ids);
    }

    public static void acceptGrips(final List<TweezerGripsPayload.Grip> updated) {
        grips = updated == null ? List.of() : List.copyOf(updated);
    }

    public static Vector3d anchorPlot() {
        return dragging() ? new Vector3d(plotAnchor) : null;
    }

    // Input

    public static boolean holdingTweezers() {
        return TweezersItem.isHolding(Minecraft.getInstance().player);
    }

    public static boolean acceptingInput() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen == null && holdingTweezers();
    }

    public static Vec3 focusPos(final Player player, final boolean mainHand, final float partialTick) {
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        if (player.isLocalPlayer() && !camera.isDetached()) {
            final Vec3 look = player.getViewVector(partialTick);
            final Vec3 across = Vec3.directionFromRotation(
                    0.0F, player.getViewYRot(partialTick) + 90.0F);
            return player.getEyePosition(partialTick)
                    .add(look.scale(0.35D))
                    .add(across.scale(0.22D * (mainHand ? 1 : -1)))
                    .add(0.0D, -0.18D, 0.0D);
        }

        final Vec3 viewDirection =
                player.calculateViewVector(0.0F, player.getPreciseBodyRotation(partialTick));
        final Vec3 handDirection =
                player.calculateViewVector(0.0F, player.getPreciseBodyRotation(partialTick) + 90.0F);
        return player.getPosition(partialTick)
                .add(0.0D, 1.28D, 0.0D)
                .add(viewDirection.scale(1.275D))
                .add(handDirection.scale(0.325D * (mainHand ? 1 : -1)));
    }

    public static boolean mainHand(final Player player) {
        return player.getMainHandItem().getItem() instanceof TweezersItem
                || !(player.getOffhandItem().getItem() instanceof TweezersItem);
    }

    public static void toggleDrag() {
        if (dragging()) {
            endDrag();
            return;
        }
        beginDrag(Minecraft.getInstance().player);
    }

    private static void beginDrag(final LocalPlayer player) {
        if (player == null || dragging()) return;

        final BlockHitResult hit = pick(player);
        if (hit == null) return;

        final ClientSubLevel craft = Sable.HELPER.getContainingClient(hit.getLocation());
        if (craft == null || craft.isRemoved()) return;

        startDragging(player, craft, hit.getBlockPos(), true);
        effects(player, craft, hit.getLocation());
    }

    private static void startDragging(
            final LocalPlayer player,
            final ClientSubLevel craft,
            final BlockPos blockPos,
            final boolean announce
    ) {
        final UUID id = craft.getUniqueId();
        if (id == null) return;

        final Vector3d anchor = JOMLConversion.atCenterOf(blockPos);
        final Pose3dc pose = craft.logicalPose();

        subLevelId = id;
        heldCraft = craft;
        plotAnchor.set(anchor);
        orientation.set(pose.orientation());
        distance = clamp(player.getEyePosition()
                .distanceTo(pose.transformPosition(JOMLConversion.toMojang(anchor))));

        if (announce) play(player, isLocked(id) ? UNLOCK : IGNITE);
        locked.remove(id);

        final Vec3 goal = goal(player);
        PacketDistributor.sendToServer(new TweezerGrabPayload(
                id, plotAnchor.x, plotAnchor.y, plotAnchor.z,
                goal.x, goal.y, goal.z,
                orientation.x(), orientation.y(), orientation.z(), orientation.w()));
    }

    public static void endDrag() {
        if (!dragging()) return;

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) play(player, EXTINGUISH);
        release();
    }

    private static void release() {
        if (!dragging()) return;

        subLevelId = null;
        heldCraft = null;
        PacketDistributor.sendToServer(TweezerCommandPayload.stop());

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) TweezerBeamRenderer.dropBeam(player.getUUID());
    }

    public static void punch() {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        if (dragging()) {
            final UUID id = subLevelId;
            final ClientSubLevel craft = heldCraft;
            final Vec3 contact = JOMLConversion.toMojang(plotAnchor);

            final boolean wasLocked = toggleLockOn(player, id);
            effects(player, craft, contact);

            if (!wasLocked) release();
            return;
        }

        final BlockHitResult hit = pick(player);
        if (hit == null) return;

        final ClientSubLevel craft = Sable.HELPER.getContainingClient(hit.getLocation());
        if (craft == null || craft.isRemoved() || craft.getUniqueId() == null) return;

        final boolean wasLocked = toggleLockOn(player, craft.getUniqueId());

        TweezerBeamRenderer.updateBeam(
                minecraft.level,
                player.getUUID(),
                focusPos(player, mainHand(player), 1.0F),
                hit.getLocation());

        effects(player, craft, hit.getLocation());

        if (wasLocked) startDragging(player, craft, hit.getBlockPos(), false);
    }

    private static boolean toggleLockOn(final LocalPlayer player, final UUID id) {
        final boolean wasLocked = isLocked(id);

        PacketDistributor.sendToServer(TweezerCommandPayload.lock(id));
        if (wasLocked) locked.remove(id); else locked.add(id);

        play(player, wasLocked ? UNLOCK : LOCK);
        return wasLocked;
    }

    private static BlockHitResult pick(final LocalPlayer player) {
        final HitResult hit = player.pick(RANGE, 1.0F, false);
        if (!(hit instanceof final BlockHitResult block) || block.getType() == HitResult.Type.MISS) {
            return null;
        }
        return block;
    }

    private static void effects(
            final LocalPlayer player, final ClientSubLevel craft, final Vec3 contactPlot
    ) {
        final Level level = player.level();
        if (craft == null || contactPlot == null) return;

        CreateClient.ZAPPER_RENDER_HANDLER.shoot(
                InteractionHand.MAIN_HAND, craft.logicalPose().transformPosition(contactPlot));

        final RandomSource random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            level.addParticle(
                    ParticleTypes.END_ROD,
                    contactPlot.x, contactPlot.y, contactPlot.z,
                    (random.nextDouble() - 0.5D) * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.2D);
        }
    }

    private static void play(final LocalPlayer player, final SoundEvent event) {
        player.playSound(event);
    }

    public static boolean rotate(final double dx, final double dy) {
        if (!dragging() || !PocketKeys.rotateHeld()) return false;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        final double look = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
        final double scaled = look * look * look * 8.0D * 0.15D;

        orientation.rotateLocalY(Math.toRadians(dx * scaled * ROTATE_SENSITIVITY));

        final Vec3 across = Vec3.directionFromRotation(0.0F, minecraft.player.getYRot() + 90.0F);
        orientation.premul(new Quaterniond(new AxisAngle4d(
                Math.toRadians(dy * scaled * ROTATE_SENSITIVITY), across.x, across.y, across.z)));
        return true;
    }

    public static void onScroll(final InputEvent.MouseScrollingEvent event) {
        if (!dragging()) return;

        final double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;

        event.setCanceled(true);

        final boolean fast = Minecraft.getInstance().options.keySprint.isDown();
        distance = clamp(distance + delta * step() * (fast ? SCROLL_FAST : 1.0D));
    }

    public static void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null) {
            subLevelId = null;
            heldCraft = null;
            locked.clear();
            grips = List.of();
            idleSound = null;
            TweezerBeamRenderer.clear();
            return;
        }

        if (dragging() && !holdingTweezers()) release();

        if (dragging() && (heldCraft == null || heldCraft.isRemoved())) release();

        if (dragging()) {
            TweezerBeamRenderer.updateBeam(
                    minecraft.level,
                    player.getUUID(),
                    focusPos(player, mainHand(player), 1.0F),
                    JOMLConversion.toMojang(plotAnchor));

            final Vec3 goal = goal(player);
            PacketDistributor.sendToServer(new TweezerDragPayload(
                    goal.x, goal.y, goal.z,
                    orientation.x(), orientation.y(), orientation.z(), orientation.w()));
        }

        for (final TweezerGripsPayload.Grip grip : grips) {
            if (grip.playerId().equals(player.getUUID())) continue;

            final Player other = minecraft.level.getPlayerByUUID(grip.playerId());
            if (other == null) continue;

            TweezerBeamRenderer.updateBeam(
                    minecraft.level,
                    grip.playerId(),
                    focusPos(other, mainHand(other), 1.0F),
                    new Vec3(grip.anchorX(), grip.anchorY(), grip.anchorZ()));
        }

        idle(player);
        TweezerBeamRenderer.tick();
    }

    private static void idle(final LocalPlayer player) {
        if (!dragging()) {
            if (idleSound != null) idleSound.setVolume(0.0F);
            return;
        }

        if (idleSound == null) idleSound = new IdleSound(player, IDLE);
        if (!Minecraft.getInstance().getSoundManager().isActive(idleSound)) {
            Minecraft.getInstance().getSoundManager().play(idleSound);
        }
        idleSound.setVolume(1.0F);
    }

    private static Vec3 goal(final Player player) {
        return player.getLookAngle().scale(distance);
    }

    private static double step() {
        return Math.max(MIN_STEP, ScaleState.getClientScale(subLevelId));
    }

    private static double clamp(final double value) {
        return Math.max(MIN_HOLD, Math.min(MAX_HOLD, value));
    }

    private static final class IdleSound extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        private IdleSound(final LocalPlayer player, final SoundEvent event) {
            super(event, SoundSource.PLAYERS, player.level().getRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
        }

        private void setVolume(final float volume) {
            this.volume = volume;
        }

        @Override
        public double getX() {
            return this.player.position().x();
        }

        @Override
        public double getY() {
            return this.player.position().y();
        }

        @Override
        public double getZ() {
            return this.player.position().z();
        }

        @Override
        public void tick() {}
    }

    private TweezerDrag() {}
}
