package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.CompressionGunRenderer;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.compression.CompressionTargeting;
import com.misterblusky9.pocket.compression.SelfCompressionSessions;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.moon.MoonCompressionSessions;
import com.misterblusky9.pocket.moon.MoonScale;
import com.misterblusky9.pocket.moon.MoonTargeting;
import com.misterblusky9.pocket.network.CompressionBeamPayload;
import com.misterblusky9.pocket.network.CompressionGunOpenMenuPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CompressionGunItem extends Item implements CustomArmPoseItem, PriorityInteractionItem {
    private static final double RANGE = 160.0D;

    public static final int CHARGE_TICKS = 40;

    private static final String MODE_KEY = "PocketGrow";
    private static final String TARGETING_MODE_KEY = "PocketTargetingMode";

    private static final CompressionStage SURVIVAL_FLOOR = CompressionStage.SIXTEENTH;

    private static final int LEVITITE_BAR_COLOUR = 0x46C8BE;

    private static final Map<UUID, Boolean> BEAMS = new ConcurrentHashMap<>();

    private final boolean pearlescent;

    public CompressionGunItem(final Properties properties, final boolean pearlescent) {
        super(properties);
        this.pearlescent = pearlescent;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CompressionGunRenderer(this.pearlescent)));
    }

    @Override
    public boolean claimsEntity(final Player player, final ItemStack stack, final Entity target) {
        return !(target instanceof ItemFrame) && !(target instanceof ArmorStand);
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
        if (player instanceof final ServerPlayer serverPlayer) {
            BEAMS.remove(serverPlayer.getUUID());
            CompressionGunTank.stopEngine(serverPlayer);
            if (targetingMode(stack) != CompressionGunTargetingMode.SELF
                    && CompressionGunTank.canFire(serverPlayer, stack)) {
                beam(serverPlayer, true, isGrowing(stack));
            }
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
        final boolean growing = isGrowing(stack);
        final CompressionGunTargetingMode targeting = targetingMode(stack);

        if (!CompressionGunTank.runEngine(player, stack, player.getUsedItemHand(), elapsed)) {
            player.displayClientMessage(Component.literal("No air pressure"), true);
            shutDown(player);
            return;
        }

        final boolean fueled = CompressionGunTank.amount(stack) > 0;
        if (elapsed < CHARGE_TICKS) {
            if (targeting != CompressionGunTargetingMode.SELF) beam(player, fueled, growing);
            return;
        }

        final CompressionStage goal = growing ? CompressionStage.NORMAL : SURVIVAL_FLOOR;

        if (targeting == CompressionGunTargetingMode.SELF) {
            if (!PehkuiScaleBridge.isOperational()) {
                player.displayClientMessage(Component.literal("Pehkui integration unavailable"), true);
                return;
            }

            if (SelfCompressionSessions.renew(player, goal)) return;
            if (!fueled) {
                player.displayClientMessage(Component.literal("Levitite Blend depleted"), true);
                return;
            }
            SelfCompressionSessions.begin(player, goal, player.getUsedItemHand(), growing);
            return;
        }

        if (MoonCompressionSessions.renew(player, goal)) return;
        if (CompressionSessions.renew(player, goal)) return;

        if (!fueled) {
            player.displayClientMessage(Component.literal("Levitite Blend depleted"), true);
            beam(player, false, growing);
            return;
        }
        beam(player, true, growing);

        final MoonTargeting.Hit moonHit = MoonTargeting.hit(
                player,
                MoonScale.get(player.serverLevel().getServer()),
                1.0F,
                RANGE
        );
        if (moonHit != null) {
            MoonCompressionSessions.hold(player, goal, player.getUsedItemHand(), moonHit, growing);
            return;
        }

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
            shutDown(player);
            CompressionGunTank.stopEngine(player);
            BEAMS.remove(player.getUUID());
            CompressionBeamPayload.send(player, false, false);
        }
    }

    @Override
    public void onStopUsing(final ItemStack stack, final LivingEntity entity, final int count) {
        if (!entity.level().isClientSide && entity instanceof final ServerPlayer player) {
            shutDown(player);
            CompressionGunTank.stopEngine(player);
            BEAMS.remove(player.getUUID());
            CompressionBeamPayload.send(player, false, false);
        }
    }

    private static void shutDown(final ServerPlayer player) {
        CompressionSessions.releaseAll(player);
        MoonCompressionSessions.release(player);
        SelfCompressionSessions.release(player);
        beam(player, false, false);
    }

    private static void beam(final ServerPlayer player, final boolean firing, final boolean growing) {
        final UUID id = player.getUUID();
        final Boolean current = BEAMS.get(id);
        if (firing) {
            if (current != null && current == growing) return;
            BEAMS.put(id, growing);
        } else {
            if (current == null) return;
            BEAMS.remove(id);
        }
        CompressionBeamPayload.send(player, firing, growing);
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
        return CompressionGunTank.amount(stack) < CompressionGunTank.CAPACITY;
    }

    @Override
    public int getBarWidth(final ItemStack stack) {
        return Math.round(13.0F * CompressionGunTank.amount(stack) / CompressionGunTank.CAPACITY);
    }

    @Override
    public int getBarColor(final ItemStack stack) {
        return LEVITITE_BAR_COLOUR;
    }

    @Override
    public void appendHoverText(
            final ItemStack stack,
            final TooltipContext context,
            final List<Component> tooltip,
            final TooltipFlag flag
    ) {
        tooltip.add(Component.literal("Levitite Blend: " + CompressionGunTank.amount(stack)
                + " / " + CompressionGunTank.CAPACITY + " mB").withStyle(ChatFormatting.GRAY));
    }
}
