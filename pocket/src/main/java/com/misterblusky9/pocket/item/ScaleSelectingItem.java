package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ScaleSelectingItem {
    default ScaleBounds selectionRange(final Player player) {
        return CreativeShrinkRayItem.limits(player);
    }

    default double selection(final ItemStack stack, final Player player) {
        return CreativeShrinkRayItem.selectedScale(stack, player);
    }

    default void select(final ItemStack stack, final double scale) {
        CreativeShrinkRayItem.setSelectedScale(stack, scale);
    }

    default boolean permitsSelection(final Player player, final double scale) {
        return ScaleBounds.isValid(scale) && selectionRange(player).contains(scale);
    }
}
