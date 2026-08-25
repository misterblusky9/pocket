package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ScaledBoundsCollider;
import com.misterblusky9.pocket.physics.ScaledFluidForces;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

public final class PocketedSubLevelEvents {
    private static final double PICKUP_SCALE_TOLERANCE = 0.0015D;

    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return;

        final SubLevel found = findSubLevel(event);
        if (found == null) return;

        final double scale = ScaleState.getScale(found);
        if (Math.abs(scale - PocketSized.MIN_SCALE) > PICKUP_SCALE_TOLERANCE
                || ScaleState.getStage(found) != CompressionStage.SIXTEENTH) return;

        final ItemStack held = event.getItemStack();
        final boolean packaged = PocketCaseItem.isContainer(held);
        final boolean creativeEmptyHanded = player.isCreative() && held.isEmpty();
        if (!packaged && !creativeEmptyHanded) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getLevel() instanceof final ServerLevel serverLevel)
                || !(found instanceof final ServerSubLevel subLevel)) return;

        final PocketMetrics metrics = PocketMetrics.measureForCompression(subLevel, serverLevel.getGameTime());
        if (metrics.blocks() > PocketSized.MAX_COMPRESSED_BLOCKS) {
            player.displayClientMessage(Component.literal("Pocket Sized hard limit: "
                    + metrics.blocks() + "/" + PocketSized.MAX_COMPRESSED_BLOCKS + " blocks"), true);
            return;
        }
        pocket(serverLevel, player, subLevel, metrics, held);
    }

    private static SubLevel findSubLevel(final PlayerInteractEvent.RightClickBlock event) {
        SubLevel sub = Sable.HELPER.getContaining(event.getLevel(), event.getHitVec().getBlockPos());
        if (sub == null) sub = Sable.HELPER.getContaining(event.getLevel(), event.getHitVec().getLocation());
        return sub;
    }

    private static void pocket(
            final ServerLevel level,
            final Player player,
            final ServerSubLevel subLevel,
            final PocketMetrics metrics,
            final ItemStack packedInto
    ) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final SubLevelData serialized = SubLevelSerializer.toData(subLevel, List.of());
        final CompoundTag fullTag = serialized.fullTag().copy();
        final CompoundTag plotTag = fullTag.getCompound("plot");
        final int plotX = plotTag.getInt("plot_x");
        final int plotZ = plotTag.getInt("plot_z");
        final UUID token = UUID.randomUUID();

        final PocketMetrics canonicalMetrics = PocketCaseItem.prepareCapturedPayload(
                subLevel, fullTag, token, player);
        if (canonicalMetrics == null) return;

        final PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
        storage.put(token, fullTag);
        final String displayName = subLevel.getName() == null ? "Pocketed Contraption" : subLevel.getName();
        final boolean highDetailPlate = packedInto.is(ModItems.BRASS_DISPLAY_PLATE.get())
                || packedInto.is(ModItems.ANDESITE_DISPLAY_PLATE.get());
        final int previewBudget = highDetailPlate
                ? PocketRenderSnapshot.DISPLAY_PLATE_PREVIEW_BLOCKS
                : PocketRenderSnapshot.MAX_PREVIEW_BLOCKS;
        final PocketRenderSnapshot snapshot = PocketRenderSnapshot.capture(subLevel, player, previewBudget);

        final var massTracker = subLevel.getMassTracker();
        final double mass = massTracker == null ? 0.0D : massTracker.getMass();

        final ItemStack result = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), token, level,
                displayName, snapshot, canonicalMetrics.blocks(), canonicalMetrics.blockEntities(), mass);
        PocketCaseItem.setPackedBy(result, player.getGameProfile().getName());

        PocketCaseItem.setContainer(result, packedInto);

        boolean removedFromWorld = false;
        boolean resultGiven = false;
        try {
            PocketedEntities.capture(level, subLevel, fullTag);
            storage.put(token, fullTag);
            subLevel.getPlot().kickAllEntities();

            final UUID id = subLevel.getUniqueId();
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            removedFromWorld = true;

            container.getOccupancy().set(container.getIndex(plotX, plotZ));
            SubLevelOccupancySavedData.getOrLoad(level).setDirty();

            ScaleState.clearServerState(id);
            ScaleState.clearServerBounds(id);
            ScaledBoundsCollider.forgetSubLevel(id);
            ScaledFluidForces.forget(id);
            PocketMetrics.invalidate(id);

            giveResult(player, result);
            resultGiven = true;

            storage.commitCapture(level, token,
                    player instanceof final net.minecraft.server.level.ServerPlayer sp ? sp : null);
            final boolean payloadStored = storage.contains(token);
            final boolean sourceReserved = container.getOccupancy().get(container.getIndex(plotX, plotZ));
            final boolean sourceGone = container.getSubLevel(plotX, plotZ) == null;
            PocketTrace.logger().info(
                    "[PocketTransfer] capture commit token={} source={} plot=({}, {}) uuid={} "
                            + "payloadStored={} sourceReserved={} liveRemoved={} blocks={} blockEntities={} "
                            + "entities={} backendValid={}",
                    token, level.dimension().location(), plotX, plotZ, id,
                    payloadStored, sourceReserved, sourceGone,
                    canonicalMetrics.blocks(), canonicalMetrics.blockEntities(),
                    PocketedEntities.count(fullTag), payloadStored && sourceReserved && sourceGone);
        } catch (final RuntimeException exception) {
            if (!removedFromWorld) {
                storage.remove(token);
            } else if (!resultGiven) {
                giveResult(player, result);
            }
            player.displayClientMessage(Component.literal(
                    "Pocket capture interrupted: " + exception.getClass().getSimpleName()
                            + (removedFromWorld ? " (payload preserved in case)" : "")
            ), true);
            PocketTrace.logger().error(
                    "[PocketTransfer] capture failed token={} source={} plot=({}, {}) removedFromWorld={} "
                            + "payloadStored={} backendValid=false",
                    token, level.dimension().location(), plotX, plotZ, removedFromWorld,
                    storage.contains(token), exception);
        }
    }

    private static void giveResult(final Player player, final ItemStack result) {
        final ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!player.isCreative()) held.shrink(1);

        if (held.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        player.getInventory().setChanged();
    }

    private PocketedSubLevelEvents() {}
}
