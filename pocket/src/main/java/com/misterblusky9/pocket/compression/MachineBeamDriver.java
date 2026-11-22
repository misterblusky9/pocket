package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.network.EntityCompressionGlowPayload;
import com.misterblusky9.pocket.scale.MachineScaleSource;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.lasers.LaserBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MachineBeamDriver implements MachineScaleSource {
    public interface Host {
        @Nullable Level hostLevel();

        BlockPos hostPos();

        @Nullable LaserBehaviour laser();

        Couple<Vec3> beam();

        float beamLength();

        double goalScale();

        ScaleBounds bounds();

        double transitionTicks();

        BeamSession.Pace pace();

        int acquireSlowdown();

        boolean hostRemoved();

        default @Nullable UUID beamSource() { return null; }

        default boolean targetsContraptions() { return true; }

        default boolean targetsEntities() { return true; }
    }

    private static final int AIM_GRACE_TICKS = 10;
    private static final double BEAM_RADIUS = 0.5D;
    private static final int GLOW_REFRESH_TICKS = 5;
    private static final int GLOW_TIMEOUT_TICKS = 15;

    private static final Map<UUID, Set<MachineBeamDriver>> DRIVERS = new ConcurrentHashMap<>();

    private final Host host;

    private int acquisitionAge;
    private int acquisitionTicks;
    private int stepAge;
    private int completedSteps;

    private boolean fieldActive;
    private boolean sealed;
    private boolean pulseSent;
    private int aimMissTicks;

    private UUID targetId;
    private ServerSubLevel target;
    private LivingEntity targetEntity;
    private int entityResizeAge;
    private boolean entityGrowing;
    private BlockPos hitLocalPos;
    private double commandedScale = NO_COMMAND;
    private double inFlightScale = NO_COMMAND;

    public MachineBeamDriver(Host host) {
        this.host = host;
    }

    public boolean tick(ServerLevel level) {
        if (Pym.resize().driversSuspended(this.targetId, level.getGameTime())) {
            clearTarget(false);
            return false;
        }

        if (!hasTarget()) {
            if (!acquireOrContinue(level)) return true;
        } else if (stillAimingAtTarget(level)) {
            this.aimMissTicks = 0;
        } else if (++this.aimMissTicks > AIM_GRACE_TICKS) {
            clearTarget(true);
            return true;
        }

        if (!hasTarget()) {
            clearTarget(true);
            return true;
        }

        if (this.targetEntity != null) {
            driveEntity(level);
            return true;
        }

        if (!this.sealed) {
            this.acquisitionAge++;
            if (this.acquisitionAge < this.acquisitionTicks) return true;

            this.sealed = true;
            this.stepAge = Integer.MAX_VALUE / 2;
            this.completedSteps = 0;
            this.pulseSent = false;
            this.inFlightScale = NO_COMMAND;
            this.commandedScale = Pym.scale().settled(this.target);
        }

        driveShrink(level);
        return true;
    }

    public boolean hasTarget() {
        if (this.targetEntity != null) return !this.targetEntity.isRemoved() && this.targetEntity.isAlive();
        return this.target != null && !this.target.isRemoved();
    }

    public boolean isEngaged() {
        return this.sealed && this.target != null && !this.target.isRemoved();
    }

    public @Nullable UUID targetId() {
        return this.targetId;
    }

    public boolean isGrowing() {
        if (this.targetEntity != null) return this.entityGrowing;
        if (this.target != null && !this.target.isRemoved()) {
            return this.host.goalScale() > Pym.scale().settled(this.target);
        }
        return this.host.goalScale() > ScaleBounds.FULL;
    }

    private boolean acquireOrContinue(ServerLevel level) {
        LivingEntity entity = this.host.targetsEntities() ? aimedEntity() : null;
        if (entity != null) return acquireEntity(entity);

        Target hit = this.host.targetsContraptions() ? findTarget(level) : null;
        if (hit == null) {
            clearTarget(true);
            return false;
        }

        if (this.targetId != null && this.targetId.equals(hit.subLevel().getUniqueId())) {
            this.target = hit.subLevel();
            return true;
        }

        clearTarget(true);

        long now = level.getGameTime();
        if (Pym.resize().driversSuspended(hit.subLevel().getUniqueId(), now)) return false;

        double goal = this.host.goalScale();
        double current = Pym.scale().settled(hit.subLevel());
        if (Pym.scale().isAt(hit.subLevel(), goal)) return false;

        PlotContents contents = Pym.resize().contents(hit.subLevel());
        if (contents.hasNoShrinkBlock() || contents.blocks() > Pym.resize().shrunkBlockLimit()) return false;

        this.target = hit.subLevel();
        this.targetId = hit.subLevel().getUniqueId();
        this.hitLocalPos = hit.hitLocalPos().immutable();
        this.acquisitionAge = 0;
        this.acquisitionTicks =
                CompressionSessions.estimateAcquireTicks(hit.subLevel()) * Math.max(1, this.host.acquireSlowdown());
        this.fieldActive = true;
        this.aimMissTicks = 0;
        DRIVERS.computeIfAbsent(this.targetId, id -> ConcurrentHashMap.newKeySet()).add(this);

        UUID source = this.host.beamSource();
        if (source == null) {
            CompressionSyncPayload.sendMachineBegin(
                    hit.subLevel(), this.hitLocalPos, this.acquisitionTicks, goal > current);
        } else {
            CompressionSyncPayload.sendSourcedBegin(
                    hit.subLevel(), source, this.hitLocalPos, this.acquisitionTicks, goal > current);
        }
        return true;
    }

    private boolean acquireEntity(LivingEntity entity) {
        clearTarget(true);
        if (protects(entity)) return false;

        double current = EntityCompressionTargeting.scale(entity);
        double goal = goalFor(entity, current);
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

    private @Nullable LivingEntity aimedEntity() {
        LaserBehaviour laser = this.host.laser();
        if (laser == null || !Pym.entities().available()) return null;
        if (!(laser.getClosestHitResult() instanceof EntityHitResult hit)) return null;
        return hit.getEntity() instanceof LivingEntity living
                && !(living instanceof ArmorStand)
                && living.isAlive()
                && !living.isSpectator()
                ? living
                : null;
    }

    private boolean stillAimingAtTarget(ServerLevel level) {
        if (this.targetId == null) return false;

        LivingEntity entity = aimedEntity();
        if (entity != null) return this.targetId.equals(entity.getUUID());
        if (this.targetEntity != null) return false;

        Target hit = findTarget(level);
        return hit != null
                && this.targetId != null
                && this.targetId.equals(hit.subLevel().getUniqueId());
    }

    private void driveShrink(ServerLevel level) {
        if (this.target == null || this.target.isRemoved() || this.targetId == null) {
            clearTarget(true);
            return;
        }

        if (!Pym.scale().isSettled(this.targetId)) {
            if (ScaleBounds.isValid(this.inFlightScale)) this.commandedScale = this.inFlightScale;
            Pym.resize().drive(this.target, this);
            return;
        }

        double goal = this.host.goalScale();
        double current = Pym.scale().settled(this.target);

        if (ScaleBounds.isValid(this.inFlightScale)) {
            if (ScaleBounds.same(current, this.inFlightScale)) {
                this.completedSteps++;
                this.stepAge = 0;
            }
            this.inFlightScale = NO_COMMAND;
            this.pulseSent = false;
        }

        this.commandedScale = current;

        if (ScaleBounds.same(current, goal)) {
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
        double nextScale = ScaleLadder.stepToward(current, goal);
        boolean finalStep = ScaleBounds.same(nextScale, goal);
        int delay = this.host.pace().delay(this.completedSteps, finalStep);

        if (!this.pulseSent && this.stepAge >= Math.max(0, delay - BeamSession.PULSE_LEAD_TICKS)) {
            CompressionSyncPayload.sendPulse(this.target, this.host.beamSource());
            this.pulseSent = true;
        }

        if (this.stepAge >= delay) {
            this.inFlightScale = nextScale;
            this.commandedScale = nextScale;
            this.pulseSent = false;
        }

        Pym.resize().drive(this.target, this);
    }

    private void driveEntity(ServerLevel level) {
        LivingEntity entity = this.targetEntity;
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

        double current = this.commandedScale;
        double goal = goalFor(entity, current);
        if (ScaleBounds.same(current, goal)) {
            clearTarget(true);
            return;
        }

        if (isOpposed(current)) {
            this.stepAge = 0;
            return;
        }

        this.stepAge++;
        double nextScale = ScaleLadder.stepToward(current, goal);
        int delay = this.host.pace().delay(this.completedSteps, ScaleBounds.same(nextScale, goal));
        if (this.stepAge < delay) return;

        if (!Pym.entities().setScale(
                entity, nextScale / EntityCompressionTargeting.containerScale(entity), entityResizeTicks())) {
            clearTarget(true);
            return;
        }
        this.inFlightScale = nextScale;
        this.entityResizeAge = 0;
        if (entity instanceof ServerPlayer player) PersonalScale.remember(player, nextScale);
    }

    private double goalFor(LivingEntity entity, double current) {
        return PersonalScale.goalFor(entity, current, this.host.goalScale());
    }

    private static boolean protects(LivingEntity entity) {
        return entity instanceof Player player && PersonalLock.protects(player);
    }

    private int entityResizeTicks() {
        return Math.max(1, (int) Math.round(transitionTicks()));
    }

    private boolean isOpposed(double current) {
        if (this.targetId == null) return false;

        Set<MachineBeamDriver> drivers = DRIVERS.get(this.targetId);
        if (drivers == null) return false;

        int direction = Double.compare(this.host.goalScale(), current);
        if (direction == 0) return false;

        for (MachineBeamDriver other : drivers) {
            if (other == this || other.isRemoved() || !other.sealed) continue;
            if (Double.compare(other.host.goalScale(), current) == -direction) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Target findTarget(ServerLevel level) {
        LaserBehaviour laser = this.host.laser();
        if (laser == null) return null;

        SubLevel host = Sable.HELPER.getContaining(level, this.host.hostPos());
        UUID hostId = host == null ? null : host.getUniqueId();

        BlockHitResult blockHit = laser.getBlockHitResult();
        BlockPos blockHitPos = blockHit == null || blockHit.getType() == HitResult.Type.MISS
                ? null
                : blockHit.getBlockPos();

        if (blockHitPos != null) {
            SubLevel owner = Sable.HELPER.getContaining(level, blockHitPos);
            if (owner instanceof ServerSubLevel hitSubLevel
                    && !hitSubLevel.isRemoved()
                    && hitSubLevel.getUniqueId() != null
                    && !hitSubLevel.getUniqueId().equals(hostId)) {
                return new Target(hitSubLevel, blockHitPos);
            }
        }

        return sweepBeamCorridor(level, hostId);
    }

    private @Nullable Target sweepBeamCorridor(ServerLevel level, @Nullable UUID hostId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        Couple<Vec3> beam = this.host.beam();
        Vec3 start = beam.getFirst();
        Vec3 direction = beam.getSecond().subtract(start);
        if (direction.lengthSqr() < 1.0E-9D) return null;
        direction = direction.normalize();

        double reach = beamReach(level, start);
        if (reach <= 0.0D) return null;

        Vec3 end = start.add(direction.scale(reach));

        ServerSubLevel nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) continue;

            UUID id = subLevel.getUniqueId();
            if (id == null || id.equals(hostId)) continue;

            double distance = beamEntryDistance(subLevel, start, end);
            if (distance < 0.0D || distance >= nearestDistance) continue;

            nearest = subLevel;
            nearestDistance = distance;
        }

        return nearest == null ? null : new Target(nearest, centreOf(nearest));
    }

    private double beamReach(ServerLevel level, Vec3 start) {
        LaserBehaviour laser = this.host.laser();
        float length = this.host.beamLength();
        HitResult hit = laser == null ? null : laser.getClosestHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS) return length;

        double distance = Math.sqrt(
                Sable.HELPER.distanceSquaredWithSubLevels(level, start, hit.getLocation()));
        if (!Double.isFinite(distance) || distance <= 0.0D) return length;

        return Math.min(length, distance);
    }

    private static double beamEntryDistance(
            ServerSubLevel subLevel,
            Vec3 start,
            Vec3 end
    ) {
        var bounds = subLevel.boundingBox();
        if (bounds == null) return -1.0D;

        AABB box = new AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()
        ).inflate(BEAM_RADIUS);

        if (box.contains(start)) return 0.0D;

        return box.clip(start, end)
                .map(start::distanceTo)
                .orElse(-1.0D);
    }

    private static BlockPos centreOf(ServerSubLevel subLevel) {
        var plot = subLevel.getPlot().getBoundingBox();
        return new BlockPos(
                (plot.minX() + plot.maxX()) / 2,
                (plot.minY() + plot.maxY()) / 2,
                (plot.minZ() + plot.maxZ()) / 2
        );
    }

    public void clearTarget(boolean releaseVisuals) {
        Level hostLevel = this.host.hostLevel();
        if (this.targetId != null) {
            if (this.targetEntity == null) Pym.resize().release(this.targetId);
            DRIVERS.computeIfPresent(this.targetId, (id, drivers) -> {
                drivers.remove(this);
                return drivers.isEmpty() ? null : drivers;
            });
        }

        if (releaseVisuals && this.fieldActive) {
            if (this.targetEntity != null) {
                if (hostLevel instanceof ServerLevel level) EntityCompressionGlowPayload.clear(level, this.targetId);
            } else if (this.target != null && !this.target.isRemoved()) {
                CompressionSyncPayload.sendRelease(this.target);
            } else if (hostLevel instanceof ServerLevel level && this.targetId != null) {
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

    @Override
    public double commandedScale() {
        return this.sealed ? this.commandedScale : NO_COMMAND;
    }

    @Override
    public ScaleBounds bounds() {
        return this.host.bounds();
    }

    @Override
    public boolean stepwiseTransitions() {
        return false;
    }

    @Override
    public double transitionTicks() {
        return this.host.transitionTicks();
    }

    @Override
    public boolean isRemoved() {
        return this.host.hostRemoved();
    }

    private record Target(ServerSubLevel subLevel, BlockPos hitLocalPos) {}
}
