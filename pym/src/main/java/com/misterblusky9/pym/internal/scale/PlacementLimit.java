package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.internal.PymMod;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = PymMod.MOD_ID)
public final class PlacementLimit {
    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) return;
        if (!(Sable.HELPER.getContaining(level, event.getPos()) instanceof final ServerSubLevel subLevel)) return;
        if (!(ScaleState.getServerScale(subLevel) < 1.0D)) return;

        final List<BlockSnapshot> placed = event instanceof final BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots()
                : List.of(event.getBlockSnapshot());

        int added = 0;
        BlockState noShrink = null;
        for (final BlockSnapshot snapshot : placed) {
            final BlockState now = snapshot == event.getBlockSnapshot() ? event.getPlacedBlock() : snapshot.getCurrentState();
            if (snapshot.getState().isAir() && !now.isAir()) added++;
            if (noShrink == null && PlotScan.isNoShrink(now)) noShrink = now;
        }

        final Player player = event.getEntity() instanceof final Player p ? p : null;
        final int limit = PlotScan.shrunkBlockLimit();
        if (added > 0 && (long) PlotScan.of(subLevel).blocks() + added > limit) {
            event.setCanceled(true);
            if (player != null) player.displayClientMessage(Component.literal("A shrunk sublevel holds at most " + limit + " blocks"), true);
            return;
        }
        if (noShrink != null && player != null) {
            player.displayClientMessage(Component.literal("A sublevel holding this block cannot be shrunk any further"), true);
        }
    }

    private PlacementLimit() {}
}
