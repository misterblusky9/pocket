package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.scale.CompressionStage;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

public final class PocketedSubLevelEvents {
    public static final double PICKUP_SCALE_TOLERANCE = 0.0015D;

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return;

        SubLevel found = findSubLevel(event);
        if (found == null) return;

        double settled = Pym.scale().settled(found);
        if (Math.abs(Pym.scale().of(found) - settled) > PICKUP_SCALE_TOLERANCE
                || !Pym.scale().isAt(found, settled)) return;

        ItemStack held = event.getItemStack();
        if (!PocketCaseItem.isContainer(held)) return;

        if (!fitsPocket(player, settled)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getLevel() instanceof ServerLevel serverLevel)
                || !(found instanceof ServerSubLevel subLevel)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null && CrossScaleWelds.isWelded(container, subLevel)) {
            player.displayClientMessage(Component.literal(CrossScaleWelds.POCKET_BLOCKED), true);
            return;
        }

        PocketedEntities.disassembleContraptions(serverLevel, subLevel);

        int blocks = Pym.resize().contents(subLevel).blocks();
        int limit = Pym.resize().shrunkBlockLimit();
        if (blocks > limit) {
            player.displayClientMessage(Component.translatable("pocket.message.hard_limit_ratio", blocks, limit), true);
            return;
        }
        pocket(serverLevel, player, subLevel, held);
    }

    private static boolean creativePocketsAnything(Player player) {
        return player != null && player.isCreative() && DeviceRanges.creativePocketsAnything();
    }

    public static boolean fitsPocket(Player player, double scale) {
        if (creativePocketsAnything(player)) return true;
        double size = player == null ? ScaleBounds.FULL : Pym.entities().scaleOf(player);
        double relative = ScaleBounds.isValid(size) ? size : ScaleBounds.FULL;
        return scale <= (PocketCaseItem.POCKETABLE_SCALE + PICKUP_SCALE_TOLERANCE) * relative;
    }

    private static SubLevel findSubLevel(PlayerInteractEvent.RightClickBlock event) {
        SubLevel sub = Sable.HELPER.getContaining(event.getLevel(), event.getHitVec().getBlockPos());
        if (sub == null) sub = Sable.HELPER.getContaining(event.getLevel(), event.getHitVec().getLocation());
        return sub;
    }

    private static void pocket(
            ServerLevel level,
            Player player,
            ServerSubLevel subLevel,
            ItemStack packedInto
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        if (CrossScaleWelds.isWelded(container, subLevel)) {
            player.displayClientMessage(Component.literal(CrossScaleWelds.POCKET_BLOCKED), true);
            return;
        }

        if (Pym.connections().isJoinedToAnother(subLevel)) {
            player.displayClientMessage(Component.literal(
                    "Cannot pocket a sublevel while it is joined to another sublevel."), true);
            return;
        }

        SubLevelData serialized = SubLevelSerializer.toData(subLevel, List.of());
        CompoundTag fullTag = serialized.fullTag().copy();
        PocketCaseItem.markDetachedPayload(fullTag);
        CompoundTag plotTag = fullTag.getCompound("plot");
        int plotX = plotTag.getInt("plot_x");
        int plotZ = plotTag.getInt("plot_z");
        UUID token = UUID.randomUUID();

        PlotContents canonicalMetrics = PocketCaseItem.prepareCapturedPayload(
                subLevel, fullTag, token, player);
        if (canonicalMetrics == null) return;

        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
        storage.put(token, fullTag);
        String displayName = subLevel.getName();
        boolean highDetailPlate = packedInto.is(ModItems.BRASS_DISPLAY_PLATE.get())
                || packedInto.is(ModItems.ANDESITE_DISPLAY_PLATE.get());
        int previewBudget = highDetailPlate
                ? PocketRenderSnapshot.DISPLAY_PLATE_PREVIEW_BLOCKS
                : PocketRenderSnapshot.MAX_PREVIEW_BLOCKS;
        PocketRenderSnapshot snapshot = PocketRenderSnapshot.capture(subLevel, player, previewBudget);

        var massTracker = subLevel.getMassTracker();
        double mass = massTracker == null ? 0.0D : massTracker.getMass();

        ItemStack result = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), token, level,
                displayName, snapshot, canonicalMetrics.blocks(), canonicalMetrics.blockEntities(), mass);
        PocketCaseItem.setPackedBy(result, player.getGameProfile().getName());
        double settled = Pym.scale().settled(subLevel);
        PocketCaseItem.setPocketedScale(result, creativePocketsAnything(player)
                ? Math.min(settled, PocketCaseItem.POCKETABLE_SCALE)
                : settled);
        PocketCaseItem.setFacadeOffset(result, com.misterblusky9.pocket.block.FacadeAlignment.of(subLevel));
        PocketCaseItem.recallCaseTurns(subLevel, result);

        PocketCaseItem.setContainer(result, packedInto.isEmpty()
                ? new ItemStack(ModItems.EMPTY_BOX.get())
                : packedInto);

        boolean removedFromWorld = false;
        boolean resultGiven = false;
        try {
            PocketedEntities.capture(level, subLevel, fullTag);
            storage.put(token, fullTag);
            subLevel.getPlot().kickAllEntities();

            UUID id = subLevel.getUniqueId();
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            removedFromWorld = true;

            giveResult(player, result);
            resultGiven = true;

            storage.commitCapture(level, token,
                    player instanceof ServerPlayer sp ? sp : null);
            boolean payloadStored = storage.contains(token);
            boolean sourceFree = !container.getOccupancy().get(container.getIndex(plotX, plotZ));
            boolean sourceGone = container.getSubLevel(plotX, plotZ) == null;
            PocketTrace.debug(
                    "[PocketTransfer] capture commit token={} source={} plot=({}, {}) uuid={} "
                            + "payloadStored={} sourceFree={} liveRemoved={} blocks={} blockEntities={} "
                            + "entities={} backendValid={}",
                    token, level.dimension().location(), plotX, plotZ, id,
                    payloadStored, sourceFree, sourceGone,
                    canonicalMetrics.blocks(), canonicalMetrics.blockEntities(),
                    PocketedEntities.count(fullTag), payloadStored && sourceFree && sourceGone);
        } catch (RuntimeException exception) {
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

    private static void giveResult(Player player, ItemStack result) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!held.isEmpty()) held.shrink(1);

        if (held.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                serverPlayer.containerMenu.broadcastChanges();
            }
        }
    }

    private PocketedSubLevelEvents() {}
}
