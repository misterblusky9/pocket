package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ScaleSelectingItem {
    default ScaleBounds selectionRange(Player player) {
        return CreativeShrinkRayItem.limits(player);
    }

    default ScaleBounds selectionRange(ItemStack stack, Player player) {
        return selectionRange(player);
    }

    default double selection(ItemStack stack, Player player) {
        return CreativeShrinkRayItem.selectedScale(stack, player);
    }

    default void select(ItemStack stack, double scale) {
        CreativeShrinkRayItem.setSelectedScale(stack, scale);
    }

    default boolean permitsSelection(Player player, double scale) {
        return ScaleBounds.isValid(scale) && selectionRange(player).contains(scale);
    }

    default boolean permitsSelection(ItemStack stack, Player player, double scale) {
        return ScaleBounds.isValid(scale) && selectionRange(stack, player).contains(scale);
    }
}
