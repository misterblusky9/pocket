package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class PocketItemTooltips {
    public static void register(final FMLClientSetupEvent event) {
        describe(ModItems.CREATIVE_SHRINK_RAY.get(), FontHelper.Palette.PURPLE);
        describe(ModItems.COMPRESSION_GUN.get(), FontHelper.Palette.STANDARD_CREATE);
        describe(ModItems.PEARLESCENT_COMPRESSION_GUN.get(), FontHelper.Palette.STANDARD_CREATE);
        describe(ModItems.SELF_RESIZE_DEVICE.get(), FontHelper.Palette.STANDARD_CREATE);
    }

    private static void describe(final Item item, final FontHelper.Palette palette) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, palette));
    }

    private PocketItemTooltips() {}
}
