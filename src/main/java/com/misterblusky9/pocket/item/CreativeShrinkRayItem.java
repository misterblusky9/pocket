package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.Overclocking;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.client.CompressionGunTargetingScreen;
import com.misterblusky9.pocket.client.CreativeShrinkRayRenderer;
import com.misterblusky9.pocket.client.MoonScaleClient;
import com.misterblusky9.pocket.compression.EntityCompressionSessions;
import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.moon.MoonCompressionSessions;
import com.misterblusky9.pocket.moon.MoonScale;
import com.misterblusky9.pocket.moon.MoonTargeting;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.network.ShrinkRayBeamColourPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pocket.scale.ScaleLimits;
import com.misterblusky9.pocket.scale.ScalePhysicsMode;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class CreativeShrinkRayItem extends ZapperItem implements PriorityInteractionItem {
    private static final ThreadLocal<Boolean> FIRED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String SCALE_KEY = "PocketScale";
    private static final String STAGE_KEY = "PocketStage";
    private static final String TARGETING_MODE_KEY = "PocketTargetingMode";
    public static final double RANGE = 192.0D;

    public CreativeShrinkRayItem(final Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CreativeShrinkRayRenderer()));
    }

    public static CompressionStage selectedStage(final ItemStack stack, final Player player) {
        return CompressionStage.nearest(selectedScale(stack, player));
    }

    public static void setSelectedStage(final ItemStack stack, final CompressionStage stage) {
        setSelectedScale(stack, stage == null ? PocketSized.FULL_SCALE : stage.scale());
    }

    public static double[] ladder(final Player player) {
        return Overclocking.ladder(player, ScaleLadder.CREATIVE);
    }

    public static ScaleLimits limits(final Player player) {
        return Overclocking.limits(player, ScaleLimits.CREATIVE);
    }

    public static boolean permits(final Player player, final double scale) {
        final ScaleLimits limits = limits(player);
        return PocketSized.isValidScale(scale)
                && scale >= limits.min() - PocketSized.EPSILON
                && scale <= limits.max() + PocketSized.EPSILON;
    }

    public static double selectedScale(final ItemStack stack, final Player player) {
        final double scale = selectedScale(stack);
        return limits(player).clamp(scale);
    }

    public static double selectedScale(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return PocketSized.FULL_SCALE;
        final CompoundTag tag = custom.copyTag();
        if (tag.contains(SCALE_KEY, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                && PocketSized.isValidScale(tag.getDouble(SCALE_KEY))) {
            return CompressionStage.snap(tag.getDouble(SCALE_KEY));
        }
        return CompressionStage.fromDepth(tag.getInt(STAGE_KEY)).scale();
    }

    public static void setSelectedScale(final ItemStack stack, final double scale) {
        if (!PocketSized.isValidScale(scale)) return;
        final double clamped = CompressionStage.snap(scale);
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putDouble(SCALE_KEY, clamped);
        tag.putInt(STAGE_KEY, CompressionStage.nearest(clamped).depth());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CompressionGunTargetingMode targetingMode(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return CompressionGunTargetingMode.SUBLEVEL;

        return CompressionGunTargetingMode.fromId(custom.copyTag().getInt(TARGETING_MODE_KEY));
    }

    public static void setTargetingMode(
            final ItemStack stack,
            final CompressionGunTargetingMode requested
    ) {
        final CompressionGunTargetingMode mode = requested == null
                ? CompressionGunTargetingMode.SUBLEVEL
                : requested;

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

        if (!player.isShiftKeyDown()) {
            final EntityCompressionTargeting.Target entityTarget =
                    EntityCompressionTargeting.find(player, RANGE);
            if (entityTarget != null) {
                if (ShootableGadgetItemMethods.shouldSwap(player, stack, hand, this::isZapper)) {
                    return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
                }

                if (level.isClientSide) {
                    CreateClient.ZAPPER_RENDER_HANDLER.dontAnimateItem(hand);
                    return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
                }

                if (player instanceof final ServerPlayer serverPlayer) {
                    final double targetScale = selectedScale(stack, player);
                    final double currentScale = EntityCompressionTargeting.scale(entityTarget.entity());
                    final int beamColour = ScaleController.sameScale(currentScale, targetScale)
                            ? ShrinkRayBeamColourPayload.INERT_COLOUR
                            : targetScale > currentScale
                                    ? ShrinkRayBeamColourPayload.GROW_COLOUR
                                    : ShrinkRayBeamColourPayload.SHRINK_COLOUR;

                    EntityCompressionSessions.creative(
                            serverPlayer, entityTarget.entity(), targetScale);
                    ShootableGadgetItemMethods.applyCooldown(
                            player, stack, hand, this::isZapper, getCooldownDelay(stack));

                    final Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                            player,
                            hand == InteractionHand.MAIN_HAND,
                            new Vec3(0.35D, -0.1D, 1.0D)
                    );
                    ShrinkRayBeamColourPayload.send(serverPlayer, entityTarget.hitPos(), beamColour);
                    ShootableGadgetItemMethods.sendPackets(
                            player,
                            local -> new ZapperBeamPacket(barrel, hand, local, entityTarget.hitPos())
                    );
                }

                return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
            }

            final float moonScale = level.isClientSide
                    ? MoonScaleClient.get()
                    : player instanceof final ServerPlayer serverPlayer
                            ? MoonScale.get(serverPlayer.serverLevel().getServer())
                            : 1.0F;

            final MoonTargeting.Hit moonHit = MoonTargeting.hit(
                    player, moonScale, 1.0F, RANGE);
            if (moonHit == null
                    && player instanceof final ServerPlayer shooter
                    && selectedStage(stack, player).isDeeperThan(MoonScale.stage(shooter.serverLevel().getServer()))
                    && MoonTargeting.aimedAtNonFullMoon(player, moonScale, RANGE)) {
                player.displayClientMessage(MoonTargeting.NOT_FULL_MESSAGE, true);
            }
            if (moonHit != null) {
                if (ShootableGadgetItemMethods.shouldSwap(player, stack, hand, this::isZapper)) {
                    return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
                }

                if (level.isClientSide) {
                    CreateClient.ZAPPER_RENDER_HANDLER.dontAnimateItem(hand);
                    return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
                }

                if (player instanceof final ServerPlayer serverPlayer) {
                    final CompressionStage moonTarget = selectedStage(stack, player);
                    final CompressionStage moonCurrent =
                            MoonScale.stage(serverPlayer.serverLevel().getServer());
                    final Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                            player,
                            hand == InteractionHand.MAIN_HAND,
                            new Vec3(0.35D, -0.1D, 1.0D)
                    );
                    final int beamColour = moonCurrent == moonTarget
                            ? ShrinkRayBeamColourPayload.INERT_COLOUR
                            : moonTarget.depth() < moonCurrent.depth()
                                    ? ShrinkRayBeamColourPayload.GROW_COLOUR
                                    : ShrinkRayBeamColourPayload.SHRINK_COLOUR;

                    ShootableGadgetItemMethods.applyCooldown(
                            player,
                            stack,
                            hand,
                            this::isZapper,
                            getCooldownDelay(stack)
                    );
                    MoonCompressionSessions.instant(serverPlayer, moonTarget, moonHit);
                    ShrinkRayBeamColourPayload.send(serverPlayer, moonHit.worldPoint(), beamColour);
                    ShootableGadgetItemMethods.sendPackets(
                            player,
                            local -> new ZapperBeamPacket(barrel, hand, local, moonHit.worldPoint())
                    );
                }

                return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
            }
        }

        if (level.isClientSide) return super.use(level, player, hand);

        FIRED.set(Boolean.FALSE);
        final InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (!FIRED.get() && result.getResult() == InteractionResult.SUCCESS) {
            beamOnly(level, player, hand);
        }
        return result;
    }

    private void beamOnly(final Level level, final Player player, final InteractionHand hand) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 end = level.clip(new ClipContext(
                eye,
                eye.add(player.getLookAngle().scale(RANGE)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player)).getLocation();
        final Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                player,
                hand == InteractionHand.MAIN_HAND,
                new Vec3(0.35D, -0.1D, 1.0D)
        );

        ShootableGadgetItemMethods.sendPackets(
                player, local -> new ZapperBeamPacket(barrel, hand, local, end));
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
        FIRED.set(Boolean.TRUE);

        if (level.isClientSide) return true;

        final double target = selectedScale(stack, player);
        final CompressionGunTargetingMode targeting = targetingMode(stack);

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

        final double currentScale = ScaleState.getServerScale(serverSubLevel);
        final boolean inert = ScaleState.isAt(serverSubLevel, target);
        final int beamColour = inert
                ? ShrinkRayBeamColourPayload.INERT_COLOUR
                : target > currentScale
                        ? ShrinkRayBeamColourPayload.GROW_COLOUR
                        : ShrinkRayBeamColourPayload.SHRINK_COLOUR;

        if (!inert && target < PocketSized.FULL_SCALE - PocketSized.EPSILON) {
            final int blocks = PocketMetrics.measureForCompression(serverSubLevel, level.getGameTime()).blocks();
            if (blocks > PocketSized.MAX_COMPRESSED_BLOCKS) {
                player.displayClientMessage(Component.translatable("pocket.message.hard_limit", PocketSized.MAX_COMPRESSED_BLOCKS), true);
                return false;
            }
        }

        if (player instanceof final ServerPlayer serverPlayer) {
            final Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                    player,
                    heldHand(player, stack) == InteractionHand.MAIN_HAND,
                    new Vec3(0.35D, -0.1D, 1.0D)
            );
            final boolean connected = targeting == CompressionGunTargetingMode.CONNECTED_SUBLEVELS;
            final ScaleLimits scaleLimits = limits(player);

            ShootableGadgetItemMethods.applyCooldown(
                    player,
                    stack,
                    heldHand(player, stack),
                    this::isZapper,
                    getCooldownDelay(stack)
            );
            final Vec3 beamPoint = worldPointOf(serverSubLevel, raytrace.getLocation());
            if (!inert) {
                com.misterblusky9.pocket.compression.CompressionSessions.instant(
                        serverPlayer, serverSubLevel, contact, target, connected, scaleLimits
                );
            }
            ShrinkRayBeamColourPayload.send(serverPlayer, beamPoint, beamColour);
            ShootableGadgetItemMethods.sendPackets(
                    player,
                    local -> new ZapperBeamPacket(barrel, heldHand(player, stack), local, beamPoint)
            );
            return false;
        }

        if (!inert) {
            ScaleController.forceScale(
                    serverSubLevel, target, level.getGameTime(), null,
                    targeting == CompressionGunTargetingMode.CONNECTED_SUBLEVELS,
                    ScalePhysicsMode.TRACKING,
                    limits(player)
            );
        }
        return false;
    }

    private static Vec3 worldPointOf(final ServerSubLevel subLevel, final Vec3 plotPoint) {
        final org.joml.Vector3d world = subLevel.logicalPose().transformPosition(
                new org.joml.Vector3d(plotPoint.x, plotPoint.y, plotPoint.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private static InteractionHand heldHand(final Player player, final ItemStack stack) {
        return player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static net.minecraft.core.BlockPos centreOf(final ServerSubLevel subLevel) {
        final var bounds = subLevel.getPlot().getBoundingBox();
        return new net.minecraft.core.BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
    }
}
