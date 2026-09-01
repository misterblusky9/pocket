package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.client.CompressionGunTargetingScreen;
import com.misterblusky9.pocket.client.CreativeShrinkRayRenderer;
import com.misterblusky9.pocket.client.MoonScaleClient;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.moon.MoonCompressionSessions;
import com.misterblusky9.pocket.moon.MoonScale;
import com.misterblusky9.pocket.moon.MoonTargeting;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods;
import com.simibubi.create.content.equipment.zapper.ZapperBeamPacket;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class CreativeShrinkRayItem extends ZapperItem {
    private static final String STAGE_KEY = "PocketStage";
    private static final String TARGETING_MODE_KEY = "PocketTargetingMode";
    private static final double RANGE = 192.0D;

    public CreativeShrinkRayItem(final Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CreativeShrinkRayRenderer()));
    }

    public static CompressionStage selectedStage(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return CompressionStage.NORMAL;
        final CompoundTag tag = custom.copyTag();
        return CompressionStage.fromDepth(tag.getInt(STAGE_KEY));
    }

    public static void setSelectedStage(final ItemStack stack, final CompressionStage stage) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putInt(STAGE_KEY, stage == null ? 0 : stage.depth());
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
    public InteractionResultHolder<ItemStack> use(
            final Level level,
            final Player player,
            final InteractionHand hand
    ) {
        final ItemStack stack = player.getItemInHand(hand);
        final CompressionGunTargetingMode targeting = targetingMode(stack);

        if (!player.isShiftKeyDown() && targeting == CompressionGunTargetingMode.SELF) {
            if (!level.isClientSide && player instanceof final ServerPlayer serverPlayer) {
                if (!PehkuiScaleBridge.isOperational()) {
                    player.displayClientMessage(Component.literal("Pehkui integration unavailable"), true);
                } else {
                    com.misterblusky9.pocket.compression.SelfCompressionSessions.instant(
                            serverPlayer, selectedStage(stack));
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (!player.isShiftKeyDown() && targeting != CompressionGunTargetingMode.SELF) {
            final float moonScale = level.isClientSide
                    ? MoonScaleClient.get()
                    : player instanceof final ServerPlayer serverPlayer
                            ? MoonScale.get(serverPlayer.serverLevel().getServer())
                            : 1.0F;

            final MoonTargeting.Hit moonHit = MoonTargeting.hit(
                    player, moonScale, 1.0F, RANGE);
            if (moonHit != null) {
                if (ShootableGadgetItemMethods.shouldSwap(player, stack, hand, this::isZapper)) {
                    return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
                }

                if (level.isClientSide) {
                    CreateClient.ZAPPER_RENDER_HANDLER.dontAnimateItem(hand);
                    return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
                }

                if (player instanceof final ServerPlayer serverPlayer) {
                    MoonCompressionSessions.instant(serverPlayer, selectedStage(stack), moonHit);
                    ShootableGadgetItemMethods.applyCooldown(
                            player,
                            stack,
                            hand,
                            this::isZapper,
                            getCooldownDelay(stack)
                    );
                    final Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                            player,
                            hand == InteractionHand.MAIN_HAND,
                            new Vec3(0.35D, -0.1D, 1.0D)
                    );
                    ShootableGadgetItemMethods.sendPackets(
                            player,
                            local -> new ZapperBeamPacket(barrel, hand, local, moonHit.worldPoint())
                    );
                }

                return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
            }
        }

        return super.use(level, player, hand);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void openHandgunGUI(final ItemStack item, final InteractionHand hand) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new CompressionGunTargetingScreen(item, hand)
        );
    }

    @Override
    public @Nullable Component validateUsage(final ItemStack item) {
        return null;
    }

    @Override
    protected int getZappingRange(final ItemStack stack) {
        return (int) RANGE;
    }

    @Override
    protected int getCooldownDelay(final ItemStack stack) {
        return 2;
    }

    @Override
    public void inventoryTick(
            final ItemStack stack,
            final Level level,
            final Entity entity,
            final int slotId,
            final boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        stack.remove(AllDataComponents.SHAPER_BLOCK_USED);
        stack.remove(AllDataComponents.SHAPER_BLOCK_DATA);
    }

    @Override
    protected boolean canActivateWithoutSelectedBlock(final ItemStack stack) {
        return true;
    }

    @Override
    protected boolean activate(
            final Level level,
            final Player player,
            final ItemStack stack,
            final BlockState stateToUse,
            final BlockHitResult raytrace,
            final CompoundTag data
    ) {
        if (level.isClientSide) return true;

        final CompressionStage target = selectedStage(stack);
        final CompressionGunTargetingMode targeting = targetingMode(stack);
        if (targeting == CompressionGunTargetingMode.SELF) {
            if (!(player instanceof final ServerPlayer serverPlayer)) return false;
            if (!PehkuiScaleBridge.isOperational()) {
                player.displayClientMessage(Component.literal("Pehkui integration unavailable"), true);
                return false;
            }
            if (com.misterblusky9.pocket.compression.SelfCompressionSessions
                    .currentStage(serverPlayer) == target) {
                return false;
            }
            com.misterblusky9.pocket.compression.SelfCompressionSessions.instant(serverPlayer, target);
            return true;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(level, raytrace.getLocation());
        if (subLevel == null) subLevel = Sable.HELPER.getContaining(level, raytrace.getBlockPos());

        boolean lockedOn = false;
        if (!(subLevel instanceof ServerSubLevel)
                && player instanceof final ServerPlayer holder) {
            subLevel = com.misterblusky9.pocket.compression.CompressionSessions.lockedSubLevel(holder);
            lockedOn = subLevel != null;
        }

        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) return true;

        final net.minecraft.core.BlockPos contact =
                lockedOn ? centreOf(serverSubLevel) : raytrace.getBlockPos();

        if (ScaleState.isSettled(serverSubLevel.getUniqueId())
                && ScaleState.getStage(serverSubLevel) == target) {
            return false;
        }

        if (target.isCompressed()) {
            final int blocks = PocketMetrics.measureForCompression(serverSubLevel, level.getGameTime()).blocks();
            if (blocks > PocketSized.MAX_COMPRESSED_BLOCKS) {
                player.displayClientMessage(Component.literal("Pocket Sized hard limit: " + PocketSized.MAX_COMPRESSED_BLOCKS + " blocks"), true);
                return false;
            }
        }

        if (player instanceof final ServerPlayer serverPlayer) {
            com.misterblusky9.pocket.compression.CompressionSessions.instant(
                    serverPlayer, serverSubLevel, contact, target,
                    targeting == CompressionGunTargetingMode.CONNECTED_SUBLEVELS
            );
            return true;
        }

        ScaleController.forceStage(
                serverSubLevel, target, level.getGameTime(), null,
                targeting == CompressionGunTargetingMode.CONNECTED_SUBLEVELS
        );
        return true;
    }

    private static net.minecraft.core.BlockPos centreOf(final ServerSubLevel subLevel) {
        final var bounds = subLevel.getPlot().getBoundingBox();
        return new net.minecraft.core.BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
    }
}
