package com.misterblusky9.pocket.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TweezersItem extends Item {
    public static final float RANGE = 16.0F;

    public TweezersItem(final Properties properties) {
        super(properties);
    }

    public static boolean isHolding(final Player player) {
        return player != null
                && (player.getMainHandItem().getItem() instanceof TweezersItem
                || player.getOffhandItem().getItem() instanceof TweezersItem);
    }

    @Override
    public boolean canAttackBlock(
            final BlockState state, final Level level, final BlockPos pos, final Player player
    ) {
        return false;
    }
}
