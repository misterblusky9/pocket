package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pym.api.ResizeRequest;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pocket.client.CreativeShrinkRayRenderer;
import com.misterblusky9.pocket.client.MoonScaleClient;
import com.misterblusky9.pocket.compression.EntityCompressionSessions;
import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.moon.MoonCompressionSessions;
import com.misterblusky9.pocket.moon.MoonScale;
import com.misterblusky9.pocket.moon.MoonTargeting;
import com.misterblusky9.pocket.network.ShrinkRayBeamColourPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ResizeActor;
import com.misterblusky9.pocket.scale.ResizeFeedback;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pocket.scale.ScaleLimits;
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

public final class CreativeShrinkRayItem extends ZapperItem implements PriorityInteractionItem, ScaleSelectingItem {
    private static final ThreadLocal<Boolean> FIRED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String SCALE_KEY = "PocketScale";
    private static final String STAGE_KEY = "PocketStage";
    public static final double RANGE = 192.0D;
    private static final Vec3 BARREL_OFFSET = new Vec3(0.35D, -0.1D, 1.0D);

    public CreativeShrinkRayItem(Properties properties) {
        super(properties);
    }

    public enum TargetingMode {
        BOTH(true, true),
        CONTRAPTIONS(true, false),
        ENTITIES(false, true);

        private final boolean contraptions;
        private final boolean entities;

        TargetingMode(boolean contraptions, boolean entities) {
            this.contraptions = contraptions;
            this.entities = entities;
        }

        public boolean allowsContraptions() {
            return this.contraptions;
        }

        public boolean allowsEntities() {
            return this.entities;
        }

