package com.misterblusky9.pocket.item;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.compression.PersonalScale;
import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public final class SelfResizeDeviceItem extends Item implements ScaleSelectingItem, PriorityInteractionItem {
    public static final int CAPACITY = 1000;
    public static final int LEVITITE_PER_USE = 100;
    private static final int COOLDOWN_TICKS = 10;

    public SelfResizeDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsBlock(Player player, ItemStack stack, Level level, BlockPos pos) {
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

        return new InteractionResultHolder<>(activate(serverPlayer, stack), stack);
    }

    public static InteractionResult activate(ServerPlayer player, ItemStack stack) {
        double selected = CreativeShrinkRayItem.selectedScale(stack);
        double current = PersonalScale.liveScale(player);
        double seat = seatScale(player);
        if (ScaleBounds.isValid(seat) && !ScaleBounds.same(seat, current) && !ScaleBounds.same(seat, selected)
                && stack.getItem() instanceof SelfResizeDeviceItem device && device.permitsSelection(stack, player, seat)) {
            device.select(stack, seat);
            AllSoundEvents.CONFIRM.playFrom(player, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        double target = ScaleBounds.same(current, selected) ? ScaleBounds.FULL : selected;
        if (ScaleBounds.same(target, current)) return InteractionResult.PASS;

        if (!player.isCreative() && CompressionGunTank.drain(stack, LEVITITE_PER_USE) == 0) {
            player.displayClientMessage(Component.translatable("pocket.message.no_power"), true);
            AllSoundEvents.CARDBOARD_SWORD.playFrom(player, 0.75F, 1.85F);
            return InteractionResult.FAIL;
        }

        PersonalScale.instant(player, target);
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        AllSoundEvents.CONFIRM.playFrom(player, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private static double seatScale(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        boolean seated = vehicle instanceof SeatEntity
                || vehicle instanceof AbstractContraptionEntity contraptionEntity
                        && contraptionEntity.getContraption() != null
                        && contraptionEntity.getContraption().getSeatOf(player.getUUID()) != null;
        if (!seated) return Double.NaN;
        for (Entity current = vehicle; current != null; current = current.getVehicle()) {
            SubLevel subLevel = Sable.HELPER.getContaining(current);
            if (subLevel == null) subLevel = Sable.HELPER.getTrackingSubLevel(current);
            if (subLevel instanceof ServerSubLevel server && !server.isRemoved()) {
                return CompressionStage.snap(Pym.scale().target(server));
            }
        }
        return Double.NaN;
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
        return ScaleBounds.isValid(scale)
                && (selectionRange(player).contains(scale)
                        || EntityCompressionTargeting.carriedNearby(player, CreativeShrinkRayItem.RANGE, scale));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return CompressionGunTank.ENCHANTMENT_VALUE;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return CompressionGunTank.isCapacity(enchantment) || super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return CompressionGunTank.isCapacity(enchantment) || super.isPrimaryItemFor(stack, enchantment);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * CompressionGunTank.amount(stack) / CompressionGunTank.capacity(stack));
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
                CompressionGunTank.amount(stack), CompressionGunTank.capacity(stack)).withStyle(ChatFormatting.GRAY));
    }
}
