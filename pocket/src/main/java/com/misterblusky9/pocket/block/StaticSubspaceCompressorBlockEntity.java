package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pocket.compression.BeamSession;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.compression.PersonalLock;
import com.misterblusky9.pocket.compression.PersonalScale;
import com.misterblusky9.pocket.network.EntityCompressionGlowPayload;
import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleCommandSource;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.lasers.LaserBehaviour;
import net.minecraft.core.BlockPos;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaticSubspaceCompressorBlockEntity extends KineticBlockEntity
        implements ScaleCommandSource {
    public static final int MAX_SIGNAL = 15;

    private static final int CHARGE_TICKS = 30;
    private static final int ACQUIRE_SLOWDOWN = 3;
    private static final double TRANSITION_SPEED_FACTOR = 0.35D;
    private static final int AIM_GRACE_TICKS = 10;
    private static final double BEAM_RADIUS = 0.5D;
    private static final int GLOW_REFRESH_TICKS = 5;
    private static final int GLOW_TIMEOUT_TICKS = 15;

    private static final Map<UUID, Set<StaticSubspaceCompressorBlockEntity>> DRIVERS =
            new ConcurrentHashMap<>();

    private LaserBehaviour laser;
    private ScrollOptionBehaviour<CompressorMode> mode;

    private int chargeAge;
    private int acquisitionAge;
    private int acquisitionTicks;
    private int stepAge;
    private int completedSteps;

    private boolean fieldActive;
    private boolean sealed;
    private boolean pulseSent;
    private boolean powered;
    private int signal;
    private int aimMissTicks;

    private UUID targetId;
    private ServerSubLevel target;
    private LivingEntity targetEntity;
    private int entityResizeAge;
    private boolean entityGrowing;
    private BlockPos hitLocalPos;
    private double commandedScale = NO_COMMAND;
    private double inFlightScale = NO_COMMAND;

    public StaticSubspaceCompressorBlockEntity(
            final BlockEntityType<?> type,
            final BlockPos pos,
            final BlockState state
    ) {
        super(type, pos, state);
    }

    public StaticSubspaceCompressorBlockEntity(final BlockPos pos, final BlockState state) {
        this(ModBlockEntities.STATIC_SUBSPACE_COMPRESSOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        this.laser = new LaserBehaviour(this, this::gatherStartAndEnd, this::getRaycastLength);
        this.laser.setBlockCollide(ClipContext.Block.COLLIDER);
        this.laser.setFluidCollide(ClipContext.Fluid.NONE);
        this.laser.setShouldCast(this::shouldCast);
        behaviours.add(this.laser);

        this.mode = new ScrollOptionBehaviour<>(
                CompressorMode.class,
                Component.translatable("pocket.static_subspace_compressor.mode"),
                this,
                new CenteredSideValueBoxTransform((state, side) -> {
                    final Direction facing = state.getValue(StaticSubspaceCompressorBlock.FACING);
                    return side != facing && side != facing.getOpposite();
                })
        );
        this.mode.withCallback(value -> {
            if (this.level != null && !this.level.isClientSide) reset(true);
        });
        behaviours.add(this.mode);
    }

    public CompressorMode mode() {
        return this.mode == null ? CompressorMode.SHRINK : this.mode.get();
    }

    private CompressionStage targetStage() {
        return mode().target();
    }

    public Direction getDirection() {
        final BlockState state = getBlockState();
        return state.getBlock() instanceof StaticSubspaceCompressorBlock
                ? state.getValue(StaticSubspaceCompressorBlock.FACING)
                : Direction.NORTH;
    }

    public Vec3i getNormal() {
        return getDirection().getNormal();
    }

    public Couple<Vec3> gatherStartAndEnd() {
        final Vec3i normal = getNormal();
        final Vec3 start = Vec3.atCenterOf(this.worldPosition)
                .add(Vec3.atLowerCornerOf(normal).scale(0.5F));
        final Vec3 end = start.add(
                Vec3.atLowerCornerOf(normal).scale(getRaycastLength())
        );
        return Couple.create(start, end);
    }

    @Override
    public AABB getRenderBoundingBox() {
        final int range = (int) getRaycastLength();
        final Vec3i normal = getNormal();
        return new AABB(getBlockPos())
                .expandTowards(Vec3.atLowerCornerOf(normal.multiply(range)));
    }

    public float getRaycastLength() {
        return this.signal;
    }

    private int redstoneSignal() {
        if (this.level == null) return 0;
        return Math.max(0, Math.min(MAX_SIGNAL, this.level.getBestNeighborSignal(this.worldPosition)));
    }

    public boolean shouldCast() {
        return this.powered;
    }

    @Override
    public void tick() {
        if (this.level == null) return;

        this.signal = redstoneSignal();
        this.powered = this.signal > 0
                && isSpeedRequirementFulfilled()
                && Math.abs(getSpeed()) > 0.0F;

        super.tick();
        if (!(this.level instanceof final ServerLevel level)) return;

        if (!this.powered) {
            reset(true);
            return;
        }

        if (Pym.resize().driversSuspended(this.targetId, level.getGameTime())) {
            reset(false);
            return;
        }

        if (this.chargeAge < CHARGE_TICKS) {
            this.chargeAge++;
            return;
        }

        if (!hasTarget()) {
            if (!acquireOrContinue(level)) return;
        } else if (stillAimingAtTarget(level)) {
            this.aimMissTicks = 0;
        } else if (++this.aimMissTicks > AIM_GRACE_TICKS) {
            clearTarget(true);
            return;
        }

        if (!hasTarget()) {
            clearTarget(true);
            return;
        }

        if (this.targetEntity != null) {
            driveEntity(level);
            return;
        }

        if (!this.sealed) {
            this.acquisitionAge++;
            if (this.acquisitionAge < this.acquisitionTicks) return;

            this.sealed = true;
            this.stepAge = Integer.MAX_VALUE / 2;
            this.completedSteps = 0;
            this.pulseSent = false;
            this.inFlightScale = NO_COMMAND;
            this.commandedScale = Pym.scale().settled(this.target);
        }

        driveShrink(level);
    }

    private boolean hasTarget() {
        if (this.targetEntity != null) return !this.targetEntity.isRemoved() && this.targetEntity.isAlive();
        return this.target != null && !this.target.isRemoved();
    }

    private boolean acquireOrContinue(final ServerLevel level) {
        final LivingEntity entity = aimedEntity();
        if (entity != null) return acquireEntity(entity);

        final Target hit = findTarget(level);
        if (hit == null) {
            clearTarget(true);
            return false;
        }

        if (this.targetId != null && this.targetId.equals(hit.subLevel().getUniqueId())) {
            this.target = hit.subLevel();
            return true;
        }

        clearTarget(true);

        final long now = level.getGameTime();
        if (Pym.resize().driversSuspended(hit.subLevel().getUniqueId(), now)) return false;

        final double current = Pym.scale().settled(hit.subLevel());
        if (Pym.scale().isAt(hit.subLevel(), targetStage().scale())) return false;

        final PlotContents contents = Pym.resize().contents(hit.subLevel());
        if (contents.hasNoShrinkBlock() || contents.blocks() > Pym.resize().shrunkBlockLimit()) return false;

        this.target = hit.subLevel();
        this.targetId = hit.subLevel().getUniqueId();
        this.hitLocalPos = hit.hitLocalPos().immutable();
        this.acquisitionAge = 0;
        this.acquisitionTicks =
                CompressionSessions.estimateAcquireTicks(hit.subLevel()) * ACQUIRE_SLOWDOWN;
        this.fieldActive = true;
        this.aimMissTicks = 0;
        DRIVERS.computeIfAbsent(this.targetId, id -> ConcurrentHashMap.newKeySet()).add(this);

        CompressionSyncPayload.sendMachineBegin(
                hit.subLevel(),
                this.hitLocalPos,
                this.acquisitionTicks,
                targetStage().scale() > current
        );
        return true;
    }

    private boolean acquireEntity(final LivingEntity entity) {
        clearTarget(true);
        if (protects(entity)) return false;

        final double current = EntityCompressionTargeting.scale(entity);
        final double goal = goalFor(entity, current);
        if (ScaleBounds.same(current, goal)) return false;

        this.targetEntity = entity;
        this.targetId = entity.getUUID();
        this.fieldActive = true;
        this.aimMissTicks = 0;
        this.sealed = true;
        this.stepAge = Integer.MAX_VALUE / 2;
        this.completedSteps = 0;
        this.inFlightScale = NO_COMMAND;
        this.commandedScale = current;
        this.entityGrowing = goal > current;
        DRIVERS.computeIfAbsent(this.targetId, id -> ConcurrentHashMap.newKeySet()).add(this);

        EntityCompressionGlowPayload.send(entity, this.entityGrowing, GLOW_TIMEOUT_TICKS);
        return true;
    }

    private LivingEntity aimedEntity() {
        if (this.laser == null || !Pym.entities().available()) return null;
        if (!(this.laser.getClosestHitResult() instanceof final EntityHitResult hit)) return null;
        return hit.getEntity() instanceof final LivingEntity living
                && !(living instanceof ArmorStand)
                && living.isAlive()
                && !living.isSpectator()
                ? living
                : null;
    }

    private boolean stillAimingAtTarget(final ServerLevel level) {
        if (this.targetId == null) return false;

        final LivingEntity entity = aimedEntity();
        if (entity != null) return this.targetId.equals(entity.getUUID());
        if (this.targetEntity != null) return false;

        final Target hit = findTarget(level);
        return hit != null
                && this.targetId != null
                && this.targetId.equals(hit.subLevel().getUniqueId());
    }

    private void driveShrink(final ServerLevel level) {
        if (this.target == null || this.target.isRemoved() || this.targetId == null) {
            clearTarget(true);
            return;
        }

        if (!Pym.scale().isSettled(this.targetId)) {
            if (ScaleBounds.isValid(this.inFlightScale)) this.commandedScale = this.inFlightScale;
            Pym.resize().drive(this.target, this);
            return;
        }

        final double current = Pym.scale().settled(this.target);

        if (ScaleBounds.isValid(this.inFlightScale)) {
            if (ScaleBounds.same(current, this.inFlightScale)) {
                this.completedSteps++;
                this.stepAge = 0;
            }
            this.inFlightScale = NO_COMMAND;
            this.pulseSent = false;
        }

        this.commandedScale = current;

        if (ScaleBounds.same(current, targetStage().scale())) {
            clearTarget(true);
            return;
        }

        if (isOpposed(current)) {
            this.stepAge = 0;
            this.pulseSent = false;
            Pym.resize().drive(this.target, this);
            return;
        }

        this.stepAge++;
        final double nextScale = ScaleLadder.stepToward(current, targetStage().scale());
        final boolean finalStep = ScaleBounds.same(nextScale, targetStage().scale());
        final int delay = BeamSession.Pace.STATIC_COMPRESSOR.delay(this.completedSteps, finalStep);

        if (!this.pulseSent && this.stepAge >= Math.max(0, delay - BeamSession.PULSE_LEAD_TICKS)) {
            CompressionSyncPayload.sendPulse(this.target, null);
            this.pulseSent = true;
        }

        if (this.stepAge >= delay) {
            this.inFlightScale = nextScale;
            this.commandedScale = nextScale;
            this.pulseSent = false;
        }

        Pym.resize().drive(this.target, this);
    }

    private void driveEntity(final ServerLevel level) {
        final LivingEntity entity = this.targetEntity;
        if (protects(entity)) {
            clearTarget(true);
            return;
        }

        if (level.getGameTime() % GLOW_REFRESH_TICKS == 0) {
            EntityCompressionGlowPayload.send(entity, this.entityGrowing, GLOW_TIMEOUT_TICKS);
        }

        if (ScaleBounds.isValid(this.inFlightScale)) {
            if (++this.entityResizeAge < entityResizeTicks()) return;
            this.commandedScale = this.inFlightScale;
            this.inFlightScale = NO_COMMAND;
            this.completedSteps++;
            this.stepAge = 0;
        }

        final double current = this.commandedScale;
        final double goal = goalFor(entity, current);
        if (ScaleBounds.same(current, goal)) {
            clearTarget(true);
            return;
        }

        if (isOpposed(current)) {
            this.stepAge = 0;
            return;
        }

        this.stepAge++;
        final double nextScale = ScaleLadder.stepToward(current, goal);
        final int delay = BeamSession.Pace.STATIC_COMPRESSOR.delay(
                this.completedSteps, ScaleBounds.same(nextScale, goal));
        if (this.stepAge < delay) return;

        if (!Pym.entities().setScale(
                entity, nextScale / EntityCompressionTargeting.containerScale(entity), entityResizeTicks())) {
            clearTarget(true);
            return;
        }
        this.inFlightScale = nextScale;
        this.entityResizeAge = 0;
        if (entity instanceof final ServerPlayer player) PersonalScale.remember(player, nextScale);
    }

    private double goalFor(final LivingEntity entity, final double current) {
        return PersonalScale.goalFor(entity, current, targetStage().scale());
    }

    private boolean protects(final LivingEntity entity) {
        return entity instanceof final Player player && PersonalLock.protects(player);
    }

    private int entityResizeTicks() {
        return Math.max(1, (int) Math.round(transitionTicks()));
    }

    private boolean isOpposed(final double current) {
        if (this.targetId == null) return false;

        final Set<StaticSubspaceCompressorBlockEntity> drivers = DRIVERS.get(this.targetId);
        if (drivers == null) return false;

        final int direction = Double.compare(targetStage().scale(), current);
        if (direction == 0) return false;

        for (final StaticSubspaceCompressorBlockEntity other : drivers) {
            if (other == this || other.isRemoved() || !other.sealed) continue;
            if (Double.compare(other.targetStage().scale(), current) == -direction) {
                return true;
            }
        }
        return false;
    }

    private Target findTarget(final ServerLevel level) {
        if (this.laser == null) return null;

        final SubLevel host = Sable.HELPER.getContaining(level, this.worldPosition);
        final UUID hostId = host == null ? null : host.getUniqueId();

        final BlockHitResult blockHit = this.laser.getBlockHitResult();
        final BlockPos blockHitPos = blockHit == null || blockHit.getType() == HitResult.Type.MISS
                ? null
                : blockHit.getBlockPos();

        if (blockHitPos != null) {
            final SubLevel owner = Sable.HELPER.getContaining(level, blockHitPos);
            if (owner instanceof final ServerSubLevel hitSubLevel
                    && !hitSubLevel.isRemoved()
                    && hitSubLevel.getUniqueId() != null
                    && !hitSubLevel.getUniqueId().equals(hostId)) {
                return new Target(hitSubLevel, blockHitPos);
            }
        }

        return sweepBeamCorridor(level, hostId);
    }

    private Target sweepBeamCorridor(final ServerLevel level, final UUID hostId) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        final Couple<Vec3> beam = gatherStartAndEnd();
        final Vec3 start = beam.getFirst();
        final double reach = beamReach(level, start);
        if (reach <= 0.0D) return null;

        final Vec3 end = start.add(Vec3.atLowerCornerOf(getNormal()).scale(reach));

        ServerSubLevel nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (final SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) continue;

            final UUID id = subLevel.getUniqueId();
            if (id == null || id.equals(hostId)) continue;

            final double distance = beamEntryDistance(subLevel, start, end);
            if (distance < 0.0D || distance >= nearestDistance) continue;

            nearest = subLevel;
            nearestDistance = distance;
        }

        return nearest == null ? null : new Target(nearest, centreOf(nearest));
    }

    private double beamReach(final ServerLevel level, final Vec3 start) {
        final HitResult hit = this.laser.getClosestHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS) return getRaycastLength();

        final double distance = Math.sqrt(
                Sable.HELPER.distanceSquaredWithSubLevels(level, start, hit.getLocation()));
        if (!Double.isFinite(distance) || distance <= 0.0D) return getRaycastLength();

        return Math.min(getRaycastLength(), distance);
    }

    private static double beamEntryDistance(
            final ServerSubLevel subLevel,
            final Vec3 start,
            final Vec3 end
    ) {
        final var bounds = subLevel.boundingBox();
        if (bounds == null) return -1.0D;

        final AABB box = new AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()
        ).inflate(BEAM_RADIUS);

        if (box.contains(start)) return 0.0D;

        return box.clip(start, end)
                .map(start::distanceTo)
                .orElse(-1.0D);
    }

    private static BlockPos centreOf(final ServerSubLevel subLevel) {
        final var plot = subLevel.getPlot().getBoundingBox();
        return new BlockPos(
                (plot.minX() + plot.maxX()) / 2,
                (plot.minY() + plot.maxY()) / 2,
                (plot.minZ() + plot.maxZ()) / 2
        );
    }

    private void reset(final boolean releaseVisuals) {
        this.chargeAge = 0;
        clearTarget(releaseVisuals);
    }

    private void clearTarget(final boolean releaseVisuals) {
        if (this.targetId != null) {
            if (this.targetEntity == null) Pym.resize().release(this.targetId);
            DRIVERS.computeIfPresent(this.targetId, (id, drivers) -> {
                drivers.remove(this);
                return drivers.isEmpty() ? null : drivers;
            });
        }

        if (releaseVisuals && this.fieldActive) {
            if (this.targetEntity != null) {
                if (this.level instanceof final ServerLevel level) EntityCompressionGlowPayload.clear(level, this.targetId);
            } else if (this.target != null && !this.target.isRemoved()) {
                CompressionSyncPayload.sendRelease(this.target);
            } else if (this.level instanceof final ServerLevel level && this.targetId != null) {
                CompressionSyncPayload.sendRelease(level, this.targetId);
            }
        }

        this.target = null;
        this.targetEntity = null;
        this.entityResizeAge = 0;
        this.entityGrowing = false;
        this.targetId = null;
        this.hitLocalPos = null;
        this.commandedScale = NO_COMMAND;
        this.inFlightScale = NO_COMMAND;
        this.acquisitionAge = 0;
        this.acquisitionTicks = 0;
        this.stepAge = 0;
        this.completedSteps = 0;
        this.fieldActive = false;
        this.aimMissTicks = 0;
        this.sealed = false;
        this.pulseSent = false;
    }

    public boolean isEngaged() {
        return this.sealed && this.target != null && !this.target.isRemoved();
    }

    @Override
    public double commandedScale() {
        return this.sealed ? this.commandedScale : NO_COMMAND;
    }

    @Override
    public ScaleBounds bounds() {
        return DeviceRanges.of(DeviceRanges.Device.COMPRESSORS);
    }

    @Override
    public boolean stepwiseTransitions() {
        return false;
    }

    @Override
    public double transitionTicks() {
        return Pym.resize().defaultTransitionTicks() / TRANSITION_SPEED_FACTOR;
    }

    @Override
    public void remove() {
        reset(true);
        super.remove();
    }

    @Override
    public void destroy() {
        reset(true);
        super.destroy();
    }

    private record Target(ServerSubLevel subLevel, BlockPos hitLocalPos) {}
}
