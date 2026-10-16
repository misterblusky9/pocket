package com.misterblusky9.pocket.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public final class BlueprintEncasing {
    private BlueprintEncasing() {}

    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EncasingRegistry.addVariant(AllBlocks.SHAFT.get(),
                    ModBlocks.BLUEPRINT_ENCASED_SHAFT.get());
            EncasingRegistry.addVariant(AllBlocks.COGWHEEL.get(),
                    ModBlocks.BLUEPRINT_ENCASED_COGWHEEL.get());
            EncasingRegistry.addVariant(AllBlocks.LARGE_COGWHEEL.get(),
                    ModBlocks.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get());
        });
    }
}
