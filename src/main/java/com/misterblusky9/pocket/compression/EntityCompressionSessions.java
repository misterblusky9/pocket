package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CompressionGunTank;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleTransitionCurve;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCompressionSessions {
    private static final int HOLD_GRACE_TICKS = 3;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private EntityCompressionSessions() {}

    public static boolean creative(
            final ServerPlayer holder,
            final LivingEntity target,
            final double goal
    ) {
        if (!valid(holder, target, goal)) return false;

        final double current = EntityCompressionTargeting.scale(target);
        if (sameScale(current, goal)) return false;

        final Session session = new Session(
                target.getUUID(), holder.getUUID(), Mode.CREATIVE, goal,
                holder.level().getGameTime(), InteractionHand.MAIN_HAND);
        session.settledScale = current;
        beginTransition(session, target, goal);
        SESSIONS.put(target.getUUID(), session);
        return true;
    }

    public static boolean renewCannon(
            final ServerPlayer holder,
            final double goal,
            final double range
    ) {
        if (holder == null) return false;

        final EntityCompressionTargeting.Target aimed = EntityCompressionTargeting.find(holder, range);
        if (aimed == null) return false;

        final Session session = SESSIONS.get(aimed.entity().getUUID());
        if (session == null
                || session.mode != Mode.CANNON
                || !session.holderId.equals(holder.getUUID())
                || !sameScale(session.goal, goal)) {
            return false;
        }

        session.lastHeldTick = holder.level().getGameTime();
        return true;
    }

    public static boolean holdCannon(
            final ServerPlayer holder,
            final LivingEntity target,
            final double goal,
            final InteractionHand hand
    ) {
        if (!valid(holder, target, goal)) return false;

        final long now = holder.level().getGameTime();
        final Session existing = SESSIONS.get(target.getUUID());
        if (existing != null
                && existing.mode == Mode.CANNON
                && existing.holderId.equals(holder.getUUID())
                && sameScale(existing.goal, goal)) {
            existing.lastHeldTick = now;
            return true;
        }

        releaseCannon(holder);

        final double current = EntityCompressionTargeting.scale(target);
        if (sameScale(current, goal)) return false;

        final Session session = new Session(
                target.getUUID(), holder.getUUID(), Mode.CANNON, goal, now,
                hand == null ? InteractionHand.MAIN_HAND : hand);
        session.settledScale = current;
        session.sinceStep = Integer.MAX_VALUE / 2;
        session.levititePerTick = holder.isCreative() ? 0.0F : CompressionSessions.LEVITITE_PER_TICK;
        SESSIONS.put(target.getUUID(), session);
        return true;
    }

    public static void releaseCannon(final ServerPlayer holder) {
        if (holder == null) return;
        final UUID holderId = holder.getUUID();
        SESSIONS.entrySet().removeIf(entry -> {
            final Session session = entry.getValue();
            return session.mode == Mode.CANNON && session.holderId.equals(holderId);
        });
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;

        final Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Session session = iterator.next().getValue();
            final LivingEntity target = findLiving(event, session.targetId);
            final ServerPlayer holder = event.getServer().getPlayerList().getPlayer(session.holderId);

            if (target == null || !target.isAlive() || holder == null || !PehkuiScaleBridge.isOperational()) {
                iterator.remove();
                continue;
            }

            final long now = holder.level().getGameTime();
            if (session.mode == Mode.CANNON) {
                if (now - session.lastHeldTick > HOLD_GRACE_TICKS) {
                    iterator.remove();
                    continue;
                }
                if (!drawLevitite(session, holder)) {
                    holder.displayClientMessage(Component.translatable("pocket.message.levitite_depleted"), true);
                    iterator.remove();
                    continue;
                }
            }

            tickTransition(session);

            if (session.mode == Mode.CREATIVE) {
                if (!session.transitioning) iterator.remove();
                continue;
            }

            session.sinceStep++;
            if (!session.transitioning && sameScale(session.settledScale, session.goal)) {
                iterator.remove();
                continue;
            }
            if (session.transitioning) continue;

            final int delay = CompressionSessions.cannonStepDelay(
                    session.steps, session.settledScale, session.goal);
            if (session.sinceStep < delay) continue;

            final double next = CompressionStage.stepToward(session.settledScale, session.goal);
            beginTransition(session, target, next);
            session.sinceStep = 0;
            session.steps++;
        }
    }

    private static void tickTransition(final Session session) {
        if (!session.transitioning) return;

        session.transitionTicks++;
        if (!ScaleTransitionCurve.complete(session.transitionTicks, 1.0D)) return;

        session.settledScale = session.transitionTo;
        session.transitioning = false;
        session.transitionTicks = 0;
    }

    private static void beginTransition(
            final Session session,
            final LivingEntity target,
            final double to
    ) {
        session.transitionTo = to;
        session.transitionTicks = 0;
        session.transitioning = true;
        PehkuiScaleBridge.setPersonalScale(target, to);
    }

    private static boolean drawLevitite(final Session session, final ServerPlayer holder) {
        if (session.levititePerTick <= 0.0F) return true;

        session.levititeDebt += session.levititePerTick;
        final int whole = (int) session.levititeDebt;
        if (whole <= 0) return true;
        session.levititeDebt -= whole;

        final ItemStack gun = holder.getItemInHand(session.hand);
        return gun.getItem() instanceof CompressionGunItem
                && CompressionGunTank.drain(gun, whole) == whole;
    }

    private static boolean valid(
            final ServerPlayer holder,
            final LivingEntity target,
            final double goal
    ) {
        return holder != null
                && target != null
                && target.isAlive()
                && target != holder
                && PocketSized.isValidScale(goal)
                && PehkuiScaleBridge.isOperational();
    }

    private static boolean sameScale(final double first, final double second) {
        return Math.abs(first - second) <= PocketSized.EPSILON;
    }

    private static LivingEntity findLiving(
            final ServerTickEvent.Post event,
            final UUID entityId
    ) {
        for (final ServerLevel level : event.getServer().getAllLevels()) {
            final Entity entity = level.getEntity(entityId);
            if (entity instanceof final LivingEntity living) return living;
        }
        return null;
    }

    private enum Mode {
        CREATIVE,
        CANNON
    }

    private static final class Session {
        private final UUID targetId;
        private final UUID holderId;
        private final Mode mode;
        private final double goal;
        private final InteractionHand hand;

        private long lastHeldTick;
        private double settledScale;
        private int sinceStep;
        private int steps;
        private float levititePerTick;
        private float levititeDebt;

        private boolean transitioning;
        private double transitionTo;
        private int transitionTicks;

        private Session(
                final UUID targetId,
                final UUID holderId,
                final Mode mode,
                final double goal,
                final long now,
                final InteractionHand hand
        ) {
            this.targetId = targetId;
            this.holderId = holderId;
            this.mode = mode;
            this.goal = goal;
            this.lastHeldTick = now;
            this.hand = hand;
        }
    }
}
