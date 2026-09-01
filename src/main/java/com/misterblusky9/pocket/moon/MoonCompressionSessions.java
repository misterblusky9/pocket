package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.advancements.AdvancementHolder;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "pocket")
public final class MoonCompressionSessions {
    private static final int ACQUIRE_TICKS = 70;
    private static final int INSTANT_ACQUIRE_TICKS = 3;
    private static final int STEP_BASE_TICKS = 14;
    private static final float STEP_GROWTH = 0.85F;
    private static final int FINAL_STEP_EXTRA_TICKS = 22;
    private static final int PULSE_LEAD_TICKS = 10;
    private static final int HOLD_GRACE_TICKS = 3;
    private static final int AIR_COST = 16;
    private static final int AIR_PER_DURABILITY = 12;
    private static final int REBOUND_SETTLE_TICKS = 9;
    private static final int REBOUND_HALF_TICKS = 120;
    private static final int REBOUND_QUARTER_TICKS = 100;
    private static final int REBOUND_EIGHTH_TICKS = 80;
    private static final int REBOUND_SIXTEENTH_TICKS = 200;
    private static final float NEFARIO_REGROWTH_MID_SCALE =
            (float) ((CompressionStage.SIXTEENTH.scale() + CompressionStage.NORMAL.scale()) * 0.5D);

    private static Session session;
    private static MinecraftServer reboundServer;
    private static long reboundAt = Long.MIN_VALUE;
    private static boolean reboundActive;
    private static float reboundSurfaceX;
    private static float reboundSurfaceZ;
    private static UUID reboundOwner;
    private static boolean reboundAdvancementAwarded;

    public static boolean hold(
            final ServerPlayer player,
            final CompressionStage floor,
            final InteractionHand hand,
            final MoonTargeting.Hit hit
    ) {
        if (player == null || floor == null || hit == null) return false;
        if (reboundActive && reboundServer == player.serverLevel().getServer()) return true;
        final long now = player.level().getGameTime();
        refreshNefarioTimer(player.serverLevel().getServer(), floor, now);

        if (session != null) {
            if (!session.holder.equals(player.getUUID())) return false;
            if (session.floor != floor || session.autoRelease) {
                end(session, true);
            } else {
                session.lastHeldTick = now;
                return true;
            }
        }

        final CompressionStage current = MoonScale.stage(player.serverLevel().getServer());
        final boolean growing = floor.depth() < current.depth();
        if (growing) cancelRebound();
        session = new Session(
                player.serverLevel().getServer(),
                player.getUUID(),
                floor,
                hand,
                player.isCreative() ? 0 : AIR_COST,
                now,
                ACQUIRE_TICKS,
                hit.surfaceX(),
                hit.surfaceZ()
        );
        MoonScaleNetwork.broadcastEffectBegin(
                growing,
                false,
                ACQUIRE_TICKS,
                hit.surfaceX(),
                hit.surfaceZ()
        );
        return true;
    }

    public static boolean renew(final ServerPlayer player, final CompressionStage floor) {
        if (player == null || session == null) return false;
        if (!session.holder.equals(player.getUUID()) || session.autoRelease) return false;
        if (floor != null && session.floor != floor) {
            end(session, true);
            return false;
        }
        final long now = player.level().getGameTime();
        refreshNefarioTimer(player.serverLevel().getServer(), floor, now);
        session.lastHeldTick = now;
        return true;
    }

    public static void instant(
            final ServerPlayer player,
            final CompressionStage stage,
            final MoonTargeting.Hit hit
    ) {
        if (player == null || stage == null || hit == null) return;
        if (reboundActive && reboundServer == player.serverLevel().getServer()) return;
        if (session != null) end(session, true);

        final MinecraftServer server = player.serverLevel().getServer();
        refreshNefarioTimer(server, stage, player.level().getGameTime());
        final CompressionStage current = MoonScale.stage(server);
        final boolean growing = stage.depth() < current.depth();
        if (growing) cancelRebound();

        session = new Session(
                server,
                player.getUUID(),
                stage,
                player.getUsedItemHand(),
                0,
                player.level().getGameTime(),
                INSTANT_ACQUIRE_TICKS,
                hit.surfaceX(),
                hit.surfaceZ()
        );
        session.autoRelease = true;
        session.directDrive = true;

        MoonScaleNetwork.broadcastEffectBegin(
                growing,
                true,
                INSTANT_ACQUIRE_TICKS,
                hit.surfaceX(),
                hit.surfaceZ()
        );
        MoonScale.transitionTo(server, stage);
        if (stage.isCompressed()) {
            scheduleRebound(
                    session,
                    stage,
                    player.level().getGameTime(),
                    REBOUND_SETTLE_TICKS,
                    stage == CompressionStage.SIXTEENTH
            );
        }
    }

