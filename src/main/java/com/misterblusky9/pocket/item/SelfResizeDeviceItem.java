package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.SelfCompressionSessions;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.scale.ScaleController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class SelfResizeDeviceItem extends Item {
    public static final int CAPACITY = 1000;
    public static final int LEVITITE_PER_USE = 100;
    private static final int COOLDOWN_TICKS = 10;

    public SelfResizeDeviceItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            final Level level,
            final Player player,
            final InteractionHand hand
    ) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof final ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (!ModList.get().isLoaded("pehkui")) {
            player.displayClientMessage(
                    Component.translatable("pocket.message.requires_pehkui").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        if (!PehkuiScaleBridge.isOperational()) {
            player.displayClientMessage(Component.translatable("pocket.message.pehkui_unavailable"), true);
            return InteractionResultHolder.fail(stack);
        }

        final double selected = CreativeShrinkRayItem.selectedScale(stack, player);
        final double current = SelfCompressionSessions.currentScale(serverPlayer);
        final double target = ScaleController.sameScale(current, selected) ? PocketSized.FULL_SCALE : selected;
        if (ScaleController.sameScale(target, current)) return InteractionResultHolder.pass(stack);

        if (!player.isCreative()) {
            if (CompressionGunTank.amount(stack) >= LEVITITE_PER_USE) {
                CompressionGunTank.drain(stack, LEVITITE_PER_USE);
            } else if (!CompressionGunTank.runEngineOnce(serverPlayer, stack, hand)) {
                player.displayClientMessage(Component.translatable("pocket.message.levitite_depleted"), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        SelfCompressionSessions.instant(serverPlayer, target);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isBarVisible(final ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(final ItemStack stack) {
        return Math.round(13.0F * CompressionGunTank.amount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(final ItemStack stack) {
        return CompressionGunItem.LEVITITE_BAR_COLOUR;
    }
}
