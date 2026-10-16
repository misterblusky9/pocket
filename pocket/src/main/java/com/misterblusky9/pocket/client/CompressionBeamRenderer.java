package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.mojang.blaze3d.vertex.PoseStack;
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
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.SubLevelShape;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.client.LineEmitter;
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

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class CompressionBeamRenderer {
    private static final int SHRINK_COLOUR = 0x9AF0FF;
    private static final int GROW_COLOUR = 0xFFD24A;
    private static final float LINE_WIDTH = 0.0375F;

    private static final Vec3 BARREL_OFFSET = new Vec3(0.75D, -0.15D, 1.5D);

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

    public static void setFiring(UUID playerId, boolean firing, boolean growing) {
        if (playerId == null) return;
        if (!firing) {
            ACTIVE.remove(playerId);
            CompressionGunMuzzleTracker.clear(playerId);
            return;
        }
        ACTIVE.computeIfAbsent(playerId, Beam::new).setGrowing(growing);
    }

    private static void recoil(UUID playerId) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !playerId.equals(player.getUUID())) return;
        CompressionGunRenderHandler.INSTANCE.shoot(player.getUsedItemHand(), player.position());
    }

    public static void setTarget(UUID playerId, UUID subLevelId) {
        if (playerId == null) return;
        Beam beam = ACTIVE.get(playerId);
        if (beam != null) beam.lockedTarget = subLevelId;
    }

    public static Vec3 landingOn(UUID playerId, UUID subLevelId) {
        if (playerId == null || subLevelId == null) return null;
        Beam beam = ACTIVE.get(playerId);
        if (beam == null || !subLevelId.equals(beam.lockedTarget) || !beam.targetSurface) return null;
        return beam.endpoint;
    }

    public static boolean hasLockedTarget(UUID subLevelId) {
        if (subLevelId == null) return false;
        for (Beam beam : ACTIVE.values()) {
            if (subLevelId.equals(beam.lockedTarget)) return true;
        }
        return false;
    }

    public static boolean isAimingAt(UUID subLevelId) {
        if (subLevelId == null) return false;
        for (Beam beam : ACTIVE.values()) {
            if (subLevelId.equals(beam.lockedTarget) && beam.targetSurface) return true;
        }
        return false;
    }

    public static void clearTarget(UUID subLevelId) {
        if (subLevelId == null) return;
        for (Beam beam : ACTIVE.values()) {
            if (subLevelId.equals(beam.lockedTarget)) beam.lockedTarget = null;
        }
    }

    public static boolean surge(UUID playerId, UUID subLevelId) {
        Beam beam = ACTIVE.get(playerId);
        if (beam == null || !beam.canCarry()) return false;

        beam.beginSurge(subLevelId);
        return true;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    public static void clear() {
        ACTIVE.clear();
        CompressionGunMuzzleTracker.clear();
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }

        for (Map.Entry<UUID, Beam> entry : ACTIVE.entrySet()) {
            Player owner = level.getPlayerByUUID(entry.getKey());
            if (owner == null) continue;
            entry.getValue().tick(owner, level);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();

        boolean drew = false;
        for (Map.Entry<UUID, Beam> entry : ACTIVE.entrySet()) {
            Player owner = level.getPlayerByUUID(entry.getKey());
            if (owner == null) continue;
            if (entry.getValue().render(poses, buffer, camera, partialTick, muzzleOf(owner, partialTick), owner)) {
                drew = true;
            }
        }

        if (drew) buffer.draw();
    }

    private static Vec3 muzzleOf(Player player) {
        return muzzleOf(player, 1.0F);
    }

    private static Vec3 muzzleOf(Player player, float partialTick) {
        Vec3 tracked = CompressionGunMuzzleTracker.muzzle(player.getUUID(), partialTick);
        if (tracked != null) return tracked;

        boolean mainHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND;

        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double y = Mth.lerp(partialTick, player.yo, player.getY());
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        Vec3 start = new Vec3(x, y + player.getEyeHeight(), z);

        float yaw = (float) ((Mth.lerp(partialTick, player.yRotO, player.getYRot())) / -180.0F * Math.PI);
        float pitch = (float) ((Mth.lerp(partialTick, player.xRotO, player.getXRot())) / -180.0F * Math.PI);

        int flip = mainHand == (player.getMainArm() == HumanoidArm.RIGHT) ? -1 : 1;
        double scale = Pym.entities().renderScale(player, partialTick);
        Vec3 local = new Vec3(flip * BARREL_OFFSET.x, BARREL_OFFSET.y, BARREL_OFFSET.z)
                .scale(ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL);
        return start.add(local.xRot(pitch).yRot(yaw));
    }

    private record Landing(Vec3 point, boolean struck, boolean targetSurface) {}

    private static Landing endpointOf(Player player, Level level, UUID lockedTarget) {
        EntityCompressionTargeting.Target entityTarget =
                EntityCompressionTargeting.find(player, RANGE);
        if (entityTarget != null) {
            return new Landing(entityTarget.hitPos(), true, false);
        }

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        SubLevel struck = hit == null || hit.getType() == HitResult.Type.MISS
                ? null : Sable.HELPER.getContaining(level, hit.getBlockPos());

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            double scale = Pym.entities().scaleOf(player);
            return new Landing(
                    eye.add(player.getViewVector(1.0F).scale(RANGE * MISS_REACH_FRACTION
                            * (ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL))), false, false);
        }

        SubLevel found = struck;
        if (!(found instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) {
            return new Landing(
                    hit.getLocation(),
                    true,
                    lockedTarget != null && lockedTarget.equals(idOf(found)));
        }

        Pose3dc pose = subLevel.renderPose();
        Vector3dc position = pose.position();
        Vector3dc rotationPoint = pose.rotationPoint();
        Vector3dc scale = pose.scale();

        org.joml.Vector3d local = new org.joml.Vector3d(
                hit.getLocation().x - rotationPoint.x(),
                hit.getLocation().y - rotationPoint.y(),
                hit.getLocation().z - rotationPoint.z()
        ).mul(scale.x(), scale.y(), scale.z());

        pose.orientation().transform(local);
        return new Landing(
                new Vec3(local.x + position.x(), local.y + position.y(), local.z + position.z()),
                true,
                lockedTarget != null && lockedTarget.equals(idOf(found)));
    }

    private static UUID idOf(SubLevel subLevel) {
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    private static Vec3 silhouettePoint(
            Level level,
            UUID subLevelId,
            Vec3 eye,
            Vec3 look
    ) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return null;

        SubLevel found = container.getSubLevel(subLevelId);
        if (!(found instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) return null;

        SubLevelShape shape = Pym.shape(subLevel);
        Pose3dc pose = subLevel.renderPose();

        if (shape != null && pose != null) {
            Vector3d localEye = pose.transformPositionInverse(
                    new Vector3d(eye.x, eye.y, eye.z));
            Vector3d localTip = pose.transformPositionInverse(
                    new Vector3d(eye.x + look.x * RANGE, eye.y + look.y * RANGE, eye.z + look.z * RANGE));

            Vector3d localDirection = new Vector3d(localTip).sub(localEye);
            double localLength = localDirection.length();
            if (localLength > 1.0E-6D) {
                localDirection.div(localLength);

                Vector3d local = shape.closestPointToRay(localEye, localDirection, localLength);
                if (local != null) {
                    Vector3d world = pose.transformPosition(new Vector3d(local));
                    return new Vec3(world.x, world.y, world.z);
                }
            }
        }

        var box = subLevel.boundingBox();
        if (box == null) return null;

        Vec3 onBox = new Vec3(
                (box.minX() + box.maxX()) * 0.5D,
                (box.minY() + box.maxY()) * 0.5D,
                (box.minZ() + box.maxZ()) * 0.5D);

        for (int i = 0; i < 3; i++) {
            double along = Math.max(0.0D, Math.min(RANGE, onBox.subtract(eye).dot(look)));
            Vec3 onRay = eye.add(look.scale(along));
            onBox = new Vec3(
                    Mth.clamp(onRay.x, box.minX(), box.maxX()),
                    Mth.clamp(onRay.y, box.minY(), box.maxY()),
                    Mth.clamp(onRay.z, box.minZ(), box.maxZ()));
        }

        return onBox;
    }

    private static void spawnImpactParticles(
            Level level,
            Vec3 point,
            Vec3 towardsMuzzle,
            float energy
    ) {
        int count = IMPACT_PARTICLES_PER_TICK + (energy > 0.35F ? IMPACT_PARTICLES_SURGE : 0);
        for (int i = 0; i < count; i++) {
            Vec3 velocity = towardsMuzzle.scale(IMPACT_SPEED).add(
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
        private double ownerScale = ScaleBounds.FULL;
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
        private volatile boolean targetSurface;
        private UUID establishedSurfaceTarget;

        private volatile UUID surgeTarget;
        private float surgeAge = -1.0F;
        private float previousSurgeAge = -1.0F;

        private Beam(UUID owner) {
            this.owner = owner;
            this.line.getParams()
                    .colored(SHRINK_COLOUR)
                    .disableLineNormals()
                    .lineWidth(LINE_WIDTH);
        }

        private boolean canCarry() {
            return this.chargeTicks >= CHARGE_TICKS && this.nodes.size() >= 2;
        }

        private void beginSurge(UUID subLevelId) {
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

            UUID target = this.surgeTarget;
            this.surgeTarget = null;
            this.surgeAge = -1.0F;
            this.previousSurgeAge = -1.0F;
            if (target != null) CompressionFieldRenderer.pulse(target, this.owner);
        }

        private float surgePosition(float partialTick) {
            if (this.surgeAge < 0.0F) return -1.0F;
            float age = Mth.lerp(partialTick, this.previousSurgeAge, this.surgeAge);
            return age / SURGE_TRAVEL_TICKS;
        }

        private void setGrowing(boolean value) {
            if (this.growing == value) return;
            this.growing = value;
            this.line.getParams().colored(value ? GROW_COLOUR : SHRINK_COLOUR);
        }

        private void tick(Player owner, Level level) {
            this.energy *= SURGE_DECAY;
            boolean charging = this.chargeTicks < CHARGE_TICKS;
            this.chargeTicks = Math.min(CHARGE_TICKS, this.chargeTicks + 1.0F);
            if (charging && this.chargeTicks >= CHARGE_TICKS) recoil(owner.getUUID());
            tickSurge();

            this.muzzle = muzzleOf(owner);
            double scale = Pym.entities().scaleOf(owner);
            this.ownerScale = ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;

            Landing landing = endpointOf(owner, level, this.lockedTarget);
            this.endpoint = landing.point();
            this.targetSurface = this.lockedTarget != null && landing.targetSurface();

            this.previousReach = this.reach;
            if (this.chargeTicks < CHARGE_TICKS) {
                this.reach = 0.0D;
                this.nodes.clear();
                return;
            }

            double wanted = this.muzzle.distanceTo(this.endpoint);
            boolean targetSurface = this.lockedTarget != null && landing.targetSurface();
            boolean established = targetSurface && this.lockedTarget.equals(this.establishedSurfaceTarget);

            if (established || this.reach >= wanted) this.reach = wanted;
            else this.reach = Math.min(wanted, this.reach + TRAVEL_SPEED * this.ownerScale);

            boolean reached = this.reach + 1.0E-6D >= wanted;
            if (targetSurface && reached) this.establishedSurfaceTarget = this.lockedTarget;
            else if (!targetSurface && this.lockedTarget == null) this.establishedSurfaceTarget = null;

            this.arcTicks += 1.0F;
            this.arcAmount = (float) Math.exp(-this.arcTicks / ARC_SETTLE_TICKS);

            updateNodes();

            if (reached && landing.struck() && !this.nodes.isEmpty()) {
                if (targetSurface) {
                    CompressionFieldRenderer.nucleate(this.lockedTarget, this.endpoint);
                }
                Vec3 back = this.muzzle.subtract(this.endpoint);
                if (back.lengthSqr() > 1.0E-6D) {
                    spawnImpactParticles(level, this.endpoint, back.normalize(), this.energy);
                }
            }
        }

        private void updateNodes() {
            Vec3 direction = this.endpoint.subtract(this.muzzle);
            if (direction.lengthSqr() < 1.0E-9D) return;

            Vec3 tip = this.muzzle.add(direction.normalize().scale(this.reach));

            this.nodeRadius = (NODE_RADIUS_BASE + SURGE_WOBBLE * this.energy) * this.ownerScale * Math.sqrt(
                    Mth.clamp(this.reach / (WOBBLE_REFERENCE_LENGTH * this.ownerScale),
                            1.0D, MAX_WOBBLE_FACTOR * MAX_WOBBLE_FACTOR));

            int wanted = Mth.clamp(
                    (int) Math.round(this.reach / (SEGMENT_SPACING * this.ownerScale)) + 1, MIN_NODES, MAX_NODES);

            while (this.nodes.size() < wanted) {
                double t = this.nodes.size() / (double) Math.max(1, wanted - 1);
                this.nodes.add(new Node(this.muzzle.lerp(tip, t)));
            }
            while (this.nodes.size() > wanted) this.nodes.remove(this.nodes.size() - 1);

            int last = this.nodes.size() - 1;
            for (int i = 0; i <= last; i++) {
                Node node = this.nodes.get(i);
                node.previousPosition = node.position;

                if (i == 0) {
                    node.position = this.muzzle;
                } else if (i == last) {
                    node.position = tip;
                } else {
                    double t = i / (double) last;
                    double taper = Math.sin(t * Math.PI);

                    double arc = LAUNCH_ARC * this.ownerScale * this.arcAmount * taper;

                    Vec3 ideal = this.muzzle.lerp(tip, t).add(
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper,
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper - arc,
                            (RANDOM.nextDouble() - 0.5D) * this.nodeRadius * taper);
                    float follow = Mth.lerp(
                            (float) taper, NODE_FOLLOW_ENDS, NODE_FOLLOW_MIDDLE);
                    node.position = node.position.lerp(ideal, follow);
                }
            }
        }

        private boolean render(
                PoseStack poses,
                SuperRenderTypeBuffer buffer,
                Vec3 camera,
                float partialTick,
                Vec3 frameMuzzle,
                Player owner
        ) {
            if (this.nodes.size() < 2) return false;
            LineEmitter.firedBy(this.line.getParams(), owner);

            this.nodes.get(0).pinTo(frameMuzzle);
            if (Mth.lerp(partialTick, (float) this.previousReach, (float) this.reach) <= 0.01F) return false;

            int segments = this.nodes.size() - 1;
            float surge = surgePosition(partialTick);
            int base = this.growing ? GROW_COLOUR : SHRINK_COLOUR;

            for (int i = 0; i < segments; i++) {
                float t = i / (float) Math.max(1, segments - 1);

                float swell = 1.0F;
                float heat = 0.0F;
                if (surge >= 0.0F) {
                    float nearness = 1.0F - Mth.clamp(Math.abs(t - surge) / SURGE_WIDTH, 0.0F, 1.0F);
                    heat = nearness * nearness;
                    swell = 1.0F + SURGE_SWELL * heat;
                }

                float taper = Mth.lerp(t, WIDTH_AT_MUZZLE, WIDTH_AT_TIP);
                this.line.getParams()
                        .lineWidth(LINE_WIDTH * taper * swell)
                        .colored(heat <= 0.0F ? base : brighten(base, heat));

                Vec3 from = this.nodes.get(i).interpolated(partialTick);
                Vec3 to = this.nodes.get(i + 1).interpolated(partialTick);
                this.line.set(from, to).render(poses, buffer, camera, partialTick);
            }
            return true;
        }
    }

    private static int brighten(int colour, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(t, (colour >> 16) & 0xFF, 255.0F));
        int g = Math.round(Mth.lerp(t, (colour >> 8) & 0xFF, 255.0F));
        int b = Math.round(Mth.lerp(t, colour & 0xFF, 255.0F));
        return (r << 16) | (g << 8) | b;
    }

    private static final class Node {
        private Vec3 position;
        private Vec3 previousPosition;

        private Node(Vec3 position) {
            this.position = position;
            this.previousPosition = position;
        }

        private Vec3 interpolated(float partialTick) {
            return this.previousPosition.lerp(this.position, partialTick);
        }

        private void pinTo(Vec3 point) {
            this.position = point;
            this.previousPosition = point;
        }
    }
}
