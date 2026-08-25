package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.misterblusky9.pocket.physics.PlotShape;
import com.misterblusky9.pocket.physics.PlotShapeCache;
import org.joml.Vector3d;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionBeamRenderer {
    private static final int SHRINK_COLOUR = 0x9AF0FF;
    private static final int GROW_COLOUR = 0xFFD24A;
    private static final float LINE_WIDTH = 0.0375F;

    private static final Vec3 BARREL_OFFSET = new Vec3(0.45D, -0.15D, 1.2D);

    private static final double RANGE = 160.0D;
    private static final double MISS_REACH_FRACTION = 0.35D;

    private static final float CHARGE_TICKS =
            com.misterblusky9.pocket.item.CompressionGunItem.CHARGE_TICKS;

    private static final double TRAVEL_SPEED = 6.5D;

    private static final double LAUNCH_ARC = 1.05D;
    private static final float ARC_SETTLE_TICKS = 5.0F;

    public static final int SURGE_TRAVEL_TICKS = 5;
    private static final float SURGE_WIDTH = 0.28F;
    private static final float SURGE_SWELL = 2.6F;

    private static final float WIDTH_AT_MUZZLE = 1.35F;
    private static final float WIDTH_AT_TIP = 0.55F;

    private static final int IMPACT_PARTICLES_PER_TICK = 1;
    private static final int IMPACT_PARTICLES_SURGE = 3;
    private static final double IMPACT_SPEED = 0.06D;
    private static final double IMPACT_SPREAD = 0.09D;

    private static final double SEGMENT_SPACING = 1.1D;
    private static final int MIN_NODES = 4;
    private static final int MAX_NODES = 64;

    private static final double NODE_RADIUS_BASE = 0.055D;
    private static final double WOBBLE_REFERENCE_LENGTH = 4.0D;
    private static final double MAX_WOBBLE_FACTOR = 4.0D;

    private static final float NODE_FOLLOW_ENDS = 0.95F;
    private static final float NODE_FOLLOW_MIDDLE = 0.72F;

    private static final double SURGE_WOBBLE = 0.10D;
    private static final float SURGE_DECAY = 0.86F;

    private static final Map<UUID, Beam> ACTIVE = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private CompressionBeamRenderer() {}

    public static void setFiring(final UUID playerId, final boolean firing, final boolean growing) {
        if (playerId == null) return;
        if (!firing) {
            ACTIVE.remove(playerId);
            return;
        }
        ACTIVE.computeIfAbsent(playerId, Beam::new).setGrowing(growing);
    }

    public static void setTarget(final UUID playerId, final UUID subLevelId) {
        if (playerId == null) return;
        final Beam beam = ACTIVE.get(playerId);
        if (beam != null) beam.lockedTarget = subLevelId;
    }

    public static Vec3 landingOn(final UUID playerId, final UUID subLevelId) {
        if (playerId == null || subLevelId == null) return null;
        final Beam beam = ACTIVE.get(playerId);
        if (beam == null || !subLevelId.equals(beam.lockedTarget)) return null;
        return beam.endpoint;
    }

    public static void clearTarget(final UUID subLevelId) {
        if (subLevelId == null) return;
        for (final Beam beam : ACTIVE.values()) {
            if (subLevelId.equals(beam.lockedTarget)) beam.lockedTarget = null;
        }
    }

    public static boolean surge(final UUID playerId, final UUID subLevelId) {
        final Beam beam = ACTIVE.get(playerId);
        if (beam == null || !beam.canCarry()) return false;

        beam.beginSurge(subLevelId);
        return true;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;

        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }

        for (final Map.Entry<UUID, Beam> entry : ACTIVE.entrySet()) {
            final Player owner = level.getPlayerByUUID(entry.getKey());
            if (owner == null) continue;
            entry.getValue().tick(owner, level);
        }
    }

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE.isEmpty()) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) return;

        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poses = event.getPoseStack();
        final SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();

        boolean drew = false;
        for (final Map.Entry<UUID, Beam> entry : ACTIVE.entrySet()) {
            final Player owner = level.getPlayerByUUID(entry.getKey());
            if (owner == null) continue;
            if (entry.getValue().render(poses, buffer, camera, partialTick, muzzleOf(owner, partialTick))) {
                drew = true;
            }
        }

        if (drew) buffer.draw();
    }

    private static Vec3 muzzleOf(final Player player) {
        return muzzleOf(player, 1.0F);
    }

    private static Vec3 muzzleOf(final Player player, final float partialTick) {
        final boolean mainHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND;

        final double x = Mth.lerp(partialTick, player.xo, player.getX());
        final double y = Mth.lerp(partialTick, player.yo, player.getY());
        final double z = Mth.lerp(partialTick, player.zo, player.getZ());
        final Vec3 start = new Vec3(x, y + player.getEyeHeight(), z);

        final float yaw = (float) ((Mth.lerp(partialTick, player.yRotO, player.getYRot())) / -180.0F * Math.PI);
        final float pitch = (float) ((Mth.lerp(partialTick, player.xRotO, player.getXRot())) / -180.0F * Math.PI);

        final int flip = mainHand == (player.getMainArm() == HumanoidArm.RIGHT) ? -1 : 1;
        final Vec3 local = new Vec3(flip * BARREL_OFFSET.x, BARREL_OFFSET.y, BARREL_OFFSET.z);
        return start.add(local.xRot(pitch).yRot(yaw));
    }

    private record Landing(Vec3 point, boolean struck) {}

    private static Landing endpointOf(final Player player, final Level level, final UUID lockedTarget) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));

        final BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        final SubLevel struck = hit == null || hit.getType() == HitResult.Type.MISS
                ? null : Sable.HELPER.getContaining(level, hit.getBlockPos());

        if (lockedTarget != null && !lockedTarget.equals(idOf(struck))) {
            final Vec3 held = silhouettePoint(level, lockedTarget, eye, player.getViewVector(1.0F));
            if (held != null) return new Landing(held, true);
        }

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return new Landing(
                    eye.add(player.getViewVector(1.0F).scale(RANGE * MISS_REACH_FRACTION)), false);
        }

        final SubLevel found = struck;
        if (!(found instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            return new Landing(hit.getLocation(), true);
        }

        final Pose3dc pose = subLevel.renderPose();
        final Vector3dc position = pose.position();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Vector3dc scale = pose.scale();

        final org.joml.Vector3d local = new org.joml.Vector3d(
                hit.getLocation().x - rotationPoint.x(),
                hit.getLocation().y - rotationPoint.y(),
                hit.getLocation().z - rotationPoint.z()
        ).mul(scale.x(), scale.y(), scale.z());

        pose.orientation().transform(local);
        return new Landing(
                new Vec3(local.x + position.x(), local.y + position.y(), local.z + position.z()),
                true);
    }

    private static UUID idOf(final SubLevel subLevel) {
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    private static Vec3 silhouettePoint(
            final Level level,
            final UUID subLevelId,
            final Vec3 eye,
            final Vec3 look
    ) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return null;

        final SubLevel found = container.getSubLevel(subLevelId);
        if (!(found instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return null;

        final PlotShape shape = PlotShapeCache.get(subLevel);
        final Pose3dc pose = subLevel.renderPose();

        if (shape != null && pose != null) {
            final Vector3d localEye = pose.transformPositionInverse(
                    new Vector3d(eye.x, eye.y, eye.z));
            final Vector3d localTip = pose.transformPositionInverse(
                    new Vector3d(eye.x + look.x * RANGE, eye.y + look.y * RANGE, eye.z + look.z * RANGE));

            final Vector3d localDirection = new Vector3d(localTip).sub(localEye);
            final double localLength = localDirection.length();
            if (localLength > 1.0E-6D) {
                localDirection.div(localLength);

                final Vector3d local = shape.closestPointToRay(localEye, localDirection, localLength);
                if (local != null) {
                    final Vector3d world = pose.transformPosition(new Vector3d(local));
                    return new Vec3(world.x, world.y, world.z);
                }
            }
        }

        final var box = subLevel.boundingBox();
        if (box == null) return null;

        Vec3 onBox = new Vec3(
                (box.minX() + box.maxX()) * 0.5D,
                (box.minY() + box.maxY()) * 0.5D,
                (box.minZ() + box.maxZ()) * 0.5D);

        for (int i = 0; i < 3; i++) {
            final double along = Math.max(0.0D, Math.min(RANGE, onBox.subtract(eye).dot(look)));
            final Vec3 onRay = eye.add(look.scale(along));
            onBox = new Vec3(
                    Mth.clamp(onRay.x, box.minX(), box.maxX()),
                    Mth.clamp(onRay.y, box.minY(), box.maxY()),
                    Mth.clamp(onRay.z, box.minZ(), box.maxZ()));
        }

        return onBox;
    }

    private static void spawnImpactParticles(
            final Level level,
            final Vec3 point,
            final Vec3 towardsMuzzle,
            final float energy
    ) {
        final int count = IMPACT_PARTICLES_PER_TICK + (energy > 0.35F ? IMPACT_PARTICLES_SURGE : 0);
        for (int i = 0; i < count; i++) {
            final Vec3 velocity = towardsMuzzle.scale(IMPACT_SPEED).add(
                    (RANDOM.nextDouble() - 0.5D) * IMPACT_SPREAD,
                    (RANDOM.nextDouble() - 0.5D) * IMPACT_SPREAD,
                    (RANDOM.nextDouble() - 0.5D) * IMPACT_SPREAD);

            level.addParticle(
                    RANDOM.nextInt(4) == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    private static final class Beam {
        private final List<Node> nodes = new ArrayList<>();
        private final LineOutline line = new LineOutline();
        private final UUID owner;

        private double nodeRadius;
        private volatile float energy;

        private float chargeTicks;

        private float arcTicks;
        private float arcAmount;
        private double reach;
        private double previousReach;

        private Vec3 muzzle = Vec3.ZERO;
        private Vec3 endpoint = Vec3.ZERO;

        private boolean growing;

        private volatile UUID lockedTarget;

        private volatile UUID surgeTarget;
        private float surgeAge = -1.0F;
        private float previousSurgeAge = -1.0F;

        private Beam(final UUID owner) {
            this.owner = owner;
            this.line.getParams()
                    .colored(SHRINK_COLOUR)
                    .disableLineNormals()
                    .lineWidth(LINE_WIDTH);
        }

        private boolean canCarry() {
            return this.chargeTicks >= CHARGE_TICKS && this.nodes.size() >= 2;
        }

        private void beginSurge(final UUID subLevelId) {
            this.surgeTarget = subLevelId;
            this.surgeAge = 0.0F;
            this.previousSurgeAge = 0.0F;
            this.energy = 1.0F;
        }

        private void tickSurge() {
            if (this.surgeAge < 0.0F) return;

            this.previousSurgeAge = this.surgeAge;
            this.surgeAge += 1.0F;

            if (this.surgeAge < SURGE_TRAVEL_TICKS) return;

            final UUID target = this.surgeTarget;
            this.surgeTarget = null;
            this.surgeAge = -1.0F;
            this.previousSurgeAge = -1.0F;
            if (target != null) CompressionFieldRenderer.pulse(target, this.owner);
        }

        private float surgePosition(final float partialTick) {
            if (this.surgeAge < 0.0F) return -1.0F;
            final float age = Mth.lerp(partialTick, this.previousSurgeAge, this.surgeAge);
            return age / SURGE_TRAVEL_TICKS;
        }

        private void setGrowing(final boolean value) {
            if (this.growing == value) return;
            this.growing = value;
            this.line.getParams().colored(value ? GROW_COLOUR : SHRINK_COLOUR);
        }

        private void tick(final Player owner, final Level level) {
            this.energy *= SURGE_DECAY;
            this.chargeTicks = Math.min(CHARGE_TICKS, this.chargeTicks + 1.0F);
            tickSurge();

            this.muzzle = muzzleOf(owner);

            final Landing landing = endpointOf(owner, level, this.lockedTarget);
            this.endpoint = landing.point();

            this.previousReach = this.reach;
            if (this.chargeTicks < CHARGE_TICKS) {
                this.reach = 0.0D;
                this.nodes.clear();
                return;
            }

            final double wanted = this.muzzle.distanceTo(this.endpoint);
            final boolean travelling = this.reach < wanted;
            if (travelling) this.reach = Math.min(wanted, this.reach + TRAVEL_SPEED);
            else this.reach = wanted;

            this.arcTicks += 1.0F;
            this.arcAmount = (float) Math.exp(-this.arcTicks / ARC_SETTLE_TICKS);

            updateNodes();

            if (!travelling && landing.struck() && !this.nodes.isEmpty()) {
                final Vec3 back = this.muzzle.subtract(this.endpoint);
                if (back.lengthSqr() > 1.0E-6D) {
                    spawnImpactParticles(level, this.endpoint, back.normalize(), this.energy);
                }
            }
        }

        private void updateNodes() {
            final Vec3 direction = this.endpoint.subtract(this.muzzle);
            if (direction.lengthSqr() < 1.0E-9D) return;

            final Vec3 tip = this.muzzle.add(direction.normalize().scale(this.reach));

            this.nodeRadius = (NODE_RADIUS_BASE + SURGE_WOBBLE * this.energy) * Math.sqrt(
                    Mth.clamp(this.reach / WOBBLE_REFERENCE_LENGTH,
                            1.0D, MAX_WOBBLE_FACTOR * MAX_WOBBLE_FACTOR));

            final int wanted = Mth.clamp(
                    (int) Math.round(this.reach / SEGMENT_SPACING) + 1, MIN_NODES, MAX_NODES);

            while (this.nodes.size() < wanted) {
                final double t = this.nodes.size() / (double) Math.max(1, wanted - 1);
                this.nodes.add(new Node(this.muzzle.lerp(tip, t)));
            }
            while (this.nodes.size() > wanted) this.nodes.remove(this.nodes.size() - 1);

            final int last = this.nodes.size() - 1;
            for (int i = 0; i <= last; i++) {
                final Node node = this.nodes.get(i);
                node.previousPosition = node.position;

                if (i == 0) {
                    node.position = this.muzzle;
                } else if (i == last) {
                    node.position = tip;
                } else {
                    final double t = i / (double) last;
                    final double taper = Math.sin(t * Math.PI);

                    final double arc = LAUNCH_ARC * this.arcAmount * taper;

                    final Vec3 ideal = this.muzzle.lerp(tip, t).add(
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper,
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper - arc,
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper);
                    final float follow = Mth.lerp(
                            (float) taper, NODE_FOLLOW_ENDS, NODE_FOLLOW_MIDDLE);
                    node.position = node.position.lerp(ideal, follow);
                }
            }
        }

        private boolean render(
                final PoseStack poses,
                final SuperRenderTypeBuffer buffer,
                final Vec3 camera,
                final float partialTick,
                final Vec3 frameMuzzle
        ) {
            if (this.nodes.size() < 2) return false;

            this.nodes.get(0).pinTo(frameMuzzle);
            if (Mth.lerp(partialTick, (float) this.previousReach, (float) this.reach) <= 0.01F) return false;

            final int segments = this.nodes.size() - 1;
            final float surge = surgePosition(partialTick);
            final int base = this.growing ? GROW_COLOUR : SHRINK_COLOUR;

            for (int i = 0; i < segments; i++) {
                final float t = i / (float) Math.max(1, segments - 1);

                float swell = 1.0F;
                float heat = 0.0F;
                if (surge >= 0.0F) {
                    final float nearness = 1.0F - Mth.clamp(Math.abs(t - surge) / SURGE_WIDTH, 0.0F, 1.0F);
                    heat = nearness * nearness;
                    swell = 1.0F + SURGE_SWELL * heat;
                }

                final float taper = Mth.lerp(t, WIDTH_AT_MUZZLE, WIDTH_AT_TIP);
                this.line.getParams()
                        .lineWidth(LINE_WIDTH * taper * swell)
                        .colored(heat <= 0.0F ? base : brighten(base, heat));

                final Vec3 from = this.nodes.get(i).interpolated(partialTick);
                final Vec3 to = this.nodes.get(i + 1).interpolated(partialTick);
                this.line.set(from, to).render(poses, buffer, camera, partialTick);
            }
            return true;
        }
    }

    private static int brighten(final int colour, final float amount) {
        final float t = Mth.clamp(amount, 0.0F, 1.0F);
        final int r = Math.round(Mth.lerp(t, (colour >> 16) & 0xFF, 255.0F));
        final int g = Math.round(Mth.lerp(t, (colour >> 8) & 0xFF, 255.0F));
        final int b = Math.round(Mth.lerp(t, colour & 0xFF, 255.0F));
        return (r << 16) | (g << 8) | b;
    }

    private static final class Node {
        private Vec3 position;
        private Vec3 previousPosition;

        private Node(final Vec3 position) {
            this.position = position;
            this.previousPosition = position;
        }

        private Vec3 interpolated(final float partialTick) {
            return this.previousPosition.lerp(this.position, partialTick);
        }

        private void pinTo(final Vec3 point) {
            this.position = point;
            this.previousPosition = point;
        }
    }
}
