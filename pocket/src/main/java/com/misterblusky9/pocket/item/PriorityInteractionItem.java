package com.misterblusky9.pocket.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface PriorityInteractionItem {
    default boolean claimsBlock(
            final Player player,
            final ItemStack stack,
            final Level level,
            final BlockPos pos
    ) {
        return true;
    }

    default boolean claimsEntity(
            final Player player,
            final ItemStack stack,
            final Entity target
    ) {
        return true;
    }

    default boolean claimsInput(final Player player, final ItemStack stack) {
        return true;
    }
}
