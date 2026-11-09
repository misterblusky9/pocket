package com.misterblusky9.pocket.block;

import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.client.DummyHeadHaunting;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class HatStandBlockEntity extends BuildHostBlockEntity {
    public static final int LAYER_MODES = 3;
    public static final int LAYER_FLAT = 1;
    public static final int LAYER_REMOVED = 2;
    private @Nullable ResolvableProfile skin;
    private static final Map<Player, HatStandBlockEntity> TURNING = new WeakHashMap<>();
    private static final int TURN_INTERVAL = 4;
    private static final float SHOWN_RATE = 0.6F;
    private static final float DETENT = 15.0F;
    private static final float DETENT_CATCH = DETENT * 0.6F;
    private static final float TURN_SETTLE = 0.1F;
    private static final double TURN_RANGE = 32.0;
    private static final float PITCH_LIMIT = 60.0F;
    private static final double PIVOT_DROP = 2.0 / 16.0;
    private static final double HEAD_HALF = 4.0 / 16.0;
    private static final double HEAD_TOUCH = 1.0E-4;
    private int skinLayer;
    private float lookYaw;
    private float lookPitch;
    private float shownYaw;
    private float shownPitch;
    private float lastShownYaw;
    private float lastShownPitch;
    private boolean settled;
    private @Nullable Player turner;

    public HatStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAT_STAND.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.HAT_STAND.get(),
                (stand, side) -> stand.items());
    }

    public @Nullable ResolvableProfile getSkinProfile() {
        return skin != null && skin.properties().containsKey("textures") ? skin : null;
    }

    public NameEgg nameEgg() {
        return NameEgg.of(skin);
    }

    public int getSkinLayer() {
        return skinLayer;
    }

    public int getHeadwearLayer() {
        return headwearLayer(getActivator());
    }

    public static int headwearLayer(ItemStack stack) {
        int layer = stack.getOrDefault(ModDataComponents.HEADWEAR_LAYER.get(), 0);
        return stack.is(ModItems.SKULL_CAP.get()) && layer != LAYER_REMOVED ? LAYER_FLAT : layer;
    }

    public void toggleHeadwear() {
        if (getHeadwearLayer() == LAYER_REMOVED) getActivator().remove(ModDataComponents.HEADWEAR_LAYER.get());
        else getActivator().set(ModDataComponents.HEADWEAR_LAYER.get(), LAYER_REMOVED);
        sync();
    }

    public boolean isFirstPersonRendered() {
        return firstPersonRendered(getActivator());
    }

    public static boolean firstPersonRendered(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FIRST_PERSON_RENDERED.get(), false);
    }

    public boolean isHeadwearShown() {
        return getHeadwearLayer() != LAYER_REMOVED;
    }

    public void showHeadwear(boolean shown) {
        if (isHeadwearShown() != shown) toggleHeadwear();
    }

    public void showFirstPerson(boolean shown) {
        if (shown) getActivator().set(ModDataComponents.FIRST_PERSON_RENDERED.get(), true);
        else getActivator().remove(ModDataComponents.FIRST_PERSON_RENDERED.get());
        sync();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new HatPlateToggle(HatPlateToggle.HELMET, 2,
                Component.translatable("pocket.hat_stand.helmet"), this, HatPlateSlot.HELMET,
                this::isHeadwearShown, this::showHeadwear));
        behaviours.add(new HatPlateToggle(HatPlateToggle.FIRST_PERSON, 3,
                Component.translatable("pocket.hat_stand.first_person"), this, HatPlateSlot.FIRST_PERSON,
                this::isFirstPersonRendered, this::showFirstPerson));
    }

    public static @Nullable HatStandBlockEntity turning(Player player) {
        HatStandBlockEntity stand = TURNING.get(player);
        if (stand == null || !stand.isRemoved()) return stand;
        TURNING.remove(player);
        return null;
    }

    public void startTurning(Player player) {
        if (turner != null) TURNING.remove(turner);
        turner = player;
        TURNING.put(player, this);
    }

    public void stopTurning() {
        if (turner != null) TURNING.remove(turner);
        turner = null;
    }

    @Override
    protected void serverTick() {
        if (turner == null) return;
        if (turner.isRemoved() || turner.level() != level
                || !AllItems.WRENCH.isIn(turner.getMainHandItem()) && !AllItems.WRENCH.isIn(turner.getOffhandItem())
                || !aim()) stopTurning();
    }

    private boolean aim() {
        Vec2 aimed = aimedLook(turner);
        if (aimed == null) return false;
        if (level.getGameTime() % TURN_INTERVAL != 0 || aimed.x == lookYaw && aimed.y == lookPitch) return true;
        lookYaw = aimed.x;
        lookPitch = aimed.y;
        sync();
        return true;
    }

    public @Nullable Vec2 aimedLook(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 ahead = eye.add(player.getViewVector(1.0F));
        SubLevel parent = Sable.HELPER.getContaining(level, worldPosition);
        if (parent != null) {
            eye = parent.logicalPose().transformPositionInverse(eye);
            ahead = parent.logicalPose().transformPositionInverse(ahead);
        }
        Vec3 away = eye.subtract(Vec3.atLowerCornerOf(worldPosition)
                .add(((HatStandBlock) getBlockState().getBlock()).anchor(getBlockState())));
        if (away.lengthSqr() > TURN_RANGE * TURN_RANGE) return null;
        Vec3 look = ahead.subtract(eye).normalize();
        Vec3 toPlayer = away.normalize();
        Vec3 forward = look.subtract(toPlayer.scale(2.0 * look.dot(toPlayer)));
        float yaw = (float) Math.toDegrees(Mth.atan2(forward.z, forward.x)) - 90.0F
                - getBlockState().getValue(HatStandBlock.FACING).toYRot();
        float steepness = (float) -Math.toDegrees(Mth.atan2(forward.y, forward.horizontalDistance()));
        float pitch = Mth.clamp(steepness, -PITCH_LIMIT, PITCH_LIMIT);
        float yawGap = Mth.wrapDegrees(yaw - lookYaw);
        float pitchGap = pitch - lookPitch;
        boolean turnYaw = Math.abs(yawGap) > DETENT_CATCH && Math.abs(steepness) < PITCH_LIMIT;
        boolean turnPitch = Math.abs(pitchGap) > DETENT_CATCH;
        return new Vec2(turnYaw ? Mth.wrapDegrees(Math.round((lookYaw + yawGap) / DETENT) * DETENT) : lookYaw,
                turnPitch ? Math.round(pitch / DETENT) * DETENT : lookPitch);
    }

    public Vec2 look() {
        return new Vec2(lookYaw, lookPitch);
    }

    public Vec3 pivot() {
        return ((HatStandBlock) getBlockState().getBlock()).anchor(getBlockState()).subtract(0.0, PIVOT_DROP, 0.0);
    }

    @Override
    public Vec3 headCentre() {
        return headCentre(orientation());
    }

    public Vec3 headCentre(float partialTick) {
        return headCentre(orientation(partialTick));
    }

    public AABB headBox() {
        Vec3 anchor = ((HatStandBlock) getBlockState().getBlock()).anchor(getBlockState());
        return new AABB(anchor, anchor).inflate(HEAD_HALF);
    }

    public Quaterniond headTurn(float partialTick) {
        return headTurn(orientation(partialTick));
    }

    private Quaterniond headTurn(Quaterniond orientation) {
        return orientation.mul(new Quaterniond(((HatStandBlock) getBlockState().getBlock()).orientation(getBlockState())).invert());
    }

    public Vec3 turnAboutPivot(Quaterniond turn, Vec3 point) {
        Vec3 pivot = Vec3.atLowerCornerOf(worldPosition).add(pivot());
        Vector3d arm = turn.transform(new Vector3d(point.x - pivot.x, point.y - pivot.y, point.z - pivot.z));
        return pivot.add(arm.x, arm.y, arm.z);
    }

    public @Nullable BlockHitResult clipHead(Vec3 from, Vec3 to, BlockPos pos) {
        Quaterniond turn = headTurn(orientation());
        Quaterniond back = new Quaterniond(turn).invert();
        BlockHitResult hit = AABB.clip(List.of(headBox()), turnAboutPivot(back, from), turnAboutPivot(back, to), worldPosition);
        if (hit == null) return null;
        Vec3i normal = hit.getDirection().getNormal();
        Vector3d facing = turn.transform(new Vector3d(normal.getX(), normal.getY(), normal.getZ()));
        return new BlockHitResult(turnAboutPivot(turn, hit.getLocation()),
                Direction.getNearest(facing.x, facing.y, facing.z), pos, false);
    }

    public boolean onHead(Vec3 point) {
        Vec3 rest = turnAboutPivot(new Quaterniond(headTurn(orientation())).invert(), point);
        return headBox().move(worldPosition).inflate(HEAD_TOUCH).contains(rest);
    }

    private Vec3 headCentre(Quaterniond orientation) {
        Quaterniond resting = ((HatStandBlock) getBlockState().getBlock()).orientation(getBlockState());
        Vector3d up = new Quaterniond(orientation).mul(resting.invert()).transform(new Vector3d(0.0, PIVOT_DROP, 0.0));
        return pivot().add(up.x, up.y, up.z);
    }

    @Override
    protected void animate() {
        lastShownYaw = shownYaw;
        lastShownPitch = shownPitch;
        if (DummyHeadHaunting.tick(this)) return;
        if (nameEgg() == NameEgg.HEROBRINE && level.hasNeighborSignal(worldPosition)
                && DummyHeadHaunting.stalk(this)) return;
        float yawGap = Mth.wrapDegrees(lookYaw - shownYaw);
        float pitchGap = lookPitch - shownPitch;
        shownYaw += Math.abs(yawGap) < TURN_SETTLE ? yawGap : yawGap * SHOWN_RATE;
        float turns = shownYaw - Mth.wrapDegrees(shownYaw);
        shownYaw -= turns;
        lastShownYaw -= turns;
        shownPitch = Math.abs(pitchGap) < TURN_SETTLE ? lookPitch : shownPitch + pitchGap * SHOWN_RATE;
    }

    public void stareAt(Player player, boolean snap) {
        Vec3 eye = player.getEyePosition();
        SubLevel parent = Sable.HELPER.getContaining(level, worldPosition);
        if (parent != null) eye = parent.logicalPose().transformPositionInverse(eye);
        Vec3 to = eye.subtract(Vec3.atLowerCornerOf(worldPosition).add(pivot()));
        float yaw = (float) Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F
                - getBlockState().getValue(HatStandBlock.FACING).toYRot();
        float pitch = Mth.clamp((float) -Math.toDegrees(Mth.atan2(to.y, to.horizontalDistance())),
                -PITCH_LIMIT, PITCH_LIMIT);
        shownYaw += Mth.wrapDegrees(yaw - shownYaw);
        shownPitch = pitch;
        if (!snap) return;
        lastShownYaw = shownYaw;
        lastShownPitch = shownPitch;
    }

    public void settleShown() {
        shownYaw = lastShownYaw = lookYaw;
        shownPitch = lastShownPitch = lookPitch;
    }

    @Override
    public Quaterniond orientation() {
        return level != null && level.isClientSide ? orientation(1.0F) : looking(lookYaw, lookPitch);
    }

    public Quaterniond orientation(float partialTick) {
        return looking(Mth.lerp(partialTick, lastShownYaw, shownYaw), Mth.lerp(partialTick, lastShownPitch, shownPitch));
    }

    private Quaterniond looking(float yaw, float pitch) {
        double facing = getBlockState().getValue(HatStandBlock.FACING).toYRot();
        return new Quaterniond().rotateY(Math.toRadians(180.0 - facing - yaw)).rotateX(Math.toRadians(-pitch));
    }

    public void setSkin(@Nullable ResolvableProfile profile) {
        if (!(level instanceof ServerLevel server)) return;
        skin = profile;
        sync();
        if (profile == null || profile.isResolved()) return;
        profile.resolve().thenAcceptAsync(resolved -> {
            if (level != server || isRemoved() || skin != profile) return;
            skin = profile.name().isEmpty() ? resolved
                    : new ResolvableProfile(profile.name(), resolved.id(), resolved.properties());
            sync();
        }, server.getServer());
    }

    public void setSkinLayer(int layer) {
        skinLayer = Math.floorMod(layer, LAYER_MODES);
        sync();
    }

    @Override
    protected void stamp(ItemStack result) {
        if (skinLayer == 0) result.remove(ModDataComponents.SKIN_LAYER.get());
        else result.set(ModDataComponents.SKIN_LAYER.get(), skinLayer);
        NameEgg egg = token(result) == null ? NameEgg.NONE : nameEgg();
        if (egg == NameEgg.NONE) result.remove(ModDataComponents.NAME_EGG.get());
        else result.set(ModDataComponents.NAME_EGG.get(), egg);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && skin != null && !skin.isResolved()) setSkin(skin);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, skin);
        if (skinLayer != 0) components.set(ModDataComponents.SKIN_LAYER.get(), skinLayer);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (skin != null) tag.put("SkinProfile",
                ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, skin).getOrThrow());
        if (skinLayer != 0) tag.putInt("SkinLayer", skinLayer);
        if (lookYaw != 0.0F) tag.putFloat("LookYaw", lookYaw);
        if (lookPitch != 0.0F) tag.putFloat("LookPitch", lookPitch);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        skin = tag.contains("SkinProfile")
                ? ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag.get("SkinProfile")).result().orElse(null)
                : null;
        skinLayer = Math.floorMod(tag.getInt("SkinLayer"), LAYER_MODES);
        lookYaw = tag.getFloat("LookYaw");
        lookPitch = tag.getFloat("LookPitch");
        if (settled) return;
        settled = true;
        shownYaw = lastShownYaw = lookYaw;
        shownPitch = lastShownPitch = lookPitch;
    }

    public enum NameEgg implements StringRepresentable {
        NONE, UPSIDE_DOWN, JEB, HEROBRINE, RAINBOW, VOID;

        public static final Codec<NameEgg> CODEC = StringRepresentable.fromEnum(NameEgg::values);
        public static final StreamCodec<FriendlyByteBuf, NameEgg> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(NameEgg.class);

        public static NameEgg of(@Nullable ResolvableProfile profile) {
            String name = profile == null ? "" : profile.name().orElse("");
            return switch (name) {
                case "Dinnerbone", "Grumm" -> UPSIDE_DOWN;
                case "jeb_" -> JEB;
                case "Herobrine" -> HEROBRINE;
                default -> name.equals("KEYGEN") ? RAINBOW : name.equalsIgnoreCase("MisterBluSky") ? VOID : NONE;
            };
        }

        public static NameEgg of(ItemStack stack) {
            return token(stack) == null ? NONE : stack.getOrDefault(ModDataComponents.NAME_EGG.get(), NONE);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
