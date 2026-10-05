package com.misterblusky9.pocket.block;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public interface SwitchControllerBlockEntity {
    boolean onContraptionInteraction(Player player, InteractionHand hand);

    int getRedstonePower();
}
