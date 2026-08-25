package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.AllDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class CreativeShrinkRayInteractionHandler {
    private CreativeShrinkRayInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        final ItemStack held = event.getEntity().getMainHandItem();
        if (!(held.getItem() instanceof CreativeShrinkRayItem)) return;

        held.remove(AllDataComponents.SHAPER_BLOCK_USED);
        held.remove(AllDataComponents.SHAPER_BLOCK_DATA);

        event.setCanceled(true);
    }
}
