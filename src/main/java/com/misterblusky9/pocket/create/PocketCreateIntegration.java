package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.bus.api.IEventBus;
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

    public static void register(final IEventBus modBus) {
        BLOCK_HIT_ACTIONS.register(modBus);
        ENTITY_HIT_ACTIONS.register(modBus);
    }

    private PocketCreateIntegration() {}
}
