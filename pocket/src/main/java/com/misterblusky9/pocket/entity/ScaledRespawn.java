package com.misterblusky9.pocket.entity;

import com.mojang.serialization.Codec;
import com.misterblusky9.pocket.compression.PersonalScale;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.misterblusky9.pocket.mixin.SableRespawnQueueMixin;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A spawn size belongs to the bed/anchor, not to the player's last life.
 * Spawn metadata is stored on the player, so Sable remains the owner of its
 * moving-sublevel tracking point and vanilla remains the owner of ordinary spawns.
 */
@EventBusSubscriber(modid = "pocket")
public final class ScaledRespawn {
    private static final String SPAWN = "PocketScaledSpawn";
    private static final String NEXT = "PocketNextRespawnScale";
    private static final String DIM = "Dimension";
    private static final String POS = "Position";
    private static final String CRAFT = "SubLevel";
    private static final String RATIO = "PlayerToBed";
    private static final String KIND = "Kind";
    private static final String BED = "bed";
    private static final String ANCHOR = "anchor";
    private static final double EPS = 1.0E-5D;

    // Sable unloads an unobserved craft while the player is on the death screen.
    // Its tracking point can survive that unload, but the bed blocks cannot be
    // inspected until it is reloaded. Hold only the craft with the saved spawn,
    // only from death through the first few ticks after respawn.
    private static final SubLevelLoadingTicketType<String> RESPAWN_HOLD_TICKET =
            SubLevelLoadingTicketType.create(ResourceLocation.fromNamespaceAndPath(
                    "pocket", "pending_respawn"), Codec.STRING);
    private static final Map<UUID, RespawnHold> RESPAWN_HOLDS = new HashMap<>();

    private static final class RespawnHold {
        final ServerLevel level;
        final ServerSubLevel craft;
        final String key;
        long releaseAt = Long.MAX_VALUE;

        RespawnHold(ServerLevel level, ServerSubLevel craft, String key) {
            this.level = level;
            this.craft = craft;
            this.key = key;
        }
    }

    // Sable 2.0.0 uses getType()/getKey(); 2.0.6 uses record accessors.
    // This bridge is loaded only by startup cleanup, not when the ticket type
    // is registered during mod construction or during death/respawn.
    private static final class TicketAccessors {
        static final Method TYPE = find("type", "getType");
        static final Method KEY = find("key", "getKey");

        private static Method find(String recordName, String getterName) {
            try {
                return SubLevelLoadingTicket.class.getMethod(recordName);
            } catch (NoSuchMethodException ignored) {
                try {
                    return SubLevelLoadingTicket.class.getMethod(getterName);
                } catch (NoSuchMethodException e) {
                    PocketTrace.logger().warn("[PocketScale] Unsupported Sable ticket API: neither {} nor {} exists; stale respawn tickets cannot be cleared",
                            recordName, getterName);
                    return null;
                }
            }
        }
    }

    // Set-spawn is confirmed after the block has actually handled the click.
    // Checking the saved position alone is not confirmation: it may have been
    // this very bed before the player right-clicked it with a tool.
    private static final Map<UUID, PendingSpawn> PENDING = new HashMap<>();
    // Three ticks of server-side observation after death respawn. Never alters
    // the player's size, position, or the Sable attachment.
    private static final Map<UUID, Integer> RESPAWN_WATCH = new HashMap<>();

    private static final class PendingSpawn {
        final ResourceKey<Level> dimension;
        final BlockPos clicked;
        final String kind;
        final double playerScale;
        final double bedScale;
        final long expires;
        final UUID oldTrackingPoint;
        final UUID craftId;
        final BlockPos oldPosition;
        final ResourceKey<Level> oldDimension;
        final CompoundTag previous;
        boolean setRespawnCalled;

        PendingSpawn(ServerPlayer player, ServerLevel level, BlockPos clicked, String kind,
                     double playerScale, double bedScale, SubLevel craft) {
            this.dimension = level.dimension();
            this.clicked = clicked.immutable();
            this.kind = kind;
            this.playerScale = playerScale;
            this.bedScale = bedScale;
            this.expires = level.getGameTime() + 5;
            this.oldTrackingPoint = ((ServerPlayerRespawnExtension) player).sable$getRespawnPoint();
            this.craftId = craft == null ? null : craft.getUniqueId();
            this.oldPosition = player.getRespawnPosition();
            this.oldDimension = player.getRespawnDimension();
            this.previous = player.getPersistentData().getCompound(SPAWN).copy();
        }
    }

