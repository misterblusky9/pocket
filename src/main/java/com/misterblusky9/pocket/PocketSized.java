package com.misterblusky9.pocket;

import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.SubspaceRecyclerBlockEntity;
import com.misterblusky9.pocket.config.PocketConfigs;
import com.misterblusky9.pocket.create.PocketContraptionTypes;
import com.misterblusky9.pocket.create.PocketCreateIntegration;
import com.misterblusky9.pocket.entity.ModEntities;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.HelmBearingNetwork;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.pocket.CannonDeploymentQueue;
import com.misterblusky9.pocket.pocket.PocketPerformanceLimits;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PocketSized.MOD_ID)
public final class PocketSized {
    public static final String MOD_ID = "pocket";
    public static final String MOD_NAME = "Create: Pocket Sized";

    public static final double MIN_SCALE = 1.0D / 64.0D;
    public static final double MAX_SCALE = 32.0D;
    public static final double EXPERIMENTAL_MIN_SCALE = 1.0D / 32.0D;
    public static final double EXPERIMENTAL_MAX_SCALE = 4.0D;
    public static final double CREATIVE_MIN_SCALE = 1.0D / 16.0D;
    public static final double CREATIVE_MAX_SCALE = 2.0D;
    public static final double SURVIVAL_MIN_SCALE = 1.0D / 16.0D;
    public static final double SURVIVAL_MAX_SCALE = 2.0D;
    public static final double STANDARD_MAX_SCALE = 2.0D;
    public static final double FULL_SCALE = 1.0D;
    public static final double EPSILON = 1.0E-6D;

    public static final int MAX_COMPRESSED_BLOCKS = 1_048_576;

    public PocketSized(final IEventBus modBus, final ModContainer modContainer) {
        PocketConfigs.register(modBus, modContainer);
        modBus.addListener(PocketContraptionTypes::register);

        PehkuiScaleBridge.initialize();

        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SubspaceRecyclerBlockEntity::registerCapabilities);
        ModItems.ITEMS.register(modBus);
        com.misterblusky9.pocket.item.CompressionGunTank.COMPONENTS.register(modBus);
        modBus.addListener(com.misterblusky9.pocket.item.CompressionGunTank::registerCapabilities);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        ModCreativeTabs.TABS.register(modBus);
        com.misterblusky9.pocket.advancement.HauntedCompressionGunTrigger.TRIGGERS.register(modBus);
        PocketCreateIntegration.register(modBus);
        modBus.addListener(ScaleNetwork::register);
        modBus.addListener(HelmBearingNetwork::register);

        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(PocketedSubLevelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CannonDeploymentQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(PocketPerformanceLimits::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.CompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.EntityCompressionSessions::onServerTick);
    }

    public static double clampCreativeScale(final double scale) {
        return Math.max(CREATIVE_MIN_SCALE, Math.min(CREATIVE_MAX_SCALE, scale));
    }

    public static double clampExperimentalScale(final double scale) {
        return Math.max(EXPERIMENTAL_MIN_SCALE, Math.min(EXPERIMENTAL_MAX_SCALE, scale));
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
