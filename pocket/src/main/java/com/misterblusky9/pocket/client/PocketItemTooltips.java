package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;
import java.util.Locale;

public final class PocketItemTooltips {
    private static final String PSC_KEY_LINE = "item.pocket.self_resize_device.tooltip.condition4";
    private static final String GLUE_ROTATE_LINE = "item.pocket.glue_gun.tooltip.condition4";

    public static void register(FMLClientSetupEvent event) {
        describe(ModItems.CREATIVE_SHRINK_RAY.get(), FontHelper.Palette.PURPLE);
        describe(ModItems.COMPRESSION_GUN.get(), FontHelper.Palette.STANDARD_CREATE);
        describe(ModItems.PEARLESCENT_COMPRESSION_GUN.get(), FontHelper.Palette.STANDARD_CREATE);
        describe(ModItems.SELF_RESIZE_DEVICE.get(), FontHelper.Palette.STANDARD_CREATE,
                context -> showBoundKey(context.getToolTip(), PocketKeys.PERSONAL_COMPRESSOR, PSC_KEY_LINE));
        describe(ModItems.GLUE_GUN.get(), FontHelper.Palette.STANDARD_CREATE,
                context -> showBoundKey(context.getToolTip(), PocketKeys.WELD_ROTATE, GLUE_ROTATE_LINE));
        describe(ModItems.POCKET_KNIFE.get(), FontHelper.Palette.STANDARD_CREATE);
    }

    private static void describe(Item item, FontHelper.Palette palette) {
        describe(item, palette, TooltipModifier.EMPTY);
    }

    private static void describe(Item item, FontHelper.Palette palette, TooltipModifier after) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, palette).andThen(after));
    }

    private static void showBoundKey(List<Component> tooltip, KeyMapping mapping, String line) {
        if (mapping.isUnbound()) return;
        String placeholder = I18n.get(line);
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().equals(placeholder)) {
                String key = mapping.getTranslatedKeyMessage().getString();
                tooltip.set(i, Component.translatable(line + ".bound", key.toUpperCase(Locale.ROOT))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private PocketItemTooltips() {}
}
