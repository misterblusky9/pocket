package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.ScaleBounds;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;

public final class PocketCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("sethandscale")
                        .then(Commands.argument("scale", StringArgumentType.word())
                                .executes(context -> setHandScale(context.getSource(),
                                        parse(StringArgumentType.getString(context, "scale"))))))
                .then(Commands.literal("usecustomsprite")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> useCustomSprite(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("recover")
                        .executes(context -> PocketRecovery.open(context.getSource(), PocketRecovery.Kind.ALL))
                        .then(Commands.literal("hats")
                                .executes(context -> PocketRecovery.open(context.getSource(), PocketRecovery.Kind.HATS)))
                        .then(Commands.literal("cases")
                                .executes(context -> PocketRecovery.open(context.getSource(), PocketRecovery.Kind.CASES)))));
    }

    private static int useCustomSprite(CommandSourceStack source, boolean on) {
        ServerPlayer player = source.getPlayer();
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (!HatStandBlock.isHeadwear(held) || BuildHostBlockEntity.token(held) == null) {
            source.sendFailure(Component.translatable("pocket.command.usecustomsprite.wrong_item"));
            return 0;
        }
        if (on) held.remove(ModDataComponents.CUSTOM_SPRITE.get());
        else held.set(ModDataComponents.CUSTOM_SPRITE.get(), false);
        source.sendSuccess(() -> Component.translatable(
                on ? "pocket.command.usecustomsprite.on" : "pocket.command.usecustomsprite.off"), false);
        return 1;
    }

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
            source.sendFailure(Component.translatable("pocket.command.sethandscale.invalid"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CreativeShrinkRayItem
                || held.getItem() instanceof SelfResizeDeviceItem)) {
            source.sendFailure(Component.translatable("pocket.command.sethandscale.wrong_item"));
            return 0;
        }
        CreativeShrinkRayItem.setSelectedScale(held, scale);
        source.sendSuccess(() -> Component.translatable("pocket.command.sethandscale.set", scale), true);
        return 1;
    }

    private PocketCommands() {}
}