    public static void release(final ServerPlayer player) {
        if (session == null) return;
        if (player != null && !session.holder.equals(player.getUUID())) return;
        end(session, true);
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        if (reboundServer != null && reboundServer != event.getServer()) clearRebound();
        MoonScale.tick(event.getServer());
        tickRebound(event.getServer());

        final Session current = session;
        if (current == null) return;
        if (current.server != event.getServer()) {
            end(current, true);
            return;
        }

        final ServerPlayer holder = event.getServer().getPlayerList().getPlayer(current.holder);
        if (holder == null) {
            end(current, true);
            return;
        }

        final long now = holder.level().getGameTime();
        if (!current.autoRelease && now - current.lastHeldTick > HOLD_GRACE_TICKS) {
            end(current, true);
            return;
        }

        if (!tick(current, holder)) end(current, true);
    }

    private static boolean tick(final Session session, final ServerPlayer holder) {
        session.age++;

        if (!session.sealed) {
            if (session.airCost > 0 && !drawAir(session, holder)) {
                holder.displayClientMessage(Component.literal("Backtank empty"), true);
                return false;
            }

            if (session.age < session.acquireTicks) return true;

            session.sealed = true;
            session.sinceStep = Integer.MAX_VALUE / 2;
            return true;
        }

        final MinecraftServer server = holder.serverLevel().getServer();
        final CompressionStage current = MoonScale.stage(server);
        if (current.depth() == session.floor.depth() && !MoonScale.isTransitioning(server)) {
            if (current.isCompressed()) {
                scheduleRebound(
                        session,
                        current,
                        holder.level().getGameTime(),
                        0,
                        current == CompressionStage.SIXTEENTH && !session.directDrive && !session.autoRelease
                );
            }
            return !session.autoRelease;
        }

        if (session.directDrive) return true;

        session.sinceStep++;
        final int delay = stepDelay(session, current);

        if (!session.pulsed && session.sinceStep >= Math.max(0, delay - PULSE_LEAD_TICKS)) {
            MoonScaleNetwork.broadcastEffectPulse();
            session.pulsed = true;
        }

        if (session.sinceStep < delay || MoonScale.isTransitioning(server)) return true;

        final int direction = session.floor.depth() > current.depth() ? 1 : -1;
        final CompressionStage next = CompressionStage.fromDepth(current.depth() + direction);
        if (next == session.floor && next.isCompressed()) {
            scheduleRebound(
                    session,
                    next,
                    holder.level().getGameTime(),
                    REBOUND_SETTLE_TICKS,
                    next == CompressionStage.SIXTEENTH && !session.directDrive && !session.autoRelease
            );
        }
        MoonScale.transitionTo(server, next);
        session.sinceStep = 0;
        session.steps++;
        session.pulsed = false;
        return true;
    }


    private static void scheduleRebound(
            final Session source,
            final CompressionStage stage,
            final long now,
            final int settleTicks,
            final boolean awardNefarioPrinciple
    ) {
        if (reboundActive || reboundAt != Long.MIN_VALUE) return;
        reboundServer = source.server;
        reboundAt = now + Math.max(0, settleTicks) + reboundDelay(stage);
        reboundSurfaceX = source.surfaceX;
        reboundSurfaceZ = source.surfaceZ;
        reboundOwner = awardNefarioPrinciple && stage == CompressionStage.SIXTEENTH ? source.holder : null;
        reboundAdvancementAwarded = false;
    }

    private static void refreshNefarioTimer(
            final MinecraftServer server,
            final CompressionStage requestedStage,
            final long now
    ) {
        if (requestedStage != CompressionStage.SIXTEENTH) return;
        if (reboundActive || reboundAt == Long.MIN_VALUE || reboundServer != server) return;
        if (MoonScale.isTransitioning(server) || MoonScale.stage(server) != CompressionStage.SIXTEENTH) return;
        reboundAt = now + REBOUND_SIXTEENTH_TICKS;
        reboundAdvancementAwarded = false;
    }

