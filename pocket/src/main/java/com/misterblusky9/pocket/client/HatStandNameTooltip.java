package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.CaseSeal;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketRecovery;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.SchematicScale;
import com.simibubi.create.AllItems;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

public final class HatStandNameTooltip {

    private static final float RAINBOW_SPEED = 1.0F / 2000.0F;
    private static final float RAINBOW_SPREAD = 0.08F;
    private static final int ENDER_TEAL = 0x1E8C8C;
    private static final DateTimeFormatter LAST_SEEN = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    private HatStandNameTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;
        if (AllItems.SCHEMATIC.isIn(stack)) {
            tooltip.add(1, schematic(stack));
            return;
        }
        boolean stand = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof HatStandBlock;
        ResolvableProfile profile = stand ? stack.get(DataComponents.PROFILE) : null;
        NameEgg egg = stand ? NameEgg.of(profile) : NameEgg.of(stack);
        List<Component> lines = new ArrayList<>();
        if (!stand) {
            add(lines, tag(egg));
            if (PocketCaseItem.isUnique(stack)) {
                add(lines, PocketCaseItem.owner(stack));
                PocketCaseItem.details(stack, event.getFlags().isAdvanced(), lines);
            } else {
                add(lines, maker(stack));
            }
            long seen = PocketRecovery.lastSeen(stack);
            if (PocketRecovery.missing(stack)) lines.add(PocketRecovery.MISSING);
            else if (seen > 0L) lines.add(Component.literal(LAST_SEEN.format(Instant.ofEpochMilli(seen)))
                    .withStyle(ChatFormatting.GREEN));
        }
        String name = profile == null ? "" : profile.name().orElse("");
        if (!name.isEmpty()) {
            Style style = Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY);
            lines.add(switch (egg) {
                case JEB -> Component.literal(name).withStyle(style.withColor(HatWool.textColour()));
                case RAINBOW -> rainbow(name, style);
                default -> Component.literal(name).withStyle(style);
            });
        }
        tooltip.addAll(1, lines);
    }

    private static Component schematic(ItemStack stack) {
        Double scale = SchematicScale.read(stack);
        return Component.literal(HudScaleText.compact(scale == null ? ScaleBounds.FULL : scale))
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void add(List<Component> lines, @Nullable Component line) {
        if (line != null) lines.add(line);
    }

    private static @Nullable Component maker(ItemStack stack) {
        if (BuildHostBlockEntity.token(stack) == null) return null;
        CaseSeal seal = stack.get(ModDataComponents.SEAL.get());
        if (seal != null)
            return Component.translatable("pocket.tooltip.sealed_by", seal.ownerName()).withStyle(ChatFormatting.AQUA);
        String creator = BuildHostBlockEntity.creator(stack);
        if (creator == null && PocketRecovery.missing(stack)) return null;
        return Component.translatable("pocket.tooltip.created_by", creator == null ? "???" : creator)
                .withStyle(ChatFormatting.AQUA);
    }

    private static @Nullable Component tag(NameEgg egg) {
        if (egg == NameEgg.NONE) return null;
        String text = Component.translatable("pocket.tooltip.hat_egg." + egg.getSerializedName()).getString();
        Style style = Style.EMPTY.withItalic(false);
        return switch (egg) {
            case RAINBOW -> rainbow(text, style, 0.0F);
            case VOID -> Component.literal(text).withStyle(style.withColor(ENDER_TEAL));
            case MELONS -> Component.literal(text).withStyle(style.withItalic(true).withColor(ChatFormatting.GRAY));
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
