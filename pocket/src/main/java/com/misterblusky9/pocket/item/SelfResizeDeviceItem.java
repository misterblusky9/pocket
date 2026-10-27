package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.compression.PersonalScale;
import com.misterblusky9.pocket.config.DeviceRanges;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public final class SelfResizeDeviceItem extends Item implements ScaleSelectingItem, PriorityInteractionItem {
    public static final int CAPACITY = 1000;
    public static final int LEVITITE_PER_USE = 100;
    public static final int DURABILITY_PER_USE = 4;
    private static final int COOLDOWN_TICKS = 10;

    public SelfResizeDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsBlock(Player player, ItemStack stack, Level level, BlockPos pos) {
        // Sneaking intentionally bypasses PSC activation; respect normal block
        // interactions in that mode.
        return !player.isShiftKeyDown();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        double selected = selection(stack, player);
        double current = PersonalScale.currentScale(serverPlayer);
        double target = ScaleBounds.same(current, selected) ? ScaleBounds.FULL : selected;
        if (ScaleBounds.same(target, current)) return InteractionResultHolder.pass(stack);

        if (!CompressionGunTank.consumeForActivation(
                serverPlayer, stack, LEVITITE_PER_USE, DURABILITY_PER_USE)) {
            player.displayClientMessage(Component.translatable("pocket.message.no_power"), true);
            return InteractionResultHolder.fail(stack);
        }

        PersonalScale.instant(serverPlayer, target);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (!player.isCreative()
                && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage()
                && CompressionGunTank.amount(stack) < LEVITITE_PER_USE) {
            player.onEquippedItemBroken(stack.getItem(), LivingEntity.getSlotForHand(hand));
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public ScaleBounds selectionRange(Player player) {
        return DeviceRanges.of(DeviceRanges.Device.PERSONAL_COMPRESSOR);
    }

    @Override
    public double selection(ItemStack stack, Player player) {
        return CreativeShrinkRayItem.selectedScale(stack);
    }

    @Override
    public boolean permitsSelection(ItemStack stack, Player player, double scale) {
        return ScaleBounds.isValid(scale);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * CompressionGunTank.amount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return CompressionGunItem.LEVITITE_BAR_COLOUR;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("pocket.tooltip.levitite",
                CompressionGunTank.amount(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
    }
}
