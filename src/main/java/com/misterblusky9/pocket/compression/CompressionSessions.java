package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ManualScaleOverride;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionSessions {
    private static final double SPREAD_PER_TICK = 1.45D;
    private static final int MIN_ACQUIRE_TICKS = 10;
    private static final int MAX_ACQUIRE_TICKS = 200;

    public static final int INSTANT_ACQUIRE_TICKS = 3;

    private static final int STEP_BASE_TICKS = 14;

    private static final float STEP_GROWTH = 0.85F;

    private static final int FINAL_STEP_EXTRA_TICKS = 22;

    private static final int CREATIVE_STEP_TICKS = 6;

    public static final int SURVIVAL_BLOCK_LIMIT = 8192;

    private static final double MASS_PER_AIR_UNIT = 250.0D;
    private static final int MIN_ACQUIRE_AIR = 16;
    private static final int MAX_ACQUIRE_AIR = 600;

    private static final int PULSE_LEAD_TICKS = 10;

    private static final int HOLD_GRACE_TICKS = 3;
    private static final double AIM_RANGE = 160.0D;

    private static final int AIR_PER_DURABILITY = 12;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private CompressionSessions() {}

    public static boolean hold(
            final ServerPlayer player,
            final ServerSubLevel subLevel,
            final BlockPos hitLocalPos,
            final CompressionStage floor,
            final boolean instant,
            final net.minecraft.world.InteractionHand hand,
            final boolean growingIntent,
            final boolean propagateJoints
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved()) return false;

        final UUID id = subLevel.getUniqueId();
        if (id == null) return false;

        final long now = player.level().getGameTime();
        Session session = SESSIONS.get(id);

        if (session != null && session.holder.equals(player.getUUID())) {
            if (session.floor != floor) {
                SESSIONS.remove(session.subLevelId);
                CompressionSyncPayload.sendRelease(subLevel);
            } else {
                session.lastHeldTick = now;
                return true;
            }
            session = null;
        } else if (session != null) {
            return false;
        }

        final int acquireTicks = instant
                ? INSTANT_ACQUIRE_TICKS
                : estimateAcquireTicks(subLevel);
        final int airCost = player.isCreative() ? 0 : estimateAirCost(subLevel);

        final boolean growing = growingIntent;
        final int ceiling = player.isCreative()
                ? PocketSized.MAX_COMPRESSED_BLOCKS
                : SURVIVAL_BLOCK_LIMIT;

        int cellLimit = 0;
        if (!growing) {
            final CompressionBlacklist.Result blocked = CompressionBlacklist.find(subLevel, now);
            if (blocked.blocked()) {
                player.displayClientMessage(Component.literal(blocked.message()), true);
                return false;
            }

            final int blocks = PocketMetrics.measureForCompression(subLevel, now).blocks();
            if (blocks > ceiling) {
                cellLimit = ceiling;
                player.displayClientMessage(Component.literal(
                        "Exceeds maximum block count (" + blocks + " / " + ceiling + ")"), true);
            }
        }

        ManualScaleOverride.engage(subLevel, now);

        session = new Session(id, player.getUUID(), hitLocalPos.immutable(), acquireTicks, airCost, floor, now);
        session.blocked = cellLimit > 0;
        session.hand = hand;
        session.propagateJoints = propagateJoints;
        SESSIONS.put(id, session);

        CompressionSyncPayload.sendBegin(
                subLevel, player, hitLocalPos, acquireTicks, !instant, growing, cellLimit);
        return true;
    }

    public static void instant(
            final ServerPlayer player,
            final ServerSubLevel subLevel,
            final BlockPos hitLocalPos,
            final CompressionStage requested,
            final boolean propagateJoints
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved() || requested == null) return;

        final UUID id = subLevel.getUniqueId();
        if (id == null) return;

        final CompressionStage current = ScaleState.getStage(subLevel);
        final long now = player.level().getGameTime();

        if (requested.depth() > current.depth()) {
            final CompressionBlacklist.Result blocked = CompressionBlacklist.find(subLevel, now);
            if (blocked.blocked()) {
                player.displayClientMessage(Component.literal(blocked.message()), true);
                return;
            }
        }

        ManualScaleOverride.engage(subLevel, now);
        ScaleController.forceStage(subLevel, requested, now, null, propagateJoints);

        final Session session = new Session(
                id, player.getUUID(), hitLocalPos.immutable(),
                INSTANT_ACQUIRE_TICKS, 0, requested, now
        );
        session.autoRelease = true;
        session.uniformSteps = true;

        session.directDrive = true;
        SESSIONS.put(id, session);

        CompressionSyncPayload.sendBegin(
                subLevel, player, hitLocalPos, INSTANT_ACQUIRE_TICKS, false,
                requested.depth() < current.depth(), 0);
    }

    public static boolean renew(final ServerPlayer player, final CompressionStage goal) {
        if (player == null) return false;

        final long now = player.level().getGameTime();
        boolean held = false;
        boolean sampledAim = false;
        UUID aimedSubLevelId = null;

        final Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Session session = iterator.next().getValue();
            if (!session.holder.equals(player.getUUID())) continue;

            if (session.autoRelease) continue;

            if (goal != null && session.floor != goal) {
                iterator.remove();

                final ServerLevel level = session.level != null ? session.level : player.serverLevel();
                CompressionSyncPayload.sendRelease(level, session.subLevelId);
                continue;
            }

            if (!session.sealed && !sampledAim) {
                final CompressionTargeting.Target target = CompressionTargeting.find(player, AIM_RANGE);
                aimedSubLevelId = target == null ? null : target.subLevel().getUniqueId();
                sampledAim = true;
            }

            session.illuminated = session.sealed || session.subLevelId.equals(aimedSubLevelId);
            session.lastHeldTick = now;
            ManualScaleOverride.sustain(session.subLevelId, now);
            held = true;
        }

        return held;
    }

    public static boolean isHeld(final UUID subLevelId) {
        if (subLevelId == null) return false;
        for (final Session session : SESSIONS.values()) {
            if (subLevelId.equals(session.subLevelId)) return true;
        }
        return false;
    }

    public static UUID lockedSubLevelId(final ServerPlayer player) {
        if (player == null) return null;
        for (final Session session : SESSIONS.values()) {
            if (session.holder.equals(player.getUUID())) return session.subLevelId;
        }
        return null;
    }

    public static ServerSubLevel lockedSubLevel(final ServerPlayer player) {
        if (player == null) return null;
        for (final Session session : SESSIONS.values()) {
            if (!session.holder.equals(player.getUUID())) continue;
            final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(player.level());
            if (container == null) return null;
            final var found = container.getSubLevel(session.subLevelId);
            if (found instanceof final ServerSubLevel subLevel && !subLevel.isRemoved()) return subLevel;
            return null;
        }
        return null;
    }

    public static void release(final ServerPlayer player, final UUID subLevelId) {
        final Session session = SESSIONS.get(subLevelId);
        if (session == null) return;
        if (player != null && !session.holder.equals(player.getUUID())) return;
        end(session, true);
    }

    public static void releaseAll(final ServerPlayer player) {
        if (player == null) return;
        for (final Session session : SESSIONS.values()) {
            if (session.holder.equals(player.getUUID())) end(session, true);
        }
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;

        final Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Session session = iterator.next().getValue();

            final ServerPlayer holder = event.getServer().getPlayerList().getPlayer(session.holder);
            final ServerSubLevel subLevel = findSubLevel(event.getServer(), session);

            if (holder == null || subLevel == null || subLevel.isRemoved()) {
                iterator.remove();
                if (subLevel != null) CompressionSyncPayload.sendRelease(subLevel);
                continue;
            }

            final long now = holder.level().getGameTime();

            if (!session.autoRelease && now - session.lastHeldTick > HOLD_GRACE_TICKS) {
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
            final Session session,
            final ServerPlayer holder,
            final ServerSubLevel subLevel
    ) {
        if (session.blocked) return true;

        if (!session.sealed) {
            if (!session.autoRelease && !session.illuminated) return true;

            session.age++;
            if (session.airCost > 0 && !drawAir(session, holder)) {
                holder.displayClientMessage(Component.literal("Backtank empty"), true);
                return false;
            }

            if (session.age < session.acquireTicks) return true;

            session.sealed = true;
            session.illuminated = true;
            session.sinceStep = Integer.MAX_VALUE / 2;
            return true;
        }

        final CompressionStage current = ScaleState.getStage(subLevel);
        if (current.depth() == session.floor.depth()) {
            return !session.autoRelease;
        }

        if (session.directDrive) return true;

        session.sinceStep++;
        final int delay = stepDelay(session, current);

        if (!session.pulsed && session.sinceStep >= Math.max(0, delay - PULSE_LEAD_TICKS)) {
            CompressionSyncPayload.sendPulse(subLevel, session.holder);
            session.pulsed = true;
        }

        if (session.sinceStep < delay) return true;

        final int direction = session.floor.depth() > current.depth() ? 1 : -1;
        final CompressionStage next = CompressionStage.fromDepth(current.depth() + direction);

        ScaleController.forceStage(
                subLevel,
                next,
                subLevel.getLevel().getGameTime(),
                null,
                session.propagateJoints
        );
        session.sinceStep = 0;
        session.pulsed = false;
        session.steps++;
        return true;
    }

    private static int stepDelay(final Session session, final CompressionStage current) {
        if (session.uniformSteps) return CREATIVE_STEP_TICKS;

        final int base = Math.round(STEP_BASE_TICKS * (1.0F + session.steps * STEP_GROWTH));
        final int direction = session.floor.depth() > current.depth() ? 1 : -1;
        final boolean isFinalStep = current.depth() + direction == session.floor.depth();
        return isFinalStep ? base + FINAL_STEP_EXTRA_TICKS : base;
    }

    private static void end(final Session session, final boolean notify) {
        SESSIONS.remove(session.subLevelId);
        if (!notify) return;

        final ServerLevel level = session.level;
        if (level == null) return;
        CompressionSyncPayload.sendRelease(level, session.subLevelId);
    }

    public static int estimateAcquireTicks(final ServerSubLevel subLevel) {
        final var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return MIN_ACQUIRE_TICKS;

        final double span = (bounds.maxX() - bounds.minX() + 1)
                + (bounds.maxY() - bounds.minY() + 1)
                + (bounds.maxZ() - bounds.minZ() + 1);

        final int ticks = (int) Math.round(span / SPREAD_PER_TICK);
        return Math.max(MIN_ACQUIRE_TICKS, Math.min(MAX_ACQUIRE_TICKS, ticks));
    }

    private static int estimateAirCost(final ServerSubLevel subLevel) {
        final var tracker = subLevel.getMassTracker();
        final double mass = tracker == null ? 0.0D : tracker.getMass();
        if (!Double.isFinite(mass) || mass <= 0.0D) return MIN_ACQUIRE_AIR;

        final int cost = (int) Math.round(mass / MASS_PER_AIR_UNIT);
        return Math.max(MIN_ACQUIRE_AIR, Math.min(MAX_ACQUIRE_AIR, cost));
    }

    private static boolean drawAir(final Session session, final ServerPlayer player) {
        session.airDebt += session.airCost / (float) Math.max(1, session.acquireTicks);

        int whole = (int) session.airDebt;
        if (whole <= 0) return true;
        session.airDebt -= whole;

        while (whole > 0) {
            final List<ItemStack> tanks = BacktankUtil.getAllWithAir(player);
            if (tanks.isEmpty()) break;

            final ItemStack tank = tanks.get(0);
            final int available = BacktankUtil.getAir(tank);
            if (available <= 0) break;

            final int spend = Math.min(available, whole);
            BacktankUtil.consumeAir(player, tank, spend);
            whole -= spend;
        }

        if (whole <= 0) return true;
        return payWithDurability(session, player, whole);
    }

    private static boolean payWithDurability(
            final Session session,
            final ServerPlayer player,
            final int shortfall
    ) {
        final ItemStack gun = player.getItemInHand(session.hand);
        if (!gun.isDamageableItem()) return false;

        final int damage = Math.max(1, shortfall / AIR_PER_DURABILITY);
        if (gun.getMaxDamage() - gun.getDamageValue() <= damage) {
            player.displayClientMessage(Component.literal("No pressure left"), true);
            return false;
        }

        gun.hurtAndBreak(damage, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return true;
    }

    private static ServerSubLevel findSubLevel(
            final net.minecraft.server.MinecraftServer server,
            final Session session
    ) {
        if (session.level != null && !session.level.isClientSide()) {
            final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(session.level);
            if (container != null) {
                final var found = container.getSubLevel(session.subLevelId);
                if (found instanceof final ServerSubLevel serverSubLevel) return serverSubLevel;
            }
        }

        for (final ServerLevel level : server.getAllLevels()) {
            final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            final var found = container.getSubLevel(session.subLevelId);
            if (found instanceof final ServerSubLevel serverSubLevel) {
                session.level = level;
                return serverSubLevel;
            }
        }
        return null;
    }

    private static final class Session {
        private final UUID subLevelId;
        private final UUID holder;
        private final BlockPos hitLocalPos;
        private final int acquireTicks;
        private final int airCost;

        private ServerLevel level;
        private CompressionStage floor;
        private long lastHeldTick;
        private int age;
        private boolean sealed;
        private int sinceStep;
        private int steps;
        private float airDebt;
        private boolean autoRelease;

        private boolean uniformSteps;

        private boolean directDrive;
        private boolean pulsed;
        private boolean blocked;
        private boolean illuminated = true;
        private boolean propagateJoints = true;
        private net.minecraft.world.InteractionHand hand =
                net.minecraft.world.InteractionHand.MAIN_HAND;

        private Session(
                final UUID subLevelId,
                final UUID holder,
                final BlockPos hitLocalPos,
                final int acquireTicks,
                final int airCost,
                final CompressionStage floor,
                final long now
        ) {
            this.subLevelId = subLevelId;
            this.holder = holder;
            this.hitLocalPos = hitLocalPos;
            this.acquireTicks = acquireTicks;
            this.airCost = airCost;
            this.floor = floor;
            this.lastHeldTick = now;
        }
    }
}