    private static int reboundDelay(final CompressionStage stage) {
        return switch (stage) {
            case HALF -> REBOUND_HALF_TICKS;
            case QUARTER -> REBOUND_QUARTER_TICKS;
            case EIGHTH -> REBOUND_EIGHTH_TICKS;
            case SIXTEENTH -> REBOUND_SIXTEENTH_TICKS;
            case NORMAL -> 0;
        };
    }

    private static void tickRebound(final MinecraftServer server) {
        if (reboundServer != server) return;

        if (reboundActive) {
            if (reboundOwner != null
                    && !reboundAdvancementAwarded
                    && MoonScale.isTransitioning(server)
                    && MoonScale.get(server) >= NEFARIO_REGROWTH_MID_SCALE) {
                reboundAdvancementAwarded = awardNefarioPrinciple(server, reboundOwner);
            }

            if (!MoonScale.isTransitioning(server) && MoonScale.stage(server) == CompressionStage.NORMAL) {
                MoonScaleNetwork.broadcastEffectRelease();
                clearRebound();
            }
            return;
        }

        if (reboundAt == Long.MIN_VALUE) return;

        final long now = server.overworld().getGameTime();
        if (now < reboundAt) return;
        if (MoonScale.stage(server) == CompressionStage.NORMAL) {
            clearRebound();
            return;
        }

        if (session != null && session.server == server) end(session, true);
        reboundAt = Long.MIN_VALUE;
        reboundActive = true;
        MoonScaleNetwork.broadcastEffectBegin(
                true,
                true,
                INSTANT_ACQUIRE_TICKS,
                reboundSurfaceX,
                reboundSurfaceZ
        );
        MoonScale.transitionTo(server, CompressionStage.NORMAL);
    }

    private static boolean awardNefarioPrinciple(final MinecraftServer server, final UUID playerId) {
        final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return false;

        final AdvancementHolder advancement = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath("pocket", "nefario_principle")
        );
        if (advancement == null) return false;

        player.getAdvancements().award(advancement, "principle");
        return true;
    }

    private static void cancelRebound() {
        if (reboundActive) return;
        clearRebound();
    }

    private static void clearRebound() {
        reboundServer = null;
        reboundAt = Long.MIN_VALUE;
        reboundActive = false;
        reboundSurfaceX = 0.0F;
        reboundSurfaceZ = 0.0F;
        reboundOwner = null;
        reboundAdvancementAwarded = false;
    }

    private static int stepDelay(final Session session, final CompressionStage current) {
        final int base = Math.round(STEP_BASE_TICKS * (1.0F + session.steps * STEP_GROWTH));
        final int direction = session.floor.depth() > current.depth() ? 1 : -1;
        final boolean finalStep = current.depth() + direction == session.floor.depth();
        return finalStep ? base + FINAL_STEP_EXTRA_TICKS : base;
    }

    private static void end(final Session value, final boolean notify) {
        if (session == value) session = null;
        if (notify) MoonScaleNetwork.broadcastEffectRelease();
    }

    private static boolean drawAir(final Session session, final ServerPlayer player) {
        session.airDebt += session.airCost / (float) session.acquireTicks;

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

        gun.hurtAndBreak(damage, player, EquipmentSlot.MAINHAND);
        return true;
    }

    private static final class Session {
        private final MinecraftServer server;
        private final UUID holder;
        private final CompressionStage floor;
        private final InteractionHand hand;
        private final int airCost;
        private final int acquireTicks;
        private long lastHeldTick;
        private int age;
        private boolean sealed;
        private int sinceStep;
        private int steps;
        private float airDebt;
        private boolean autoRelease;
        private boolean directDrive;
        private boolean pulsed;
        private final float surfaceX;
        private final float surfaceZ;

        private Session(
                final MinecraftServer server,
                final UUID holder,
                final CompressionStage floor,
                final InteractionHand hand,
                final int airCost,
                final long lastHeldTick,
                final int acquireTicks,
                final float surfaceX,
                final float surfaceZ
        ) {
            this.server = server;
            this.holder = holder;
            this.floor = floor;
            this.hand = hand;
            this.airCost = airCost;
            this.lastHeldTick = lastHeldTick;
            this.acquireTicks = acquireTicks;
            this.surfaceX = surfaceX;
            this.surfaceZ = surfaceZ;
        }
    }

    private MoonCompressionSessions() {}
}
