package com.misterblusky9.pocket;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.SubspaceRecyclerBlockEntity;
import com.misterblusky9.pocket.create.PocketContraptionTypes;
import com.misterblusky9.pocket.create.PocketCreateIntegration;
import com.misterblusky9.pocket.entity.ModEntities;
import com.misterblusky9.pocket.entity.ScaledRespawn;
import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.HelmBearingNetwork;
import com.misterblusky9.pocket.network.PocketNetwork;
import com.misterblusky9.pocket.pocket.CannonDeploymentQueue;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pocket.scale.PocketPymIntegration;
import com.misterblusky9.pym.api.Pym;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PocketSized.MOD_ID)
public final class PocketSized {
    public static final String MOD_ID = "pocket";
    public static final String MOD_NAME = "Create: Pocket Sized";

    public PocketSized(IEventBus modBus, ModContainer modContainer) {
        if (!Pym.entities().pehkuiAvailable()) {
            throw new IllegalStateException("Create: Pocket Sized requires Pym with a working Pehkui backend");
        }

        DeviceRanges.register(modBus, modContainer);
        modBus.addListener(PocketContraptionTypes::register);

        PocketPymIntegration.register();
        com.misterblusky9.pym.api.Hosted.wearables(com.misterblusky9.pocket.build.CarriedBuilds::worn,
                com.misterblusky9.pocket.build.CarriedBuilds::replaced,
                com.misterblusky9.pocket.build.CarriedBuilds::openRiders);
        com.misterblusky9.pym.api.Hosted.tangible(root -> com.misterblusky9.pocket.build.BuildSites.isAttached(root)
                || com.misterblusky9.pocket.build.CarriedBuilds.isWorn(root));
        com.misterblusky9.pym.api.Hosted.editable(com.misterblusky9.pocket.build.BuildSites::isAttached);
        com.misterblusky9.pym.api.Hosted.wornBy(com.misterblusky9.pocket.build.CarriedBuilds::wornBy);
        if (ModList.get().isLoaded("sable_schematic_api")) PhotomancyCompat.register();
        if (ModList.get().isLoaded("curios")) com.misterblusky9.pocket.compat.curios.CuriosHats.register();
        if (com.misterblusky9.pocket.compat.WornHats.ACCESSORIES) com.misterblusky9.pocket.compat.accessories.AccessoriesCompat.register();

        ScaledRespawn.registerRespawnTicket();

        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SubspaceRecyclerBlockEntity::registerCapabilities);
        modBus.addListener(HatStandBlockEntity::registerCapabilities);
        modBus.addListener(com.misterblusky9.pocket.block.DummyHead::addToSkullEntity);
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.misterblusky9.pocket.item.HatStandRecipe.SERIALIZERS.register(modBus);
        com.misterblusky9.pocket.item.ModMenus.MENUS.register(modBus);
        com.misterblusky9.pocket.item.ModDataComponents.COMPONENTS.register(modBus);
        com.misterblusky9.pocket.item.CompressionGunTank.COMPONENTS.register(modBus);
        modBus.addListener(com.misterblusky9.pocket.item.CompressionGunTank::registerCapabilities);
        ModEntities.ENTITIES.register(modBus);
        com.misterblusky9.pocket.particle.ModParticles.PARTICLE_TYPES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        ModCreativeTabs.TABS.register(modBus);
        com.misterblusky9.pocket.advancement.HauntedCompressionGunTrigger.TRIGGERS.register(modBus);
        PocketCreateIntegration.register(modBus);
        modBus.addListener(com.misterblusky9.pocket.block.BlueprintEncasing::register);

        modBus.addListener(PocketNetwork::register);
        modBus.addListener(HelmBearingNetwork::register);

        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(HeldInteractionPriority::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(PocketedSubLevelEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onStackedOnOther);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.HatStandInteractions::onAnvilTake);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.BuildSites::onLevelSave);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.BuildSites::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatNativeContraptions::onJoin);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.BuildSites::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.BuildSites::onServerStopping);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onMount);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onJump);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onBreak);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onPlace);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW,
                com.misterblusky9.pocket.advancement.HatAdvancements::onPlace);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.advancement.HatAdvancements::onStruck);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.HatSeals::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onLevelSave);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.build.CarriedBuilds::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CannonDeploymentQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.FacadeAlignment::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.block.FacadeAlignment::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.CompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.compression.EntityCompressionSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.item.ScaleToolModifier::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.misterblusky9.pocket.item.PocketCommands::register);
    }
}
