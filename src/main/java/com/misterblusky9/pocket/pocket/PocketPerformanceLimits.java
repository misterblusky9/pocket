package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class PocketPerformanceLimits {
    public static void onBlockPlaced(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) return;

        final SubLevel found = Sable.HELPER.getContaining(level, event.getPos());
        if (!(found instanceof final ServerSubLevel subLevel) || !ScaleState.isScaled(subLevel)) return;

        BlockState noShrinkState = null;
        if (event instanceof final BlockEvent.EntityMultiPlaceEvent multi) {
            for (final var snapshot : multi.getReplacedBlockSnapshots()) {
                if (CompressionBlacklist.isBlacklisted(snapshot.getCurrentState())) {
                    noShrinkState = snapshot.getCurrentState();
                    break;
                }
            }
        } else if (CompressionBlacklist.isBlacklisted(event.getPlacedBlock())) {
            noShrinkState = event.getPlacedBlock();
        }

        int additions = 0;
        if (event instanceof final BlockEvent.EntityMultiPlaceEvent multi) {
            for (final var snapshot : multi.getReplacedBlockSnapshots()) {
                if (snapshot.getState().isAir() && !snapshot.getCurrentState().isAir()) additions++;
            }
        } else if (event.getBlockSnapshot().getState().isAir() && !event.getPlacedBlock().isAir()) {
            additions = 1;
        }
        if (additions > 0) {
            final PocketMetrics metrics = PocketMetrics.measureForCompression(subLevel, level.getGameTime());
            if ((long) metrics.blocks() + additions > PocketSized.MAX_COMPRESSED_BLOCKS) {
                event.setCanceled(true);
                if (event.getEntity() instanceof final Player player) {
                    player.displayClientMessage(Component.literal(
                            "Pocket Sized limit: " + PocketSized.MAX_COMPRESSED_BLOCKS + " blocks while compressed"
                    ), true);
                }
                return;
            }
        }

        if (noShrinkState != null && event.getEntity() instanceof final Player player) {
            player.displayClientMessage(Component.literal(CompressionBlacklist.MESSAGE), true);
        }
    }

    private PocketPerformanceLimits() {}
}
