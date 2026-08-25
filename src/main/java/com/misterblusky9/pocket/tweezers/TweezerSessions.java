package com.misterblusky9.pocket.tweezers;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.item.TweezersItem;
import com.misterblusky9.pocket.network.TweezerGripsPayload;
import com.misterblusky9.pocket.network.TweezerLocksPayload;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TweezerSessions {
    public static final double MIN_HOLD = 0.35D;

    public static final double MAX_HOLD = 12.0D;

    private static final double LINEAR_STIFFNESS = 2650.0D;
    private static final double LINEAR_DAMPING = 125.0D;
    private static final double ANGULAR_STIFFNESS = 10000.0D;
    private static final double ANGULAR_DAMPING = 850.0D;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Queue<Session> RELEASED = new ConcurrentLinkedQueue<>();

    // Commands

    public static void grab(
            final Player player,
            final ServerSubLevel subLevel,
            final Vector3d plotAnchor,
            final Vector3d relativeGoal,
            final Quaterniond orientation
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved()) return;

        stop(player);

        final ServerLevel level = subLevel.getLevel();
        final boolean wasLocked = TweezerLocks.locked(level, subLevel);
        if (wasLocked) TweezerLocks.remove(level, subLevel);

        SESSIONS.put(player.getUUID(), new Session(
                player.getUUID(), subLevel, new Vector3d(plotAnchor),
                clampGoal(new Vector3d(relativeGoal)), new Quaterniond(orientation)));
        markGripsDirty(level);

        if (wasLocked) {
            final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container != null) flushLocks(container);
        }

        PocketTrace.scale("tweezers grabbed {}", subLevel.getUniqueId());
    }

    public static void steer(
            final Player player, final Vector3d relativeGoal, final Quaterniond orientation
    ) {
        if (player == null) return;
        final Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;

        clampGoal(session.relativeGoal.set(relativeGoal));
        session.orientation.set(orientation);

        TweezerLocks.remove(session.subLevel.getLevel(), session.subLevel);
    }

    public static void stop(final Player player) {
        if (player == null) return;
        final Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            RELEASED.add(session);
            markGripsDirty(session.subLevel.getLevel());
        }
    }

    public static void lock(final Player player, final UUID subLevelId) {
        if (player == null || subLevelId == null) return;
        if (!(player.level() instanceof final ServerLevel level)) return;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;
        if (!(container.getSubLevel(subLevelId) instanceof final ServerSubLevel craft)) return;
        if (craft.isRemoved()) return;

        TweezerLocks.toggle(level, subLevelId);
        final boolean nowLocked = TweezerLocks.locked(level, craft);
        PocketTrace.scale("tweezers {} {}", nowLocked ? "pinned" : "released", subLevelId);

        if (nowLocked) {
            final Session session = SESSIONS.get(player.getUUID());
            if (session != null && session.subLevel == craft) stop(player);
        }

        flushLocks(container);
    }

    public static boolean isHolding(final Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    // Driving

    private static final long LOCK_SYNC_INTERVAL = 20L;

    private static final Map<ResourceKey<Level>, Long> NEXT_LOCK_SYNC = new ConcurrentHashMap<>();

    private static final Set<ResourceKey<Level>> GRIPS_DIRTY = ConcurrentHashMap.newKeySet();

    private static void markGripsDirty(final Level level) {
        if (level != null) GRIPS_DIRTY.add(level.dimension());
    }

    public static void tick(final ServerSubLevelContainer container) {
        syncLocks(container);

        for (Session released = RELEASED.poll(); released != null; released = RELEASED.poll()) {
            released.detach();
        }

        SESSIONS.values().removeIf(session -> {
            final Player player = session.subLevel.getLevel().getPlayerByUUID(session.playerId);

            if (session.subLevel.isRemoved() || !TweezersItem.isHolding(player)) {
                session.detach();
                markGripsDirty(session.subLevel.getLevel());
                return true;
            }
            return false;
        });

        if (GRIPS_DIRTY.remove(container.getLevel().dimension())) syncGrips(container);
    }

    private static void syncLocks(final ServerSubLevelContainer container) {
        final ServerLevel level = container.getLevel();
        final long now = level.getGameTime();
        if (now < NEXT_LOCK_SYNC.getOrDefault(level.dimension(), Long.MIN_VALUE)) return;
        NEXT_LOCK_SYNC.put(level.dimension(), now + LOCK_SYNC_INTERVAL);

        flushLocks(container);
    }

    private static void flushLocks(final ServerSubLevelContainer container) {
        final ServerLevel level = container.getLevel();
        final List<UUID> pinned = new ArrayList<>();
        for (final ServerSubLevel craft : container.getAllSubLevels()) {
            if (craft.isRemoved()) continue;
            if (TweezerLocks.locked(level, craft)) pinned.add(craft.getUniqueId());
        }

        PacketDistributor.sendToPlayersInDimension(level, new TweezerLocksPayload(pinned));
    }

    private static void syncGrips(final ServerSubLevelContainer container) {
        final ServerLevel level = container.getLevel();
        final List<TweezerGripsPayload.Grip> grips = new ArrayList<>();
        for (final Session session : SESSIONS.values()) {
            if (session.subLevel.getLevel() != level) continue;
            grips.add(new TweezerGripsPayload.Grip(
                    session.playerId,
                    session.plotAnchor.x, session.plotAnchor.y, session.plotAnchor.z));
        }

        PacketDistributor.sendToPlayersInDimension(level, new TweezerGripsPayload(grips));
    }

    public static void drive(
            final ServerSubLevel subLevel, final PhysicsPipeline pipeline, final double partialTick
    ) {
        if (SESSIONS.isEmpty() || subLevel == null || pipeline == null) return;

        for (final Session session : SESSIONS.values()) {
            if (session.subLevel != subLevel) continue;

            final Player player = subLevel.getLevel().getPlayerByUUID(session.playerId);
            if (player == null) return;

            session.drive(pipeline, player, partialTick);
            return;
        }
    }

    private static Vector3d clampGoal(final Vector3d relativeGoal) {
        final double length = relativeGoal.length();
        if (length < 1.0E-6D) return relativeGoal.set(0.0D, 0.0D, MIN_HOLD);

        final double clamped = Math.max(MIN_HOLD, Math.min(MAX_HOLD, length));
        return relativeGoal.mul(clamped / length);
    }

    // Session
    private static final class Session {
        private final UUID playerId;
        private final ServerSubLevel subLevel;

        private final Vector3d plotAnchor;

        private final Vector3d relativeGoal;

        private final Quaterniond orientation;
        private PhysicsConstraintHandle constraint;
        private final Vector3d goal = new Vector3d();

        private Session(
                final UUID playerId,
                final ServerSubLevel subLevel,
                final Vector3d plotAnchor,
                final Vector3d relativeGoal,
                final Quaterniond orientation
        ) {
            this.playerId = playerId;
            this.subLevel = subLevel;
            this.plotAnchor = plotAnchor;
            this.relativeGoal = relativeGoal;
            this.orientation = orientation;
        }

        private void drive(final PhysicsPipeline pipeline, final Player player, final double partialTick) {
            detach();

            this.constraint = pipeline.addConstraint(
                    (PhysicsPipelineBody) null,
                    this.subLevel,
                    new FreeConstraintConfiguration(
                            JOMLConversion.ZERO,
                            this.plotAnchor,
                            new Quaterniond(this.orientation)));

            if (this.constraint == null) return;

            final double x = Mth.lerp(partialTick, player.xOld, player.getX());
            final double y = Mth.lerp(partialTick, player.yOld, player.getY()) + player.getEyeHeight();
            final double z = Mth.lerp(partialTick, player.zOld, player.getZ());

            this.goal.set(this.relativeGoal).add(x, y, z);

            this.orientation.transformInverse(this.goal);

            this.constraint.setMotor(
                    ConstraintJointAxis.LINEAR_X, this.goal.x, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0D);
            this.constraint.setMotor(
                    ConstraintJointAxis.LINEAR_Y, this.goal.y, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0D);
            this.constraint.setMotor(
                    ConstraintJointAxis.LINEAR_Z, this.goal.z, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0D);

            for (final ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                this.constraint.setMotor(axis, 0.0D, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0D);
            }
        }

        private void detach() {
            if (this.constraint == null) return;

            this.constraint.remove();
            this.constraint = null;
        }
    }

    private TweezerSessions() {}
}
