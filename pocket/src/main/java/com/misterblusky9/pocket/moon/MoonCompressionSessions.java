package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.compression.BeamSession;
import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = "pocket")
public final class MoonCompressionSessions {
    private static final int ACQUIRE_TICKS = 70;
    private static final int INSTANT_ACQUIRE_TICKS = 3;
    private static final int ACQUIRE_LEVITITE = 2000;
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
            final MoonTargeting.Hit hit,
            final boolean growingIntent
    ) {
        if (player == null || floor == null || hit == null) return false;
        MoonLightLag.hold(player, floor, hand, hit, growingIntent);
        return true;
    }

    static boolean deliverHold(
            final ServerPlayer player,
            final CompressionStage floor,
            final InteractionHand hand,
            final MoonTargeting.Hit hit,
            final boolean growingIntent
    ) {
        if (player == null || floor == null || hit == null) return false;
        if (reboundActive && reboundServer == player.serverLevel().getServer()) return true;
        final long now = player.level().getGameTime();
        refreshNefarioTimer(player.serverLevel().getServer(), floor, now);

        if (session != null) {
            if (!session.beam.heldBy(player)) return false;
            if (session.floor != floor || session.autoRelease) {
                end(session, true);
            } else {
                session.beam.hold(now);
                return true;
            }
        }

        final boolean growing = growingIntent;
        if (growing) cancelRebound();
        final float levitite = player.isCreative() ? 0.0F : (float) ACQUIRE_LEVITITE / ACQUIRE_TICKS;
        session = new Session(
                player.serverLevel().getServer(),
                new BeamSession(player.getUUID(), hand, ACQUIRE_TICKS, levitite, now),
                floor,
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
        return MoonLightLag.renew(player, floor);
    }

    public static void instant(
            final ServerPlayer player,
            final CompressionStage stage,
            final MoonTargeting.Hit hit
    ) {
        if (player == null || stage == null || hit == null) return;
        MoonLightLag.instant(player, stage, hit);
    }

    static void deliverInstant(
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
                new BeamSession(player.getUUID(), player.getUsedItemHand(), INSTANT_ACQUIRE_TICKS, 0.0F,
                        player.level().getGameTime()),
                stage,
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
        MoonLightLag.release(player);
    }

    public static void reset() {
        MoonLightLag.clear(null);
        deliverRelease(null);
    }

    public static void abandon(final ServerPlayer player) {
        MoonLightLag.clear(player);
        deliverRelease(player);
    }

    static void deliverRelease(final ServerPlayer player) {
        if (session == null) return;
        if (player != null && !session.beam.heldBy(player)) return;
        end(session, true);
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        if (reboundServer != null && reboundServer != event.getServer()) clearRebound();
        MoonLightLag.tick(event.getServer());
        MoonScale.tick(event.getServer());
        tickRebound(event.getServer());

        final Session current = session;
        if (current == null) return;
        if (current.server != event.getServer()) {
            end(current, true);
            return;
        }

        final ServerPlayer holder = event.getServer().getPlayerList().getPlayer(current.beam.holder());
        if (holder == null) {
            end(current, true);
            return;
        }

        final long now = holder.level().getGameTime();
        if (!current.autoRelease && current.beam.expired(now)) {
            end(current, true);
            return;
        }

        if (!tick(current, holder)) end(current, true);
    }

    private static boolean tick(final Session session, final ServerPlayer holder) {
        final BeamSession beam = session.beam;
        if (!beam.sealed()) {
            if (!beam.acquire(holder)) {
                holder.displayClientMessage(Component.translatable("pocket.message.levitite_depleted"), true);
                return false;
            }
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

        final int direction = session.floor.depth() > current.depth() ? 1 : -1;
        final CompressionStage next = CompressionStage.fromDepth(current.depth() + direction);
        final BeamSession.Tick tick = beam.advance(BeamSession.Pace.HELD_BEAM, next == session.floor);
        if (tick.pulse()) MoonScaleNetwork.broadcastEffectPulse();
        if (!tick.step() || MoonScale.isTransitioning(server)) return true;

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
        beam.stepped();
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
        reboundOwner = awardNefarioPrinciple && stage == CompressionStage.SIXTEENTH ? source.beam.holder() : null;
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

    private static void end(final Session value, final boolean notify) {
        if (session == value) session = null;
        if (notify) MoonScaleNetwork.broadcastEffectRelease();
    }

    private static final class Session {
        private final MinecraftServer server;
        private final BeamSession beam;
        private final CompressionStage floor;
        private final float surfaceX;
        private final float surfaceZ;
        private boolean autoRelease;
        private boolean directDrive;

        private Session(
                final MinecraftServer server,
                final BeamSession beam,
                final CompressionStage floor,
                final float surfaceX,
                final float surfaceZ
        ) {
            this.server = server;
            this.beam = beam;
            this.floor = floor;
            this.surfaceX = surfaceX;
            this.surfaceZ = surfaceZ;
        }
    }

    private MoonCompressionSessions() {}
}
