package com.misterblusky9.pocket.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class EmptyBoxItem extends Item {
    public EmptyBoxItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            final ItemStack stack,
            final TooltipContext context,
            final List<Component> tooltip,
            final TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.pocket.empty_box.tooltip.hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
