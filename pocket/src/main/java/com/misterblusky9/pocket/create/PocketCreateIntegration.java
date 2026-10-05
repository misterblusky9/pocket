package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import com.misterblusky9.pocket.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PocketCreateIntegration {
    public static final DeferredRegister<MapCodec<? extends PotatoProjectileBlockHitAction>> BLOCK_HIT_ACTIONS =
            DeferredRegister.create(CreateRegistries.POTATO_PROJECTILE_BLOCK_HIT_ACTION, PocketSized.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends PotatoProjectileEntityHitAction>> ENTITY_HIT_ACTIONS =
            DeferredRegister.create(CreateRegistries.POTATO_PROJECTILE_ENTITY_HIT_ACTION, PocketSized.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends PotatoProjectileBlockHitAction>, MapCodec<PocketedSubLevelBlockHitAction>>
            POCKETED_DEPLOY = BLOCK_HIT_ACTIONS.register(
                    "deploy_pocketed_sublevel", () -> PocketedSubLevelBlockHitAction.CODEC
            );

    public static final DeferredHolder<MapCodec<? extends PotatoProjectileEntityHitAction>, MapCodec<PocketedSubLevelEntityHitAction>>
            POCKETED_ENTITY_HIT = ENTITY_HIT_ACTIONS.register(
                    "pocketed_sublevel_entity_hit", () -> PocketedSubLevelEntityHitAction.CODEC
            );

    public static final DeferredRegister<ArmInteractionPointType> ARM_INTERACTION_POINTS =
            DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<ArmInteractionPointType, SubspaceRecyclerArmPointType>
            SUBSPACE_RECYCLER_ARM_POINT = ARM_INTERACTION_POINTS.register(
                    "subspace_recycler", SubspaceRecyclerArmPointType::new
            );

    public static final double SUBSPACE_RECYCLER_STRESS_IMPACT = 4.0D;
    public static final double STATIC_SUBSPACE_COMPRESSOR_STRESS_IMPACT = 8.0D;
    public static final double SWITCH_BEARING_STRESS_IMPACT = 4.0D;
    public static final double SWITCH_PISTON_STRESS_IMPACT = 4.0D;
    public static final double HELM_BEARING_STRESS_CAPACITY = 16.0D;

    public static void register(final IEventBus modBus) {
        BLOCK_HIT_ACTIONS.register(modBus);
        ENTITY_HIT_ACTIONS.register(modBus);
        ARM_INTERACTION_POINTS.register(modBus);
        modBus.addListener(PocketCreateIntegration::onCommonSetup);
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        BlockStressValues.IMPACTS.register(
                ModBlocks.SUBSPACE_RECYCLER.get(), () -> SUBSPACE_RECYCLER_STRESS_IMPACT
        );
        BlockStressValues.IMPACTS.register(
                ModBlocks.STATIC_SUBSPACE_COMPRESSOR.get(),
                () -> STATIC_SUBSPACE_COMPRESSOR_STRESS_IMPACT
        );
        BlockStressValues.IMPACTS.register(
                ModBlocks.SWITCH_BEARING.get(), () -> SWITCH_BEARING_STRESS_IMPACT
        );
        BlockStressValues.IMPACTS.register(
                ModBlocks.SWITCH_PISTON.get(), () -> SWITCH_PISTON_STRESS_IMPACT
        );
        BlockStressValues.CAPACITIES.register(
                ModBlocks.HELM_BEARING.get(), () -> HELM_BEARING_STRESS_CAPACITY
        );
        BlockStressValues.setGeneratorSpeed(HelmBearingBlockEntity.RPM).accept(ModBlocks.HELM_BEARING.get());
        MovementBehaviour.REGISTRY.register(
                ModBlocks.SWITCH_BEARING.get(), new SwitchBearingMovementBehaviour()
        );
        MovementBehaviour.REGISTRY.register(
                ModBlocks.HELM_BEARING.get(), new SwitchBearingMovementBehaviour()
        );
    }

    private PocketCreateIntegration() {}
}
