package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.CompressionGunRenderer;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.compression.SelfCompressionSessions;
import com.misterblusky9.pocket.compression.CompressionTargeting;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.network.CompressionBeamPayload;
import com.misterblusky9.pocket.network.CompressionGunOpenMenuPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public final class CompressionGunItem extends Item implements CustomArmPoseItem {
    private static final double RANGE = 160.0D;

    public static final int CHARGE_TICKS = 40;

    private static final String MODE_KEY = "PocketGrow";
    private static final String TARGETING_MODE_KEY = "PocketTargetingMode";

    private static final CompressionStage SURVIVAL_FLOOR = CompressionStage.SIXTEENTH;

    private static final int NOMINAL_AIR_COST = 60;

    public CompressionGunItem(final Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CompressionGunRenderer()));
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            final ItemStack oldStack,
            final ItemStack newStack,
            final boolean slotChanged
    ) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            final Level level,
            final Player player,
            final InteractionHand hand
    ) {
        final ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof final ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new CompressionGunOpenMenuPayload(hand));
            }
            return InteractionResultHolder.success(stack);
        }

        player.startUsingItem(hand);
        if (player instanceof final ServerPlayer serverPlayer
                && targetingMode(stack) != CompressionGunTargetingMode.SELF) {
            CompressionBeamPayload.send(serverPlayer, true, isGrowing(stack));
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(
            final Level level,
            final LivingEntity entity,
            final ItemStack stack,
            final int remainingUseTicks
    ) {
        if (level.isClientSide || !(entity instanceof final ServerPlayer player)) return;

        final int elapsed = getUseDuration(stack, entity) - remainingUseTicks;
        if (elapsed < CHARGE_TICKS) return;

        final boolean growing = isGrowing(stack);
        final CompressionStage goal = growing ? CompressionStage.NORMAL : SURVIVAL_FLOOR;
        final CompressionGunTargetingMode targeting = targetingMode(stack);

        if (targeting == CompressionGunTargetingMode.SELF) {
            if (elapsed < CHARGE_TICKS) return;

            if (!PehkuiScaleBridge.isOperational()) {
                player.displayClientMessage(Component.literal("Pehkui integration unavailable"), true);
                return;
            }

            if (SelfCompressionSessions.renew(player, goal)) return;
            SelfCompressionSessions.begin(player, goal, player.getUsedItemHand(), growing);
            return;
        }

        if (CompressionSessions.renew(player, goal)) return;

        final CompressionTargeting.Target target = CompressionTargeting.find(player, RANGE);
        if (target == null) return;

        CompressionSessions.hold(
                player,
                target.subLevel(),
                target.hitLocalPos(),
                goal,
                false,
                player.getUsedItemHand(),
                growing,
                targeting == CompressionGunTargetingMode.CONNECTED_SUBLEVELS
        );
    }

    @Override
    public void releaseUsing(
            final ItemStack stack,
            final Level level,
            final LivingEntity entity,
            final int remainingUseTicks
    ) {
        if (!level.isClientSide && entity instanceof final ServerPlayer player) {
            CompressionSessions.releaseAll(player);
            SelfCompressionSessions.release(player);
            CompressionBeamPayload.send(player, false, false);
        }
    }

    @Override
    public void onStopUsing(final ItemStack stack, final LivingEntity entity, final int count) {
        if (!entity.level().isClientSide && entity instanceof final ServerPlayer player) {
            CompressionSessions.releaseAll(player);
            SelfCompressionSessions.release(player);
            CompressionBeamPayload.send(player, false, false);
        }
    }

    public static boolean isGrowing(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().getBoolean(MODE_KEY);
    }

    public static void setGrowing(final ItemStack stack, final boolean growing) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putBoolean(MODE_KEY, growing);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CompressionGunTargetingMode targetingMode(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return CompressionGunTargetingMode.SUBLEVEL;

        final CompressionGunTargetingMode mode = CompressionGunTargetingMode.fromId(
                custom.copyTag().getInt(TARGETING_MODE_KEY)
        );
        if (mode == CompressionGunTargetingMode.SELF && !PehkuiScaleBridge.ownsScaling()) {
            return CompressionGunTargetingMode.SUBLEVEL;
        }
        return mode;
    }

    public static void setTargetingMode(
            final ItemStack stack,
            final CompressionGunTargetingMode requested
    ) {
        CompressionGunTargetingMode mode = requested == null
                ? CompressionGunTargetingMode.SUBLEVEL
                : requested;
        if (mode == CompressionGunTargetingMode.SELF && !PehkuiScaleBridge.ownsScaling()) {
            mode = CompressionGunTargetingMode.SUBLEVEL;
        }

        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putInt(TARGETING_MODE_KEY, mode.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public int getUseDuration(final ItemStack stack, final LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(final ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(
            final ItemStack stack,
            final AbstractClientPlayer player,
            final InteractionHand hand
    ) {
        return player.swinging ? null : HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Override
    public boolean isBarVisible(final ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, NOMINAL_AIR_COST);
    }

    @Override
    public int getBarWidth(final ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, NOMINAL_AIR_COST);
    }

    @Override
    public int getBarColor(final ItemStack stack) {
        return BacktankUtil.getBarColor(stack, NOMINAL_AIR_COST);
    }
}
