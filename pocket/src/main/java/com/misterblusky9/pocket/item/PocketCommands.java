package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.ScaleBounds;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PocketCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("sethandscale")
                        .then(Commands.argument("scale", StringArgumentType.word())
                                .executes(context -> setHandScale(context.getSource(),
                                        parse(StringArgumentType.getString(context, "scale")))))));
    }

    /** Decimal ("0.0625") or fraction ("1/16"); NaN when unparseable. */
    private static double parse(String text) {
        try {
            int slash = text.indexOf('/');
            return slash < 0 ? Double.parseDouble(text)
                    : Double.parseDouble(text.substring(0, slash))
                            / Double.parseDouble(text.substring(slash + 1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int setHandScale(CommandSourceStack source, double scale) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !ScaleBounds.isValid(scale)) {
            source.sendFailure(Component.literal("Needs a player and a positive scale, e.g. 0.0625 or 1/16"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CreativeShrinkRayItem
                || held.getItem() instanceof SelfResizeDeviceItem)) {
            source.sendFailure(Component.literal("Hold a creative shrink ray or personal compressor"));
            return 0;
        }
        CreativeShrinkRayItem.setSelectedScale(held, scale);
        source.sendSuccess(() -> Component.literal("Set held scale to " + scale), true);
        return 1;
    }

    private PocketCommands() {}
}
