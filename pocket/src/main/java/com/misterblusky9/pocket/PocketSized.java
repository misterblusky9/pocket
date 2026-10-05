package com.misterblusky9.pocket;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.SubspaceRecyclerBlockEntity;
import com.misterblusky9.pocket.create.PocketContraptionTypes;
import com.misterblusky9.pocket.create.PocketCreateIntegration;
import com.misterblusky9.pocket.entity.ModEntities;
import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.HelmBearingNetwork;
import com.misterblusky9.pocket.network.PocketNetwork;
import com.misterblusky9.pocket.pocket.CannonDeploymentQueue;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pocket.scale.PocketPymIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PocketSized.MOD_ID)
public final class PocketSized {
    public static final String MOD_ID = "pocket";
    public static final String MOD_NAME = "Create: Pocket Sized";

    public PocketSized(final IEventBus modBus, final ModContainer modContainer) {
        DeviceRanges.register(modBus, modContainer);
        modBus.addListener(PocketContraptionTypes::register);

        PocketPymIntegration.register();

        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SubspaceRecyclerBlockEntity::registerCapabilities);
        ModItems.ITEMS.register(modBus);
        com.misterblusky9.pocket.item.ModDataComponents.COMPONENTS.register(modBus);
        com.misterblusky9.pocket.item.CompressionGunTank.COMPONENTS.register(modBus);
        modBus.addListener(com.misterblusky9.pocket.item.CompressionGunTank::registerCapabilities);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        ModCreativeTabs.TABS.register(modBus);
        com.misterblusky9.pocket.advancement.HauntedCompressionGunTrigger.TRIGGERS.register(modBus);
        PocketCreateIntegration.register(modBus);
        modBus.addListener(PocketNetwork::register);
        modBus.addListener(HelmBearingNetwork::register);

        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(PocketedSubLevelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CannonDeploymentQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.CompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.EntityCompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.item.ScaleToolModifier::onLoggedOut);
    }
}
