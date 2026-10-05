package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CompressionGunTank;
import com.misterblusky9.pocket.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class PocketItemProperties {
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "empty");

    public static void register(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.SELF_RESIZE_DEVICE.get(), EMPTY,
                (stack, level, entity, seed) -> CompressionGunTank.amount(stack) > 0 ? 0.0F : 1.0F));
    }

    private PocketItemProperties() {}
}
