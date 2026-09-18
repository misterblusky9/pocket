package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.network.ShrinkRayScalePayload;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.AllDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class CreativeShrinkRayInteractionHandler {
    public static java.util.function.DoubleConsumer clientFeedback = scale -> {};

    private CreativeShrinkRayInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        final ItemStack held = event.getEntity().getMainHandItem();
        if (!(held.getItem() instanceof CreativeShrinkRayItem)) return;

        held.remove(AllDataComponents.SHAPER_BLOCK_USED);
        held.remove(AllDataComponents.SHAPER_BLOCK_DATA);

        final SubLevel subLevel = Sable.HELPER.getContaining(event.getLevel(), event.getPos());
        if (subLevel != null && !subLevel.isRemoved()) {
            pickScale(event.getEntity(), held, ScaleState.getSettledScale(subLevel));
        }

        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(final AttackEntityEvent event) {
        final ItemStack held = event.getEntity().getMainHandItem();
        if (!(held.getItem() instanceof CreativeShrinkRayItem)) return;

        event.setCanceled(true);
    }

    private static void pickScale(
            final net.minecraft.world.entity.player.Player player,
            final ItemStack ray,
            final double scale
    ) {
        if (!CreativeShrinkRayItem.permits(player, scale)) return;
        CreativeShrinkRayItem.setSelectedScale(ray, scale);
        if (player.level().isClientSide) {
            PacketDistributor.sendToServer(new ShrinkRayScalePayload(InteractionHand.MAIN_HAND, scale));
            clientFeedback.accept(CreativeShrinkRayItem.selectedScale(ray, player));
        }
    }
}
