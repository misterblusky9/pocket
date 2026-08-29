package com.misterblusky9.pocket;

import com.misterblusky9.pocket.entity.ModEntities;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.SubspaceRecyclerBlockEntity;
import com.misterblusky9.pocket.create.PocketCreateIntegration;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.pocket.CannonDeploymentQueue;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pocket.pocket.PocketPerformanceLimits;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PocketSized.MOD_ID)
public final class PocketSized {
    public static final String MOD_ID = "pocket";
    public static final String MOD_NAME = "Create: Pocket Sized";

    public static final double MIN_SCALE = 1.0D / 16.0D;
    public static final double MAX_SCALE = 1.0D;
    public static final double EPSILON = 1.0E-6D;

    public static final int MAX_COMPRESSED_BLOCKS = 1_048_576;

    public PocketSized(final IEventBus modBus) {
        PehkuiScaleBridge.initialize();

        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SubspaceRecyclerBlockEntity::registerCapabilities);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        ModCreativeTabs.TABS.register(modBus);
        PocketCreateIntegration.register(modBus);
        modBus.addListener(ScaleNetwork::register);

        NeoForge.EVENT_BUS.addListener(PocketedSubLevelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CannonDeploymentQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(PocketPerformanceLimits::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.CompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.SelfCompressionSessions::onServerTick);
    }

    public static double clampScale(final double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static boolean isValidScale(final double scale) {
        return Double.isFinite(scale)
                && scale >= MIN_SCALE - EPSILON
                && scale <= MAX_SCALE + EPSILON;
    }
}