        public static TargetingMode byId(int id) {
            TargetingMode[] values = values();
            return id >= 0 && id < values.length ? values[id] : BOTH;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CreativeShrinkRayRenderer()));
    }

    public static CompressionStage selectedStage(ItemStack stack, Player player) {
        return CompressionStage.nearest(selectedScale(stack, player));
    }

    public static void setSelectedStage(ItemStack stack, CompressionStage stage) {
        setSelectedScale(stack, stage == null ? ScaleBounds.FULL : stage.scale());
    }

    public static double[] ladder(Player player) {
        return ScaleLadder.SAFE;
    }

    public static ScaleBounds limits(Player player) {
        return DeviceRanges.of(DeviceRanges.Device.CREATIVE_SHRINK_RAY);
    }

    public static ScaleBounds limits(TargetingMode mode, Player player) {
        ScaleBounds ray = limits(player);
        if (mode != TargetingMode.ENTITIES) return ray;
        ScaleBounds personal = DeviceRanges.of(DeviceRanges.Device.PERSONAL_COMPRESSOR);
        return new ScaleBounds(Math.min(ray.min(), personal.min()), personal.max());
    }

    public static ScaleBounds limits(ItemStack stack, Player player) {
        return limits(targetingMode(stack), player);
    }

    public static boolean permits(ItemStack stack, Player player, double scale) {
        return ScaleBounds.isValid(scale);
    }

    public static double selectedScale(ItemStack stack, Player player) {
        return selectedScale(stack);
    }

    private static ScaleBounds reaching(ScaleBounds limits, double target) {
        return new ScaleBounds(Math.min(limits.min(), target), Math.max(limits.max(), target));
    }

    public static double selectedScale(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return ScaleBounds.FULL;
        CompoundTag tag = custom.copyTag();
        if (tag.contains(SCALE_KEY, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                && ScaleBounds.isValid(tag.getDouble(SCALE_KEY))) {
            return CompressionStage.snap(tag.getDouble(SCALE_KEY));
        }
        return CompressionStage.fromDepth(tag.getInt(STAGE_KEY)).scale();
    }

    public static void setSelectedScale(ItemStack stack, double scale) {
        if (!ScaleBounds.isValid(scale)) return;
        double clamped = CompressionStage.snap(scale);
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putDouble(SCALE_KEY, clamped);
        tag.putInt(STAGE_KEY, CompressionStage.nearest(clamped).depth());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static TargetingMode targetingMode(ItemStack stack) {
        return TargetingMode.byId(stack.getOrDefault(ModDataComponents.SHRINK_RAY_TARGETING.get(), 0));
    }

    public static void setTargetingMode(ItemStack stack, TargetingMode mode, Player player) {
        TargetingMode next = mode == null ? TargetingMode.BOTH : mode;
        stack.set(ModDataComponents.SHRINK_RAY_TARGETING.get(), next.ordinal());
        setSelectedScale(stack, limits(next, player).clamp(selectedScale(stack)));
    }

    @Override
    public ScaleBounds selectionRange(ItemStack stack, Player player) {
        return limits(stack, player);
    }

    @Override
    public boolean permitsSelection(ItemStack stack, Player player, double scale) {
        return permits(stack, player, scale);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown()) {
            TargetingMode targeting = targetingMode(stack);

            EntityCompressionTargeting.Target entityTarget = targeting.allowsEntities()
                    ? EntityCompressionTargeting.find(player, RANGE, true)
                    : null;
            if (entityTarget != null) {
                if (ShootableGadgetItemMethods.shouldSwap(player, stack, hand, this::isZapper)) {
                    return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
                }

                if (level.isClientSide) {
                    CreateClient.ZAPPER_RENDER_HANDLER.dontAnimateItem(hand);
                    return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    double currentScale = EntityCompressionTargeting.scale(entityTarget.entity());
                    double targetScale = selectedScale(stack, player);
                    int beamColour = ScaleBounds.same(currentScale, targetScale)
                            ? ShrinkRayBeamColourPayload.INERT_COLOUR
                            : targetScale > currentScale
                                    ? ShrinkRayBeamColourPayload.GROW_COLOUR
                                    : ShrinkRayBeamColourPayload.SHRINK_COLOUR;

                    EntityCompressionSessions.creative(
                            serverPlayer, entityTarget.entity(), targetScale);
                    ShootableGadgetItemMethods.applyCooldown(
                            player, stack, hand, this::isZapper, getCooldownDelay(stack));

                    Vec3 barrel = barrelOf(player, hand == InteractionHand.MAIN_HAND);
                    ShrinkRayBeamColourPayload.send(serverPlayer, entityTarget.hitPos(), beamColour);
                    ShootableGadgetItemMethods.sendPackets(
                            player,
                            local -> new ZapperBeamPacket(barrel, hand, local, entityTarget.hitPos())
                    );
                }

                return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
            }

            float moonScale = level.isClientSide
                    ? MoonScaleClient.get()
                    : player instanceof ServerPlayer serverPlayer
                            ? MoonScale.get(serverPlayer.serverLevel().getServer())
                            : 1.0F;

            MoonTargeting.Hit moonHit = targeting.allowsContraptions()
                    ? MoonTargeting.hit(player, moonScale, 1.0F, RANGE)
                    : null;
            if (targeting.allowsContraptions()
                    && moonHit == null
                    && player instanceof ServerPlayer shooter
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

                if (player instanceof ServerPlayer serverPlayer) {
                    CompressionStage moonTarget = selectedStage(stack, player);
                    CompressionStage moonCurrent =
                            MoonScale.stage(serverPlayer.serverLevel().getServer());
                    Vec3 barrel = barrelOf(player, hand == InteractionHand.MAIN_HAND);
                    int beamColour = moonCurrent == moonTarget
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
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (!FIRED.get() && result.getResult() == InteractionResult.SUCCESS) {
            beamOnly(level, player, hand);
        }
        return result;
    }

    private void beamOnly(Level level, Player player, InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                eye.add(player.getLookAngle().scale(RANGE)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        beamOnly(level, player, hand, hit);
    }

    private void beamOnly(
            Level level,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        Vec3 end = beamPointOf(level, hit);
        Vec3 barrel = barrelOf(player, hand == InteractionHand.MAIN_HAND);

        if (player instanceof ServerPlayer serverPlayer) {
            ShrinkRayBeamColourPayload.send(serverPlayer, end, ShrinkRayBeamColourPayload.INERT_COLOUR);
        }
        ShootableGadgetItemMethods.sendPackets(
                player, local -> new ZapperBeamPacket(barrel, hand, local, end));
    }

    private static Vec3 barrelOf(Player player, boolean mainHand) {
        double scale = Pym.entities().scaleOf(player);
        return ShootableGadgetItemMethods.getGunBarrelVec(
                player, mainHand, BARREL_OFFSET.scale(ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void openHandgunGUI(ItemStack item, InteractionHand hand) {
    }

    @Override
    public @Nullable Component validateUsage(ItemStack item) {
        return null;
    }

    @Override
    protected int getZappingRange(ItemStack stack) {
        return (int) RANGE;
    }

    @Override
    protected int getCooldownDelay(ItemStack stack) {
        return 2;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        stack.remove(AllDataComponents.SHAPER_BLOCK_USED);
        stack.remove(AllDataComponents.SHAPER_BLOCK_DATA);
    }

    @Override
    protected boolean canActivateWithoutSelectedBlock(ItemStack stack) {
        return true;
    }

    @Override
    protected boolean activate(
            Level level,
            Player player,
            ItemStack stack,
            BlockState stateToUse,
            BlockHitResult raytrace,
            CompoundTag data
    ) {
        if (!targetingMode(stack).allowsContraptions()) {
            FIRED.set(Boolean.TRUE);
            InteractionHand hand = heldHand(player, stack);
            ShootableGadgetItemMethods.applyCooldown(
                    player, stack, hand, this::isZapper, getCooldownDelay(stack));
            beamOnly(level, player, hand, raytrace);
            return false;
        }

        FIRED.set(Boolean.TRUE);
        if (level.isClientSide) return true;

        double target = selectedScale(stack, player);

        SubLevel subLevel = Sable.HELPER.getContaining(level, raytrace.getLocation());
        if (subLevel == null) subLevel = Sable.HELPER.getContaining(level, raytrace.getBlockPos());

        boolean lockedOn = false;
        if (!(subLevel instanceof ServerSubLevel)
                && player instanceof ServerPlayer holder) {
            subLevel = com.misterblusky9.pocket.compression.CompressionSessions.lockedSubLevel(holder);
            lockedOn = subLevel != null;
        }

        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return true;

        net.minecraft.core.BlockPos contact =
                lockedOn ? centreOf(serverSubLevel) : raytrace.getBlockPos();

        double currentScale = Pym.scale().of(serverSubLevel);
        boolean inert = Pym.scale().isAt(serverSubLevel, target);
        int beamColour = inert
                ? ShrinkRayBeamColourPayload.INERT_COLOUR
                : target > currentScale
                        ? ShrinkRayBeamColourPayload.GROW_COLOUR
                        : ShrinkRayBeamColourPayload.SHRINK_COLOUR;

        if (!inert && target < ScaleBounds.FULL - ScaleBounds.EPSILON) {
            int limit = Pym.resize().shrunkBlockLimit();
            if (Pym.resize().contents(serverSubLevel).blocks() > limit) {
                player.displayClientMessage(Component.translatable("pocket.message.hard_limit", limit), true);
                return false;
            }
        }

        if (player instanceof ServerPlayer serverPlayer) {
            Vec3 barrel = barrelOf(player, heldHand(player, stack) == InteractionHand.MAIN_HAND);
            boolean connected = ScaleToolModifier.propagate(player);
            ScaleBounds scaleLimits = reaching(limits(player), target);

            ShootableGadgetItemMethods.applyCooldown(
                    player,
                    stack,
                    heldHand(player, stack),
                    this::isZapper,
                    getCooldownDelay(stack)
            );
            Vec3 beamPoint = worldPointOf(serverSubLevel, raytrace.getLocation());
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
            ScaleBounds limits = reaching(limits(player), target);
            ResizeRequest request = Pym.resize().request(serverSubLevel)
                    .scaleTo(target)
                    .propagate(ScaleToolModifier.propagate(player))
                    .bounds(limits);
            if (ScaleLimits.confirmsUnsupported(limits)) request.unsupported();
            ResizeFeedback.report(player, ResizeActor.as(player, request::submit));
        }
        return false;
    }

    private static Vec3 beamPointOf(Level level, BlockHitResult hit) {
        if (hit == null) return Vec3.ZERO;

        SubLevel subLevel = Sable.HELPER.getContaining(level, hit.getLocation());
        if (subLevel == null) subLevel = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return worldPointOf(serverSubLevel, hit.getLocation());
        }
        return hit.getLocation();
    }

    private static Vec3 worldPointOf(ServerSubLevel subLevel, Vec3 plotPoint) {
        org.joml.Vector3d world = subLevel.logicalPose().transformPosition(
                new org.joml.Vector3d(plotPoint.x, plotPoint.y, plotPoint.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private static InteractionHand heldHand(Player player, ItemStack stack) {
        return player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static net.minecraft.core.BlockPos centreOf(ServerSubLevel subLevel) {
        var bounds = subLevel.getPlot().getBoundingBox();
        return new net.minecraft.core.BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
    }
}
