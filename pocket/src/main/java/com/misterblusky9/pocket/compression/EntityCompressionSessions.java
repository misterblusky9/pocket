package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pocket.network.EntityCompressionGlowPayload;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCompressionSessions {
    private static final int RESIZE_TICKS = (int) Pym.resize().defaultTransitionTicks();
    private static final int GLOW_REFRESH_TICKS = 5;
    private static final int GLOW_TIMEOUT_TICKS = 15;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private EntityCompressionSessions() {}

    public static boolean creative(ServerPlayer holder, Entity target, double goal) {
        if (!valid(holder, target, goal)) return false;

        double current = EntityCompressionTargeting.scale(target);
        if (ScaleBounds.same(current, goal)) return false;

        Session session = new Session(target.getUUID(), holder.getUUID(), null, goal, current);
        if (!session.resizeTo(target, goal)) return false;
        Session previous = SESSIONS.put(target.getUUID(), session);
        if (previous != null) EntityCompressionGlowPayload.clear(target);
        EntityCompressionGlowPayload.send(target, goal > current, GLOW_TIMEOUT_TICKS);
        return true;
    }

    public static boolean renewCannon(ServerPlayer holder, double goal, double range) {
        if (holder == null) return false;

        EntityCompressionTargeting.Target aimed = EntityCompressionTargeting.find(holder, range);
        if (aimed == null) return false;

        Session session = SESSIONS.get(aimed.entity().getUUID());
        if (session == null || session.beam == null || !session.beam.heldBy(holder)
                || !ScaleBounds.same(session.goal, PersonalScale.goalFor(aimed.entity(), session.settled, goal))) {
            return false;
        }
        session.beam.hold(holder.level().getGameTime());
        return true;
    }

    public static boolean holdCannon(
            ServerPlayer holder,
            Entity target,
            double requested,
            InteractionHand hand
    ) {
        if (!valid(holder, target, requested)) return false;

        long now = holder.level().getGameTime();
        Session existing = SESSIONS.get(target.getUUID());
        if (existing != null && existing.beam != null && existing.beam.heldBy(holder)
                && ScaleBounds.same(existing.goal, PersonalScale.goalFor(target, existing.settled, requested))) {
            existing.beam.hold(now);
            return true;
        }

        releaseCannon(holder);

        double current = EntityCompressionTargeting.scale(target);
        double goal = PersonalScale.goalFor(target, current, requested);
        if (ScaleBounds.same(current, goal)) return false;

        float levitite = holder.isCreative() ? 0.0F : CompressionSessions.LEVITITE_PER_TICK;
        BeamSession beam = new BeamSession(holder.getUUID(), hand, 0, levitite, now);
        Session previous = SESSIONS.put(target.getUUID(),
                new Session(target.getUUID(), holder.getUUID(), beam, goal, current));
        if (previous != null) EntityCompressionGlowPayload.clear(target);
        EntityCompressionGlowPayload.send(target, goal > current, GLOW_TIMEOUT_TICKS);
        return true;
    }

    public static void releaseCannon(ServerPlayer holder) {
        if (holder == null) return;
        SESSIONS.values().removeIf(session -> {
            if (session.beam == null || !session.beam.heldBy(holder)) return false;
            EntityCompressionGlowPayload.clear(holder.serverLevel(), session.targetId);
            return true;
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            Entity target = findEntity(event, session.targetId);
            ServerPlayer holder = event.getServer().getPlayerList().getPlayer(session.holderId);
            if (target == null || !target.isAlive() || holder == null || !tick(session, target, holder)) {
                ServerLevel level = target != null && target.level() instanceof ServerLevel serverLevel
                        ? serverLevel : holder == null ? null : holder.serverLevel();
                EntityCompressionGlowPayload.clear(level, session.targetId);
                iterator.remove();
                continue;
            }
            if (holder.level().getGameTime() % GLOW_REFRESH_TICKS == 0) {
                EntityCompressionGlowPayload.send(
                        target, session.growing, GLOW_TIMEOUT_TICKS);
            }
        }
    }

    private static boolean tick(Session session, Entity target, ServerPlayer holder) {
        if (locked(target, holder)) return false;
        BeamSession beam = session.beam;
        if (beam != null) {
            if (beam.expired(holder.level().getGameTime())) return false;
            if (!session.resizing() && ScaleBounds.same(session.settled, session.goal)) return true;
            if (!beam.drain(holder)) {
                holder.displayClientMessage(Component.translatable("pocket.message.levitite_depleted"), true);
                return false;
            }
        }

        session.tickResize();
        if (beam == null) return session.resizing();

        if (!session.resizing() && ScaleBounds.same(session.settled, session.goal)) return true;
        double next = ScaleLadder.stepToward(session.settled, session.goal);
        BeamSession.Tick tick = beam.advance(BeamSession.Pace.HELD_BEAM, ScaleBounds.same(next, session.goal));
        if (session.resizing() || !tick.step()) return true;

        if (!session.resizeTo(target, next)) return false;
        beam.stepped();
        return true;
    }

    private static boolean valid(ServerPlayer holder, Entity target, double goal) {
        if (holder == null || target == null || !target.isAlive() || target == holder) return false;
        if (!Pym.entities().available() || !ScaleBounds.isValid(goal)) return false;
        if (locked(target, holder)) {
            holder.displayClientMessage(Component.translatable("pocket.message.size_locked", target.getDisplayName()), true);
            return false;
        }
        return true;
    }

    private static boolean locked(Entity target, ServerPlayer holder) {
        return target instanceof Player player && PersonalLock.blocks(holder, player);
    }

    private static Entity findEntity(ServerTickEvent.Post event, UUID entityId) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static final class Session {
        private final UUID targetId;
        private final UUID holderId;
        private final BeamSession beam;
        private final double goal;
        private final boolean growing;

        private double settled;
        private double resizingTo = Double.NaN;
        private int resizeAge;

        private Session(UUID targetId, UUID holderId, BeamSession beam, double goal, double settled) {
            this.targetId = targetId;
            this.holderId = holderId;
            this.beam = beam;
            this.goal = goal;
            this.growing = goal > settled;
            this.settled = settled;
        }

        boolean resizing() {
            return !Double.isNaN(this.resizingTo);
        }

        boolean resizeTo(Entity target, double scale) {
            if (!Pym.entities().setScale(target, scale / EntityCompressionTargeting.containerScale(target), RESIZE_TICKS)) {
                return false;
            }
            this.resizingTo = scale;
            this.resizeAge = 0;
            return true;
        }

        void tickResize() {
            if (!resizing() || ++this.resizeAge < Math.max(1, RESIZE_TICKS)) return;
            this.settled = this.resizingTo;
            this.resizingTo = Double.NaN;
        }
    }
}
