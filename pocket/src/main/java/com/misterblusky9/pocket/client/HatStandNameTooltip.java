package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HatStandNameTooltip {

    private static final float RAINBOW_SPEED = 1.0F / 2000.0F;
    private static final float RAINBOW_SPREAD = 0.08F;
    private static final int ENDER_TEAL = 0x1E8C8C;

    private HatStandNameTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;
        boolean stand = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof HatStandBlock;
        ResolvableProfile profile = stand ? stack.get(DataComponents.PROFILE) : null;
        NameEgg egg = stand ? NameEgg.of(profile) : NameEgg.of(stack);
        int at = 1;
        Component tag = stand ? null : tag(egg);
        if (tag != null) tooltip.add(at++, tag);
        String name = profile == null ? "" : profile.name().orElse("");
        if (name.isEmpty()) return;
        Style style = Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY);
        Component line = switch (egg) {
            case JEB -> Component.literal(name).withStyle(style.withColor(HatWool.textColour()));
            case RAINBOW -> rainbow(name, style);
            default -> Component.literal(name).withStyle(style);
        };
        tooltip.add(at, line);
    }

    private static @Nullable Component tag(NameEgg egg) {
        if (egg == NameEgg.NONE) return null;
        String text = Component.translatable("pocket.tooltip.hat_egg." + egg.getSerializedName()).getString();
        Style style = Style.EMPTY.withItalic(false);
        return switch (egg) {
            case RAINBOW -> rainbow(text, style, 0.0F);
            case VOID -> Component.literal(text).withStyle(style.withColor(ENDER_TEAL));
            default -> Component.literal(text).withStyle(style.withColor(ChatFormatting.GRAY));
        };
    }

    public static Component rainbow(String text, Style style) {
        return rainbow(text, style, (Util.getMillis() * RAINBOW_SPEED) % 1.0F);
    }

    private static Component rainbow(String text, Style style, float start) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < text.length(); i++)
            line.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style.withColor(Mth.hsvToRgb(Mth.positiveModulo(i * RAINBOW_SPREAD - start, 1.0F), 0.8F, 1.0F))));
        return line;
    }
}
