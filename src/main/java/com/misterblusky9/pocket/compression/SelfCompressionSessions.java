package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.network.SelfCompressionEffectPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelfCompressionSessions {
    private static final String STAGE_KEY = "PocketPersonalScaleDepth";

    private static final int HOLD_GRACE_TICKS = 3;
    private static final int STEP_BASE_TICKS = 10;
    private static final int STEP_GROWTH = 4;
    private static final int FINAL_STEP_EXTRA_TICKS = 8;
    private static final int PULSE_LEAD_TICKS = 3;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private SelfCompressionSessions() {}

    public static boolean begin(
            final ServerPlayer player,
            final CompressionStage goal,
            final InteractionHand hand,
            final boolean growing
    ) {
        if (player == null || goal == null || !PehkuiScaleBridge.isOperational()) return false;

        final UUID id = player.getUUID();
        final long now = player.level().getGameTime();
        final CompressionStage current = currentStage(player);

        if (current == goal) return false;

        final Session existing = SESSIONS.get(id);
        if (existing != null) {
            existing.goal = goal;
            existing.growing = growing;
            existing.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
            existing.lastHeldTick = now;
            return true;
        }

        final Session session = new Session(id, goal, growing, now);
        session.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        SESSIONS.put(id, session);
        SelfCompressionEffectPayload.sendBegin(player, growing);
        SelfCompressionEffectPayload.sendPulse(player, growing);
        return true;
    }

    public static void instant(final ServerPlayer player, final CompressionStage stage) {
        if (player == null || stage == null || !PehkuiScaleBridge.isOperational()) return;
        release(player);
        final boolean growing = stage.depth() < currentStage(player).depth();
        SelfCompressionEffectPayload.sendBegin(player, growing);
        SelfCompressionEffectPayload.sendPulse(player, growing);
        applyStage(player, stage);
        SelfCompressionEffectPayload.sendRelease(player);
    }

    public static boolean renew(final ServerPlayer player, final CompressionStage goal) {
        if (player == null) return false;

        final Session session = SESSIONS.get(player.getUUID());
        if (session == null) return false;
        if (goal != null) session.goal = goal;
        session.lastHeldTick = player.level().getGameTime();
        return true;
    }

    public static void release(final ServerPlayer player) {
        if (player == null) return;

        final Session session = SESSIONS.remove(player.getUUID());
        if (session != null) SelfCompressionEffectPayload.sendRelease(player);
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;

        final Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Session session = iterator.next().getValue();
            final ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }

            final long now = player.level().getGameTime();
            if (now - session.lastHeldTick > HOLD_GRACE_TICKS) {
                iterator.remove();
                SelfCompressionEffectPayload.sendRelease(player);
                continue;
            }

            if (!tick(session, player)) {
                iterator.remove();
                SelfCompressionEffectPayload.sendRelease(player);
            }
        }
    }

    private static boolean tick(final Session session, final ServerPlayer player) {
        final CompressionStage current = currentStage(player);
        if (current == session.goal) return false;

        session.sinceStep++;
        final int delay = stepDelay(session, current);
        if (!session.pulsed && session.sinceStep >= Math.max(0, delay - PULSE_LEAD_TICKS)) {
            SelfCompressionEffectPayload.sendPulse(player, session.growing);
            session.pulsed = true;
        }
        if (session.sinceStep < delay) return true;

        final CompressionStage next = current.stepToward(session.goal);
        applyStage(player, next);
        session.sinceStep = 0;
        session.pulsed = false;
        session.steps++;
        return next != session.goal;
    }


    private static int stepDelay(final Session session, final CompressionStage current) {
        final int base = STEP_BASE_TICKS + session.steps * STEP_GROWTH;
        final CompressionStage next = current.stepToward(session.goal);
        return next == session.goal ? base + FINAL_STEP_EXTRA_TICKS : base;
    }

    public static CompressionStage currentStage(final ServerPlayer player) {
        if (player == null) return CompressionStage.NORMAL;
        final int depth = player.getPersistentData().getInt(STAGE_KEY);
        return CompressionStage.fromDepth(depth);
    }

    private static void applyStage(final ServerPlayer player, final CompressionStage stage) {
        if (player == null || stage == null) return;

        player.getPersistentData().putInt(STAGE_KEY, stage.depth());
        if (stage == CompressionStage.NORMAL) {
            PehkuiScaleBridge.clearPersonalScale(player);
            return;
        }

        PehkuiScaleBridge.setPersonalScale(player, stage.scale());
    }

    private static final class Session {
        private final UUID playerId;
        private CompressionStage goal;
        private boolean growing;
        private long lastHeldTick;
        private int sinceStep;
        private int steps;
        private boolean pulsed;
        private InteractionHand hand = InteractionHand.MAIN_HAND;

        private Session(
                final UUID playerId,
                final CompressionStage goal,
                final boolean growing,
                final long now
        ) {
            this.playerId = playerId;
            this.goal = goal;
            this.growing = growing;
            this.lastHeldTick = now;
            this.sinceStep = 0;
        }
    }
}
