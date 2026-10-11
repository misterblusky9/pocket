package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class PackagePickupSizeGuard {

    private PackagePickupSizeGuard() {}

    @SubscribeEvent
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof final PackageEntity box)) return;
        if (!event.getItemStack().isEmpty()) return;

        final Player player = event.getEntity();
        final double size = Pym.entities().scaleOf(box);
        final double reach = Pym.entities().scaleOf(player);
        if (size <= reach || ScaleBounds.same(size, reach)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide) {
            player.displayClientMessage(Component.translatable("pocket.message.too_big"), true);
        }
    }
}
