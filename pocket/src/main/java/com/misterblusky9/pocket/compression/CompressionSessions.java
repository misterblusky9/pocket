package com.misterblusky9.pocket.compression;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pocket.scale.ResizeActor;
import com.misterblusky9.pocket.scale.ResizeFeedback;
import com.misterblusky9.pocket.scale.ScaleLimits;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ResizeRequest;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionSessions {
    private static final double SPREAD_PER_TICK = 1.45D;
    private static final int MIN_ACQUIRE_TICKS = 10;
    private static final int MAX_ACQUIRE_TICKS = 200;

    public static final int INSTANT_ACQUIRE_TICKS = 3;

    private static final BeamSession.Pace CREATIVE_PACE = new BeamSession.Pace(6, 0.0F, 0);

    static final float LEVITITE_PER_TICK = 5.0F;

    private static final double AIM_RANGE = 160.0D;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private CompressionSessions() {}

    public static boolean hold(
            ServerPlayer player,
            ServerSubLevel subLevel,
            BlockPos hitLocalPos,
            double floor,
            boolean instant,
            net.minecraft.world.InteractionHand hand,
            boolean growingIntent,
            boolean propagateJoints,
            ScaleBounds limits
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved() || !ScaleBounds.isValid(floor)) return false;

        UUID id = subLevel.getUniqueId();
        if (id == null) return false;

        long now = player.level().getGameTime();
        if (!ResizeActor.survival(() -> withinLimits(player, subLevel, floor, now, limits, propagateJoints))) return false;

        Session session = SESSIONS.get(id);

        if (session != null && session.beam.heldBy(player)) {
            if (!ScaleBounds.same(session.floor, floor)) {
                SESSIONS.remove(session.subLevelId);
                CompressionSyncPayload.sendRelease(subLevel);
            } else {
                session.beam.hold(now);
                return true;
            }
            session = null;
        } else if (session != null) {
            return false;
        }

        int acquireTicks = instant
                ? INSTANT_ACQUIRE_TICKS
                : estimateAcquireTicks(subLevel);
        float levititePerTick = player.isCreative() ? 0.0F : LEVITITE_PER_TICK;

        boolean growing = growingIntent;
        int ceiling = Pym.resize().shrunkBlockLimit();

        int cellLimit = 0;
        if (!growing) {
            PlotContents contents = Pym.resize().contents(subLevel);
            String blocked = ResizeFeedback.cannotShrink(contents);
            if (blocked != null) {
                player.displayClientMessage(Component.literal(blocked), true);
                return false;
            }

            int blocks = contents.blocks();
            if (blocks > ceiling) {
                cellLimit = ceiling;
                player.displayClientMessage(Component.literal(
                        "Exceeds maximum block count (" + blocks + " / " + ceiling + ")"), true);
            }
        }

        if (Pym.resize().suspendDrivers(subLevel, now)) CompressionSyncPayload.sendRelease(subLevel);

        session = new Session(id, new BeamSession(player.getUUID(), hand, acquireTicks, levititePerTick, now), floor);
        session.blocked = cellLimit > 0;
        session.propagateJoints = propagateJoints;
        session.limits = limits;
        session.growing = growing;
        SESSIONS.put(id, session);

        CompressionSyncPayload.sendBegin(
                subLevel, player, hitLocalPos, acquireTicks, !instant, growing, cellLimit);
        return true;
    }

    public static void instant(
            ServerPlayer player,
            ServerSubLevel subLevel,
            BlockPos hitLocalPos,
            CompressionStage requested,
            boolean propagateJoints,
            ScaleBounds limits
    ) {
        if (requested != null) instant(player, subLevel, hitLocalPos, requested.scale(), propagateJoints, limits);
    }

    public static void instant(
            ServerPlayer player,
            ServerSubLevel subLevel,
            BlockPos hitLocalPos,
            double requested,
            boolean propagateJoints,
            ScaleBounds limits
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved() || !ScaleBounds.isValid(requested)) return;

        UUID id = subLevel.getUniqueId();
        if (id == null) return;

        double current = Pym.scale().of(subLevel);
        long now = player.level().getGameTime();
        if (!withinLimits(player, subLevel, requested, now, limits, propagateJoints)) return;

        if (requested < current - ScaleBounds.EPSILON) {
            String blocked = ResizeFeedback.cannotShrink(Pym.resize().contents(subLevel));
            if (blocked != null) {
                player.displayClientMessage(Component.literal(blocked), true);
                return;
            }
        }

        if (Pym.resize().suspendDrivers(subLevel, now)) CompressionSyncPayload.sendRelease(subLevel);
        ResizeResult result = ResizeActor.as(player,
                () -> request(subLevel, requested, limits, propagateJoints).submit());
        if (!ResizeFeedback.report(player, result)) return;

        Session session = new Session(
                id, new BeamSession(player.getUUID(), null, INSTANT_ACQUIRE_TICKS, 0.0F, now), requested);
        session.autoRelease = true;
        session.uniformSteps = true;

        session.directDrive = true;
        SESSIONS.put(id, session);

        CompressionSyncPayload.sendBegin(
                subLevel, player, hitLocalPos, INSTANT_ACQUIRE_TICKS, false,
                requested > current + ScaleBounds.EPSILON, 0);
    }

    private static ResizeRequest request(
            ServerSubLevel subLevel,
            double scale,
            ScaleBounds limits,
            boolean propagateJoints
    ) {
        ResizeRequest request = Pym.resize().request(subLevel).scaleTo(scale).propagate(propagateJoints).bounds(limits);
        if (ScaleLimits.confirmsUnsupported(limits)) request.unsupported();
        return request;
    }

    private static boolean withinLimits(
            ServerPlayer player,
            ServerSubLevel subLevel,
            double requested,
            long now,
            ScaleBounds limits,
            boolean propagateJoints
    ) {
        ResizeResult check = request(subLevel, requested, limits, propagateJoints).check();
        if (!check.accepted()) {
            ResizeFeedback.report(player, check);
            return false;
        }
        return true;
    }

    public static boolean renew(ServerPlayer player, double goal) {
        if (player == null) return false;

        long now = player.level().getGameTime();
        boolean held = false;
        boolean sampledAim = false;
        UUID aimedSubLevelId = null;

        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (!session.beam.heldBy(player) || session.autoRelease) continue;

            if (!ScaleBounds.same(session.floor, goal)) {
                iterator.remove();

                ServerLevel level = session.level != null ? session.level : player.serverLevel();
                CompressionSyncPayload.sendRelease(level, session.subLevelId);
                continue;
            }

            if (!session.beam.sealed() && !sampledAim) {
                CompressionTargeting.Target target = CompressionTargeting.find(player, AIM_RANGE);
                aimedSubLevelId = target == null ? null : target.subLevel().getUniqueId();
                sampledAim = true;
            }

            session.illuminated = session.beam.sealed() || session.subLevelId.equals(aimedSubLevelId);
            session.beam.hold(now);
            Pym.resize().sustainSuspension(session.subLevelId, now);
            held = true;
        }

        return held;
    }

    public static boolean isHeld(UUID subLevelId) {
        if (subLevelId == null) return false;
        for (Session session : SESSIONS.values()) {
            if (subLevelId.equals(session.subLevelId)) return true;
        }
        return false;
    }

    public static UUID lockedSubLevelId(ServerPlayer player) {
        if (player == null) return null;
        for (Session session : SESSIONS.values()) {
            if (session.beam.heldBy(player)) return session.subLevelId;
        }
        return null;
    }

    public static ServerSubLevel lockedSubLevel(ServerPlayer player) {
        if (player == null) return null;
        for (Session session : SESSIONS.values()) {
            if (!session.beam.heldBy(player)) continue;
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(player.level());
            if (container == null) return null;
            var found = container.getSubLevel(session.subLevelId);
            if (found instanceof ServerSubLevel subLevel && !subLevel.isRemoved()) return subLevel;
            return null;
        }
        return null;
    }

    public static void release(ServerPlayer player, UUID subLevelId) {
        Session session = SESSIONS.get(subLevelId);
        if (session == null) return;
        if (player != null && !session.beam.heldBy(player)) return;
        end(session, true);
    }

    public static void releaseAll(ServerPlayer player) {
        if (player == null) return;
        for (Session session : SESSIONS.values()) {
            if (session.beam.heldBy(player)) end(session, true);
        }
    }

    public static void releaseSubLevel(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        Session session = SESSIONS.remove(subLevel.getUniqueId());
        if (session == null) return;
        try {
            CompressionSyncPayload.sendRelease(subLevel);
        } catch (RuntimeException ignored) {
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();

            ServerPlayer holder = event.getServer().getPlayerList().getPlayer(session.beam.holder());
            ServerSubLevel subLevel = findSubLevel(event.getServer(), session);

            if (holder == null || subLevel == null || subLevel.isRemoved()) {
                iterator.remove();
                if (subLevel != null) CompressionSyncPayload.sendRelease(subLevel);
                continue;
            }

            long now = holder.level().getGameTime();

            if (!session.autoRelease && session.beam.expired(now)) {
                iterator.remove();
                CompressionSyncPayload.sendRelease(subLevel);
                continue;
            }

            if (!tick(session, holder, subLevel)) {
                iterator.remove();
                CompressionSyncPayload.sendRelease(subLevel);
            }
        }
    }

    private static boolean tick(
            Session session,
            ServerPlayer holder,
            ServerSubLevel subLevel
    ) {
        if (session.blocked) return true;
        BeamSession beam = session.beam;

        if (!beam.sealed()) {
            if (!session.autoRelease && !session.illuminated) return true;
            if (!beam.acquire(holder)) {
                holder.displayClientMessage(Component.translatable("pocket.message.no_power"), true);
                return false;
            }
            if (beam.sealed()) session.illuminated = true;
            return true;
        }

        if (session.directDrive) return !Pym.scale().isSettled(subLevel);

        if (!Pym.scale().isSettled(subLevel)) return true;

        double current = Pym.scale().settled(subLevel);
        boolean arrived = session.growing
                ? current >= session.floor - ScaleBounds.EPSILON
                : current <= session.floor + ScaleBounds.EPSILON;
        if (arrived) return !session.autoRelease;

        double next = ScaleLadder.stepToward(current, session.floor);
        BeamSession.Tick tick = beam.advance(
                session.uniformSteps ? CREATIVE_PACE : BeamSession.Pace.HELD_BEAM,
                ScaleBounds.same(next, session.floor));
        if (tick.pulse()) CompressionSyncPayload.sendPulse(subLevel, beam.holder());
        if (!tick.step()) return true;

        long now = subLevel.getLevel().getGameTime();
        if (!ResizeActor.survival(() -> withinLimits(holder, subLevel, next, now, session.limits, session.propagateJoints))) return false;

        ResizeResult result = ResizeActor.survival(() -> ResizeActor.as(holder,
                () -> request(subLevel, next, session.limits, session.propagateJoints).submit()));
        if (!ResizeFeedback.report(holder, result)) return false;
        beam.stepped();
        return true;
    }

    private static void end(Session session, boolean notify) {
        SESSIONS.remove(session.subLevelId);
        if (!notify) return;

        ServerLevel level = session.level;
        if (level == null) return;
        CompressionSyncPayload.sendRelease(level, session.subLevelId);
    }

    public static int estimateAcquireTicks(ServerSubLevel subLevel) {
        var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return MIN_ACQUIRE_TICKS;

        double span = (bounds.maxX() - bounds.minX() + 1)
                + (bounds.maxY() - bounds.minY() + 1)
                + (bounds.maxZ() - bounds.minZ() + 1);

        int ticks = (int) Math.round(span / SPREAD_PER_TICK);
        return Math.max(MIN_ACQUIRE_TICKS, Math.min(MAX_ACQUIRE_TICKS, ticks));
    }

    private static ServerSubLevel findSubLevel(
            net.minecraft.server.MinecraftServer server,
            Session session
    ) {
        if (session.level != null && !session.level.isClientSide()) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(session.level);
            if (container != null) {
                var found = container.getSubLevel(session.subLevelId);
                if (found instanceof ServerSubLevel serverSubLevel) return serverSubLevel;
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            var found = container.getSubLevel(session.subLevelId);
            if (found instanceof ServerSubLevel serverSubLevel) {
                session.level = level;
                return serverSubLevel;
            }
        }
        return null;
    }

    private static final class Session {
        private final UUID subLevelId;
        private final BeamSession beam;

        private ServerLevel level;
        private double floor;
        private boolean autoRelease;
        private boolean uniformSteps;
        private boolean directDrive;
        private boolean blocked;
        private boolean illuminated = true;
        private boolean propagateJoints = true;
        private boolean growing;
        private ScaleBounds limits = ScaleBounds.SAFE;

        private Session(UUID subLevelId, BeamSession beam, double floor) {
            this.subLevelId = subLevelId;
            this.beam = beam;
            this.floor = floor;
        }
    }
}
