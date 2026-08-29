package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class PocketPackageModels {
    public static void register(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerPackage(BuiltInRegistries.ITEM.getKey(ModItems.POCKETED_SUBLEVEL.get()));
            registerPackage(BuiltInRegistries.ITEM.getKey(ModItems.THE_MOON.get()));
        });
    }

    private static void registerPackage(final ResourceLocation id) {
        if (id == null) return;
        AllPartialModels.PACKAGES.put(id, PartialModel.of(id.withPrefix("item/")));
        AllPartialModels.PACKAGE_RIGGING.put(id, PartialModel.of(PocketCaseItem.STYLE.getRiggingModel()));
    }

    private PocketPackageModels() {}
}
