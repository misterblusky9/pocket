package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.debug.ColliderDebugLayers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ColliderWandItem extends Item {
    public ColliderWandItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            final Level level,
            final Player player,
            final InteractionHand hand
    ) {
        final ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            player.displayClientMessage(
                    Component.literal("Collider wand: " + ColliderDebugLayers.cycle().label()), true);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
