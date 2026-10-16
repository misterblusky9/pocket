package com.misterblusky9.pocket.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface PriorityInteractionItem {
    default boolean claimsBlock(
            Player player,
            ItemStack stack,
            Level level,
            BlockPos pos
    ) {
        return true;
    }

    default boolean claimsEntity(
            Player player,
            ItemStack stack,
            Entity target
    ) {
        return true;
    }

    default boolean claimsInput(Player player, ItemStack stack) {
        return true;
    }
}