    // These are adjacent-space preferences, not world-coordinate offsets. Sable
    // rotates and scales the entire room when we project the final location.
    private static final int[][] OFFSETS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 },
            { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 },
            { 2, 1 }, { 2, -1 }, { -2, 1 }, { -2, -1 },
            { 1, 2 }, { -1, 2 }, { 1, -2 }, { -1, -2 }
    };

    private ScaledRespawn() {}

    /**
     * Calling this during mod construction initializes this class, registering
     * RESPAWN_HOLD_TICKET before Sable deserializes saved tickets.
     */
    public static void registerRespawnTicket() {
        // The static field initializer performs the registration.
    }

    /** Keep a verified bed's craft loaded while its player is dead. */
    @SubscribeEvent
    public static void holdRespawnCraft(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID playerId = player.getUUID();
        releaseRespawnHold(playerId);
        CompoundTag spawn = player.getPersistentData().getCompound(SPAWN);
        if (!spawn.hasUUID(CRAFT) || !spawn.contains(DIM, Tag.TAG_STRING)
                || !spawn.contains(POS, Tag.TAG_LONG)) return;
        BlockPos pos = player.getRespawnPosition();
        if (pos == null || !pos.equals(BlockPos.of(spawn.getLong(POS)))
                || !player.getRespawnDimension().location().toString().equals(spawn.getString(DIM))) return;
        ServerLevel level = player.server.getLevel(player.getRespawnDimension());
        if (level == null || !level.hasChunkAt(pos)) return;
        SubLevel craft = craftAtBed(level, pos);
        if (!(craft instanceof ServerSubLevel serverCraft) || craft.isRemoved()
                || !craft.getUniqueId().equals(spawn.getUUID(CRAFT))) return;
        BlockState state = level.getBlockState(pos);
        String blockKind = kind(state);
        if (blockKind == null || !blockKind.equals(spawn.getString(KIND))) return;
        if (BED.equals(blockKind) && !validBed(level, pos, state)) return;
        if (ANCHOR.equals(blockKind) && state.getValue(RespawnAnchorBlock.CHARGE) == 0) return;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;
        String key = playerId.toString();
        container.addForceLoadTicket(serverCraft, RESPAWN_HOLD_TICKET, key);
        RESPAWN_HOLDS.put(playerId, new RespawnHold(level, serverCraft, key));
        PocketTrace.scale("Scaled respawn: holding craft={} from death until respawn; bed={} loaded={}",
                craft.getUniqueId(), pos, level.hasChunkAt(pos));
    }

    private static void releaseRespawnHold(UUID playerId) {
        RespawnHold hold = RESPAWN_HOLDS.remove(playerId);
        if (hold == null) return;
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(hold.level);
        if (container != null) container.removeForceLoadTicket(hold.craft, RESPAWN_HOLD_TICKET, hold.key);
        PocketTrace.scale("Scaled respawn: released temporary craft hold for {} craft={}",
                playerId, hold.craft.getUniqueId());
    }

    @SubscribeEvent
    public static void releaseRespawnHoldAfterTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RespawnHold hold = RESPAWN_HOLDS.get(player.getUUID());
        if (hold != null && hold.level.getGameTime() >= hold.releaseAt) {
            releaseRespawnHold(player.getUUID());
        }
    }

    // Sable serializes loading tickets. Remove any leftover temporary tickets
    // after a crash/restart, rather than keeping a sublevel loaded forever.
    @SubscribeEvent
    public static void clearStaleRespawnHolds(ServerStartedEvent event) {
        if (TicketAccessors.TYPE == null || TicketAccessors.KEY == null) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (var entry : new ArrayList<>(container.getAllTickets().entrySet())) {
                for (var ticket : new ArrayList<>(entry.getValue().tickets())) {
                    final Object type;
                    final Object key;
                    try {
                        type = TicketAccessors.TYPE.invoke(ticket);
                        if (!RESPAWN_HOLD_TICKET.equals(type)) continue;
                        key = TicketAccessors.KEY.invoke(ticket);
                    } catch (ReflectiveOperationException | IllegalArgumentException e) {
                        // Unknown ticket implementations must not cause us to
                        // remove other mods' tickets or abort world startup.
                        PocketTrace.logger().warn("[PocketScale] Could not inspect Sable respawn ticket for startup cleanup", e);
                        continue;
                    }
                    SubLevel craft = container.getSubLevel(entry.getKey());
                    if (craft instanceof ServerSubLevel serverCraft && key instanceof String playerKey) {
                        container.removeForceLoadTicket(serverCraft, RESPAWN_HOLD_TICKET, playerKey);
                    } else {
                        entry.getValue().tickets().remove(ticket);
                        SubLevelTicketsSavedData.getOrLoad(level).setDirty();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void releaseRespawnHoldsOnStop(ServerStoppingEvent event) {
        for (UUID playerId : new ArrayList<>(RESPAWN_HOLDS.keySet())) releaseRespawnHold(playerId);
    }

    /** The interaction hook runs before vanilla consumes fuel or attempts to sleep. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interacted(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // A PSC (or another held priority tool) owns this click. The bed must
        // not silently set spawn while the player is trying to resize.
        if (event.getUseBlock() == TriState.FALSE || HeldInteractionPriority.claimsBlock(
                player, event.getItemStack(), event.getLevel(), event.getPos())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        String kind = kind(state);
        if (kind == null) return;
        // Let beds explode normally in forbidden dimensions. Glowstone still
        // charges anchors; the spawn-setting method is guarded separately.
        if (BED.equals(kind) && !event.getLevel().dimensionType().bedWorks()) return;
        if (ANCHOR.equals(kind) && !RespawnAnchorBlock.canSetSpawn(event.getLevel())) return;
        if (ANCHOR.equals(kind) && event.getItemStack().is(Blocks.GLOWSTONE.asItem())
                && state.getValue(RespawnAnchorBlock.CHARGE) < 4) return;
        if (fits(player, event.getLevel(), event.getPos())) {
            double playerScale = Pym.entities().scaleOf(player);
            double bedScale = bedSize(event.getLevel(), event.getPos());
            if (ScaleBounds.isValid(playerScale) && ScaleBounds.isValid(bedScale)
                    && event.getLevel() instanceof ServerLevel level) {
                PENDING.put(player.getUUID(), new PendingSpawn(player, level, event.getPos(),
                        kind, playerScale, bedScale, craftAtBed(level, event.getPos())));
            }
            return;
        }
        PENDING.remove(player.getUUID());
        tooSmall(player, kind);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Confirms the bed/anchor was used, not merely right-clicked. */
    @SubscribeEvent
    public static void confirmClickedSpawn(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingSpawn pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        ServerLevel level = player.server.getLevel(pending.dimension);
        if (level == null || !player.level().dimension().equals(pending.dimension)
                || player.level().getGameTime() > pending.expires) {
            PENDING.remove(player.getUUID());
            return;
        }
        BlockPos actual = player.getRespawnPosition();
        if (actual == null || !player.getRespawnDimension().equals(pending.dimension)
                || !matchesClickedSpawn(level, pending.clicked, actual, pending.kind)) return;

        UUID newPoint = ((ServerPlayerRespawnExtension) player).sable$getRespawnPoint();
        // Sable changes the tracking-point UUID each time it actually sets a
        // craft spawn. On a failed click, the old position may still match.
        boolean tracked = pending.craftId == null
                || (newPoint != null && !newPoint.equals(pending.oldTrackingPoint));
        if (!tracked || (!pending.setRespawnCalled && pending.craftId == null)) {
            PocketTrace.scale("Scaled respawn: ignored {} click (setCalled={}, oldPoint={}, newPoint={}, craft={})",
                    pending.kind, pending.setRespawnCalled, pending.oldTrackingPoint, newPoint, pending.craftId);
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.remove(player.getUUID());
        if (!ScaledSpawnMath.fits(pending.playerScale, pending.bedScale)) return;
        SubLevel currentCraft = craftAtBed(level, actual);
        if (pending.craftId != null && (currentCraft == null
                || !pending.craftId.equals(currentCraft.getUniqueId()))) return;
        if (!pending.kind.equals(kind(level.getBlockState(actual)))) return;

        recordSpawn(player, level, actual, pending.kind, pending.playerScale, pending.bedScale);
        double ratio = pending.playerScale / pending.bedScale;
        boolean sameBed = pending.oldPosition != null && pending.dimension.equals(pending.oldDimension)
                && matchesClickedSpawn(level, pending.clicked, pending.oldPosition, pending.kind);
        boolean changedRatio = !pending.previous.contains(RATIO, Tag.TAG_ANY_NUMERIC)
                || Math.abs(pending.previous.getDouble(RATIO) - ratio) > EPS;
        if (sameBed && changedRatio) {
            player.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
        }
        PocketTrace.scale("Scaled respawn: confirmed {} at {} with player={} bed={} trackingPoint={}",
                pending.kind, actual, pending.playerScale, pending.bedScale, newPoint);
    }

    @SubscribeEvent
    public static void clearPendingOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        RESPAWN_WATCH.remove(event.getEntity().getUUID());
        releaseRespawnHold(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void traceAfterRespawn(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Integer ticks = RESPAWN_WATCH.get(player.getUUID());
        if (ticks == null) return;
        PocketTrace.scale("Scaled respawn: watch tick={} alive={} pos={} scale={} savedPos={} tracking={}",
                4 - ticks, player.isAlive(), player.position(), Pym.entities().scaleOf(player),
                player.getRespawnPosition(), ((ServerPlayerRespawnExtension) player).sable$getRespawnPoint());
        if (ticks <= 1) RESPAWN_WATCH.remove(player.getUUID());
        else RESPAWN_WATCH.put(player.getUUID(), ticks - 1);
    }

    private static boolean matchesClickedSpawn(ServerLevel level, BlockPos clicked,
                                               BlockPos actual, String kind) {
        if (!kind.equals(kind(level.getBlockState(actual)))) return false;
        if (clicked.equals(actual)) return true;
        if (!BED.equals(kind)) return false;
        BlockState clickedState = level.getBlockState(clicked);
        return clickedState.getBlock() instanceof BedBlock
                && clicked.relative(BedBlock.getConnectedDirection(clickedState)).equals(actual);
    }

    /** Called before Sable's cancellable setRespawnPosition HEAD injection. */
    public static boolean settingSpawn(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
        if (pos == null) {
            PENDING.remove(player.getUUID());
            player.getPersistentData().remove(SPAWN);
            return true;
        }
        ServerLevel level = player.server.getLevel(dimension);
        if (level == null) {
            player.getPersistentData().remove(SPAWN);
            return true;
        }
        BlockState state = level.getBlockState(pos);
        String kind = kind(state);
        if (kind == null) {
            // /spawnpoint and other forced/custom spawns are vanilla-owned.
            PENDING.remove(player.getUUID());
            player.getPersistentData().remove(SPAWN);
            return true;
        }
        if (!fits(player, level, pos)) {
            PENDING.remove(player.getUUID());
            tooSmall(player, kind);
            return false;
        }
        PendingSpawn pending = PENDING.get(player.getUUID());
        if (pending != null && pending.dimension.equals(dimension)
                && pending.kind.equals(kind)
                && matchesClickedSpawn(level, pending.clicked, pos, kind)) {
            // Confirm only after Sable has installed a new tracking point.
            // This avoids retaining spawn ratios from interactions that failed.
            pending.setRespawnCalled = true;
            return true;
        }
        // Commands and mod integrations can call this without a right-click.
        // Keep their existing behavior without pretending a bed was used.
        double bedScale = bedSize(level, pos);
        double playerScale = Pym.entities().scaleOf(player);
        if (ScaleBounds.isValid(playerScale) && ScaleBounds.isValid(bedScale))
            recordSpawn(player, level, pos, kind, playerScale, bedScale);
        else player.getPersistentData().remove(SPAWN);
        return true;
    }

    private static void recordSpawn(ServerPlayer player, ServerLevel level, BlockPos pos,
                                    String kind, double playerSize, double bedSize) {
        CompoundTag spawn = new CompoundTag();
        spawn.putString(DIM, level.dimension().location().toString());
        spawn.putLong(POS, pos.asLong());
        spawn.putString(KIND, kind);
        spawn.putDouble(RATIO, playerSize / bedSize);
        SubLevel sublevel = craftAtBed(level, pos);
        if (sublevel != null && !sublevel.isRemoved()) spawn.putUUID(CRAFT, sublevel.getUniqueId());
        player.getPersistentData().put(SPAWN, spawn);
        PocketTrace.scale("Scaled respawn: saved {} at {} ratio={} craft={}",
                kind, pos, playerSize / bedSize, sublevel == null ? "none" : sublevel.getUniqueId());
    }

    /**
     * Sable/vanilla resolve the saved spawn before this event. Don't turn their
     * successful result into a missing-bed respawn just because our optional
     * adjacent-floor search cannot find a better location.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void selectRespawn(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight()) return;
        ServerPlayer old = (ServerPlayer) event.getEntity();
        CompoundTag data = old.getPersistentData();
        data.putDouble(NEXT, 1.0D);
        CompoundTag spawn = data.getCompound(SPAWN);
        var initialQueue = ((ServerPlayerRespawnExtension) old).sable$getQueuedFreeze();
        PocketTrace.scale("Scaled respawn: BEGIN player={} actualScale={} savedPos={} dimension={} sablePoint={} queuedCraft={} originalMissing={} stored={}",
                old.getUUID(), Pym.entities().scaleOf(old), old.getRespawnPosition(),
                old.getRespawnDimension().location(),
                ((ServerPlayerRespawnExtension) old).sable$getRespawnPoint(),
                initialQueue == null ? "none" : initialQueue.first(),
                event.getOriginalDimensionTransition().missingRespawnBlock(),
                data.contains(SPAWN, Tag.TAG_COMPOUND) ? spawn : "none");
        if (!spawn.contains(DIM, Tag.TAG_STRING) || !spawn.contains(POS, Tag.TAG_LONG)
                || !spawn.contains(RATIO, Tag.TAG_ANY_NUMERIC)) {
            // A legacy Sable spawn has no recorded size ratio. Keeping a 1x
            // player in a miniature room would be unsafe; ordinary beds and
            // full-sized craft still use their existing spawn.
            PocketTrace.scale("Scaled respawn: no stored bed-to-player ratio for saved position {}",
                    old.getRespawnPosition());
            guardUnscaledCraftRespawn(event, old);
            return;
        }

        BlockPos pos = BlockPos.of(spawn.getLong(POS));
        ServerLevel level = old.server.getLevel(old.getRespawnDimension());
        if (level == null || !old.getRespawnDimension().location().toString().equals(spawn.getString(DIM))
                || !pos.equals(old.getRespawnPosition())) {
            PocketTrace.scale("Scaled respawn: saved position differs; record={} current={}",
                    pos, old.getRespawnPosition());
            guardUnscaledCraftRespawn(event, old);
            return;
        }

        boolean originallyMissing = event.getOriginalDimensionTransition().missingRespawnBlock();
        // Only claim that a bed was destroyed if the block can actually be
        // inspected. An unloaded Sable holding chunk is not a destroyed bed.
        if (!level.hasChunkAt(pos)) {
            PocketTrace.scale("Scaled respawn: saved bed at {} is not loaded", pos);
            guardUnscaledCraftRespawn(event, old);
            return;
        }
        BlockState state = level.getBlockState(pos);
        String kind = kind(state);
        if (kind == null || !kind.equals(spawn.getString(KIND))) {
            PocketTrace.scale("Scaled respawn: saved bed/anchor at {} is gone or replaced", pos);
            data.remove(SPAWN);
            if (spawn.hasUUID(CRAFT) && !originallyMissing) missing(event, old);
            else guardUnscaledCraftRespawn(event, old);
            return;
        }

        SubLevel containing = craftAtBed(level, pos);
        var queued = ((ServerPlayerRespawnExtension) old).sable$getQueuedFreeze();
        UUID expected = spawn.hasUUID(CRAFT) ? spawn.getUUID(CRAFT) : null;
        // Sable's queued anchor is authoritative if an older saved record
        // lacks its optional craft UUID.
        if (expected == null && queued != null && containing != null
                && queued.first().equals(containing.getUniqueId())) expected = queued.first();
        SubLevel craft = expected == null ? null : containing;
        boolean scaledCraft = expected != null;
        PocketTrace.scale("Scaled respawn: LOOKUP pos={} expectedCraft={} foundCraft={} queuedCraft={} originalMissing={} loaded={}",
                pos, expected, containing == null ? "none" : containing.getUniqueId(),
                queued == null ? "none" : queued.first(), originallyMissing, level.hasChunkAt(pos));
        if (scaledCraft && (craft == null || craft.isRemoved() || !expected.equals(craft.getUniqueId()))) {
            // A tracked Sable sublevel can temporarily be unloaded during
            // position resolution. Do not destroy its respawn point based on
            // an inconclusive lookup. A genuinely missing spawn is already
            // handled by vanilla/Sable and stays at the safe 1x fallback.
            PocketTrace.scale("Scaled respawn: sublevel for bed at {} unresolved", pos);
            guardUnscaledCraftRespawn(event, old);
            return;
        }
        if (scaledCraft && !validBed(level, pos, state)) {
            PocketTrace.scale("Scaled respawn: bed at {} has a missing half", pos);
            missing(event, old);
            return;
        }
        // Sable's tracking point skips vanilla's block validity checks.
        if (scaledCraft && ANCHOR.equals(kind) && state.getValue(RespawnAnchorBlock.CHARGE) <= 0) {
            PocketTrace.scale("Scaled respawn: anchor at {} is out of charges", pos);
            missing(event, old);
            return;
        }

        double roomScale = bedSize(level, pos);
        double ratio = spawn.getDouble(RATIO);
        double size = ScaledSpawnMath.restore(roomScale, ratio);
        PocketTrace.scale("Scaled respawn: {} at {} craft={} room={} ratio={} restored={} missing={}",
                kind, pos, expected, roomScale, ratio, size, originallyMissing);
        if (!ScaleBounds.isValid(ratio) || !ScaleBounds.isValid(roomScale)
                || ratio > ScaledSpawnMath.MAX_PLAYER_TO_BED * (1.0D + EPS)
                || !ScaledSpawnMath.fits(size, roomScale)) {
            PocketTrace.scale("Scaled respawn: saved scale relationship at {} is invalid", pos);
            if (originallyMissing) data.remove(SPAWN);
            guardUnscaledCraftRespawn(event, old);
            return;
        }

        if (scaledCraft) {
            Vec3 stand = findStandingRoom(level, pos, size, BED.equals(kind));
            if (stand != null) {
                Vec3 world = Pym.frames().toWorld(craft, stand);
                // Audit ordinary world blocks separately from Sable's local
                // holding-chunk collision. This is observational only: no
                // position is rejected on the basis of this result.
                if (Pym.debugging()) {
                    String worldAudit = size > 16.0D ? "skipped (over 16x)"
                            : auditWorldTerrain(level, world, size);
                    PocketTrace.scale("Scaled respawn: PLACEMENT local={} world={} restoredScale={} worldTerrain={}",
                            stand, world, size, worldAudit);
                }
                DimensionTransition previous = event.getDimensionTransition();
                event.setDimensionTransition(new DimensionTransition(level, world, Vec3.ZERO,
                        previous.yRot(), previous.xRot(), false, previous.postDimensionTransition()));
                ((SableRespawnQueueMixin) old).pocket$setQueuedFreeze(
                        it.unimi.dsi.fastutil.Pair.of(craft.getUniqueId(), new Vector3d(stand.x, stand.y, stand.z)));
                if (originallyMissing) event.setCopyOriginalSpawnPosition(true);
            } else {
                // Sable's tracking point is at the centre of the bed block.
                // Keeping it when no clear spot exists can suffocate the new
                // player, even when the bed is larger than 1x.
                PocketTrace.scale("Scaled respawn: PLACEMENT FAILED at {} roomScale={} desiredScale={} bedOnly={} block={} ; using world spawn",
                        pos, roomScale, size, BED.equals(kind), state.getBlock());
                safeWorldFallback(event, old);
                return;
            }
            // Vanilla consumes charges for ordinary anchors. Sable's tracked
            // shortcut does not, so consume only if we kept a valid spawn.
            if (ANCHOR.equals(kind) && !event.getDimensionTransition().missingRespawnBlock()) {
                level.setBlock(pos, state.setValue(RespawnAnchorBlock.CHARGE,
                        state.getValue(RespawnAnchorBlock.CHARGE) - 1), 3);
            }
        }
        // A normal bed/anchor keeps vanilla's spawn-location decision. A
        // legitimately missing/blocked spawn retains vanilla's 1x fallback.
        if (!event.getDimensionTransition().missingRespawnBlock()) {
            data.putDouble(NEXT, size);
            PocketTrace.scale("Scaled respawn: selected size {} for new player", size);
        }
    }

    /**
     * A successfully resolved Sable tracking point can still be an unsafe 1x
     * respawn if the size record is missing or cannot be verified. In that
     * case, use world spawn for this death without deleting a potentially
     * valid bed/anchor or Sable tracking point.
     */
    private static void guardUnscaledCraftRespawn(PlayerRespawnPositionEvent event, ServerPlayer player) {
        var queued = ((ServerPlayerRespawnExtension) player).sable$getQueuedFreeze();
        if (queued == null) return; // Vanilla spawn; no Sable craft attachment.
        if (event.getDimensionTransition().missingRespawnBlock()) {
            ((SableRespawnQueueMixin) player).pocket$setQueuedFreeze(null);
            return; // Already going to the world spawn.
        }

        ServerLevel level = player.server.getLevel(player.getRespawnDimension());
        BlockPos pos = player.getRespawnPosition();
        SubLevel craft = level == null || pos == null ? null : craftAtBed(level, pos);
        // Only trust 1x in a craft we can identify *and* prove is at least 1x.
        // If the room is unloaded, fail safely rather than assuming scale 1.
        if (craft != null && !craft.isRemoved() && craft.getUniqueId().equals(queued.first())) {
            double scale = Pym.scale().of(craft);
            if (ScaleBounds.isValid(scale) && scale >= 1.0D) return;
        }

        PocketTrace.scale("Scaled respawn: Sable spawn has no verified safe size; using world spawn at 1x");
        safeWorldFallback(event, player);
    }

    private static void safeWorldFallback(PlayerRespawnPositionEvent event, ServerPlayer player) {
        PocketTrace.scale("Scaled respawn: FALLBACK player={} savedPos={} sablePoint={} queuedCraft={} eventMissing={}",
                player.getUUID(), player.getRespawnPosition(),
                ((ServerPlayerRespawnExtension) player).sable$getRespawnPoint(),
                ((ServerPlayerRespawnExtension) player).sable$getQueuedFreeze() == null ? "none"
                        : ((ServerPlayerRespawnExtension) player).sable$getQueuedFreeze().first(),
                event.getDimensionTransition().missingRespawnBlock());
        DimensionTransition safe = DimensionTransition.missingRespawnBlock(player.server.overworld(), player,
                event.getDimensionTransition().postDimensionTransition());
        // This is an uncertain scale/loaded-state fallback, not proof that the
        // original bed was destroyed. Suppress the misleading missing-bed text
        // and retain the original spawn so it can work after the craft loads.
        event.setDimensionTransition(new DimensionTransition(safe.newLevel(), safe.pos(), safe.speed(),
                safe.yRot(), safe.xRot(), false, safe.postDimensionTransition()));
        event.setCopyOriginalSpawnPosition(true);
        ((SableRespawnQueueMixin) player).pocket$setQueuedFreeze(null);
    }

    private static void missing(PlayerRespawnPositionEvent event, ServerPlayer player) {
        player.getPersistentData().remove(SPAWN);
        ((SableRespawnQueueMixin) player).pocket$setQueuedFreeze(null);
        // Do not leave an unreachable saved tracking point behind after the
        // original spawn block is gone. Sable otherwise keeps the UUID alive.
        UUID oldPoint = ((ServerPlayerRespawnExtension) player).sable$getRespawnPoint();
        ServerLevel savedLevel = player.server.getLevel(player.getRespawnDimension());
        if (oldPoint != null && savedLevel != null) {
            SubLevelTrackingPointSavedData.getOrLoad(savedLevel).removeTrackingPoint(oldPoint);
        }
        // Use vanilla's safe world-spawn search, including the missing-bed
        // message, rather than relying on a now-invalid Sable tracking point.
        ServerLevel overworld = player.server.overworld();
        DimensionTransition current = event.getDimensionTransition();
        event.setDimensionTransition(DimensionTransition.missingRespawnBlock(overworld, player,
                current.postDimensionTransition()));
        event.setCopyOriginalSpawnPosition(false);
    }

    /**
     * Audit vanilla world terrain at the full restored size (not local size).
     * Sable's holding blocks live elsewhere, so they are checked by the
     * existing local search and deliberately excluded from this audit.
     * Never used to choose a respawn location; logs are diagnostic only.
     */
    private static String auditWorldTerrain(ServerLevel level, Vec3 world, double scale) {
        if (!ScaleBounds.isValid(scale)) return "invalid size";
        double radius = 0.3D * scale;
        AABB fullBox = new AABB(world.x - radius + EPS, world.y + EPS,
                world.z - radius + EPS, world.x + radius - EPS,
                world.y + 1.8D * scale - EPS, world.z + radius - EPS);
        for (BlockPos candidate : BlockPos.betweenClosed(
                (int) Math.floor(fullBox.minX), (int) Math.floor(fullBox.minY), (int) Math.floor(fullBox.minZ),
                (int) Math.floor(fullBox.maxX), (int) Math.floor(fullBox.maxY), (int) Math.floor(fullBox.maxZ))) {
            if (!level.hasChunkAt(candidate)) return "unloaded at " + candidate;
            VoxelShape shape = level.getBlockState(candidate).getCollisionShape(level, candidate);
            for (AABB blockBox : shape.toAabbs()) {
                if (blockBox.move(candidate.getX(), candidate.getY(), candidate.getZ()).intersects(fullBox)) {
                    return "overlaps vanilla block at " + candidate;
                }
            }
        }
        return "clear of vanilla world blocks";
    }

    private static Vec3 findStandingRoom(ServerLevel level, BlockPos pos,
                                         double desiredScale, boolean bed) {
        BlockState state = level.getBlockState(pos);
        BlockPos start = pos;
        if (bed && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
            start = pos.relative(state.getValue(BedBlock.FACING).getOpposite());
        }
        double roomScale = bedSize(level, pos);
        double relative = desiredScale / roomScale;
        double width = 0.6D * relative;
        double height = 1.8D * relative;
        int[] rangeY = { 0, 1, -1 };
        // Search adjacent floor cells; don't accept a centre-of-bed spawn.
        for (int y : rangeY) {
            for (int[] offset : OFFSETS) {
                BlockPos ground = start.offset(offset[0], y, offset[1]);
                BlockPos below = ground.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                Vec3 local = Vec3.atBottomCenterOf(ground);
                AABB localBox = new AABB(local.x - width / 2.0D, local.y + EPS,
                        local.z - width / 2.0D, local.x + width / 2.0D,
                        local.y + height, local.z + width / 2.0D);
                if (!level.noCollision(localBox)) continue;
                // The local holding blocks are authoritative for clearance.
                // A second noCollision check in transformed world coordinates
                // can reject perfectly safe spaces inside scaled sublevels.
                return local;
            }
        }
        // A bed-only sublevel has no neighbouring floor blocks. Spawn safely
        // on top of a bed half instead; never use Sable's default bed-centre
        // point, which intersects the bed at any player scale.
        if (bed) {
            Vec3 top = onTopOfBlock(level, start, width, height);
            if (top != null) return top;
            BlockPos other = start.relative(level.getBlockState(start).getValue(BedBlock.FACING));
            return onTopOfBlock(level, other, width, height);
        }
        return onTopOfBlock(level, pos, width, height);
    }

    private static Vec3 onTopOfBlock(ServerLevel level, BlockPos pos,
                                     double width, double height) {
        BlockState state = level.getBlockState(pos);
        VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return null;
        double top = collision.max(Direction.Axis.Y);
        if (top <= 0.0D || top > 1.0D) return null;
        Vec3 local = new Vec3(pos.getX() + 0.5D, pos.getY() + top + EPS, pos.getZ() + 0.5D);
        AABB box = new AABB(local.x - width / 2.0D, local.y, local.z - width / 2.0D,
                local.x + width / 2.0D, local.y + height, local.z + width / 2.0D);
        return level.noCollision(box) ? local : null;
    }

    private static boolean validBed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return true;
        BlockPos other = pos.relative(BedBlock.getConnectedDirection(state));
        BlockState partner = level.getBlockState(other);
        return partner.getBlock() == state.getBlock() && partner.hasProperty(BedBlock.PART)
                && partner.getValue(BedBlock.PART) != state.getValue(BedBlock.PART)
                && partner.getValue(BedBlock.FACING) == state.getValue(BedBlock.FACING);
    }

    private static String kind(BlockState state) {
        if (state.getBlock() instanceof BedBlock) return BED;
        if (state.is(Blocks.RESPAWN_ANCHOR)) return ANCHOR;
        return null;
    }

    private static SubLevel craftAtBed(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        SubLevel craft = Sable.HELPER.getContaining(level, pos);
        if (craft != null && !craft.isRemoved()) return craft;
        // A bed can cross a holding-chunk boundary. When one half is available
        // but its partner's Sable lookup is not, resolve through the partner.
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        BlockPos other = pos.relative(BedBlock.getConnectedDirection(state));
        BlockState partner = level.getBlockState(other);
        if (partner.getBlock() != state.getBlock() || !partner.hasProperty(BedBlock.PART)
                || partner.getValue(BedBlock.PART) == state.getValue(BedBlock.PART)) return null;
        craft = Sable.HELPER.getContaining(level, other);
        return craft == null || craft.isRemoved() ? null : craft;
    }

    private static double bedSize(Level level, BlockPos pos) {
        SubLevel craft = craftAtBed(level, pos);
        return craft == null ? 1.0D : Pym.scale().of(craft);
    }

    private static boolean fits(ServerPlayer player, Level level, BlockPos pos) {
        double bedScale = bedSize(level, pos);
        double playerScale = Pym.entities().scaleOf(player);
        return ScaledSpawnMath.fits(playerScale, bedScale);
    }

    private static void tooSmall(ServerPlayer player, String kind) {
        player.displayClientMessage(Component.translatable(BED.equals(kind)
                ? "pocket.message.bed_too_small" : "pocket.message.anchor_too_small"), true);
    }

    static void cloneScale(ServerPlayer from, ServerPlayer to, boolean death) {
        if (from.getPersistentData().contains(SPAWN, Tag.TAG_COMPOUND))
            to.getPersistentData().put(SPAWN, from.getPersistentData().getCompound(SPAWN).copy());
        if (!death) return;
        double next = from.getPersistentData().getDouble(NEXT);
        if (!ScaleBounds.isValid(next)) next = 1.0D;
        to.getPersistentData().putDouble(NEXT, next);
        PersonalScale.remember(to, next);
        PocketTrace.scale("Scaled respawn: CLONE carrying size={} oldSavedPos={} newSavedPos={} oldTracking={} newTracking={}",
                next, from.getRespawnPosition(), to.getRespawnPosition(),
                ((ServerPlayerRespawnExtension) from).sable$getRespawnPoint(),
                ((ServerPlayerRespawnExtension) to).sable$getRespawnPoint());
    }

    static void finishRespawn(ServerPlayer player) {
        RespawnHold hold = RESPAWN_HOLDS.get(player.getUUID());
        if (hold != null) hold.releaseAt = hold.level.getGameTime() + 3;
        CompoundTag data = player.getPersistentData();
        double scale = data.contains(NEXT, Tag.TAG_ANY_NUMERIC) ? data.getDouble(NEXT) : 1.0D;
        if (!ScaleBounds.isValid(scale)) scale = 1.0D;
        PersonalScale.remember(player, scale);
        Pym.entities().setScale(player, scale, 0);
        data.remove(NEXT);
        if (Pym.debugging()) RESPAWN_WATCH.put(player.getUUID(), 3);
        PocketTrace.scale("Scaled respawn: applied requested={} actual={} success={}",
                scale, Pym.entities().scaleOf(player), ScaleBounds.same(scale, Pym.entities().scaleOf(player)));
    }
}
