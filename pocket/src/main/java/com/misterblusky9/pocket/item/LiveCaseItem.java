package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.LiveCaseItemRenderer;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.pocket.CannonExpansionMode;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class LiveCaseItem extends Item {
    private static final String HELD_KEY = "live_sublevel";
    private static final String GROUP_KEY = "live_group";
    private static final String WORN_POSE_KEY = "worn_pose";
    public static final int WORN_POSE_FIELDS = 6;
    private static final double PARK_RISE = 200.0D;
    private static final double PARK_ABOVE_BUILD_LIMIT = 16.0D;
    private static final double PARK_SPACING = 4.0D;
    private static final int PARK_ROW = 9;
    private static final int WORN_PARK_SLOT = 45;
    private static final double RELEASE_LIFT = 1.0D / 64.0D;
    private static final int THROW_USE_TICKS = 72000;
    private static final float MIN_THROW_VELOCITY = 0.1F;
    private static final double THROW_SPEED_FACTOR = 1.0D;

    public LiveCaseItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new LiveCaseItemRenderer()));
    }

    @Nullable
    public static UUID held(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        final CompoundTag tag = data.getUnsafe();
        return tag.hasUUID(HELD_KEY) ? tag.getUUID(HELD_KEY) : null;
    }

    public static List<UUID> group(final ItemStack stack) {
        final UUID root = held(stack);
        if (root == null) return List.of();
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        final ListTag list = data == null ? new ListTag() : data.getUnsafe().getList(GROUP_KEY, Tag.TAG_INT_ARRAY);
        final List<UUID> ids = new ArrayList<>(list.size() + 1);
        ids.add(root);
        for (final Tag entry : list) {
            final UUID id = NbtUtils.loadUUID(entry);
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private static void setHeld(final ItemStack stack, @Nullable final UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(GROUP_KEY);
            if (id == null) tag.remove(HELD_KEY);
            else tag.putUUID(HELD_KEY, id);
        });
    }

    private static void recordGroup(final ItemStack stack, final List<ServerSubLevel> members) {
        final List<UUID> ids = new ArrayList<>(members.size());
        for (final ServerSubLevel member : members) ids.add(member.getUniqueId());
        if (ids.equals(group(stack))) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            final ListTag list = new ListTag();
            for (final UUID id : ids.subList(1, ids.size())) list.add(NbtUtils.createUUID(id));
            tag.put(GROUP_KEY, list);
        });
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

        final Level level = context.getLevel();
        final ItemStack stack = context.getItemInHand();

        if (held(stack) == null) {
            final SubLevel found = Sable.HELPER.getContaining(level, context.getClickedPos());
            if (found == null) return InteractionResult.PASS;
            if (level instanceof final ServerLevel serverLevel && found instanceof final ServerSubLevel subLevel) {
                pickUp(serverLevel, player, subLevel, stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level instanceof final ServerLevel serverLevel) release(serverLevel, player, stack, context);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void inventoryTick(
            final ItemStack stack,
            final Level level,
            final Entity entity,
            final int slot,
            final boolean selected
    ) {
        if (!(level instanceof final ServerLevel serverLevel)) return;
        park(serverLevel, entity, stack, wornSlot(entity, stack) instanceof final EquipmentSlot worn
                ? WORN_PARK_SLOT + worn.getIndex()
                : slot);
    }

    @Nullable
    private static EquipmentSlot wornSlot(final Entity entity, final ItemStack stack) {
        if (!(entity instanceof final LivingEntity living)) return null;
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && living.getItemBySlot(slot) == stack) return slot;
        }
        return null;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(final ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean canEquip(final ItemStack stack, final EquipmentSlot slot, final LivingEntity entity) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Override
    public boolean onEntityItemUpdate(final ItemStack stack, final ItemEntity entity) {
        if (entity.level() instanceof final ServerLevel serverLevel) park(serverLevel, entity, stack, 0);
        return false;
    }

    // Worn pose

    public static float[] wornPose(final ItemStack stack) {
        final float[] pose = new float[WORN_POSE_FIELDS];
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return pose;
        final ListTag list = data.getUnsafe().getList(WORN_POSE_KEY, Tag.TAG_FLOAT);
        for (int i = 0; i < Math.min(list.size(), pose.length); i++) pose[i] = list.getFloat(i);
        return pose;
    }

    public static void setWornPose(final ItemStack stack, final float[] pose) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            final ListTag list = new ListTag();
            for (final float value : pose) list.add(FloatTag.valueOf(value));
            tag.put(WORN_POSE_KEY, list);
        });
    }

    // Group

    private static List<ServerSubLevel> assemble(final ServerSubLevelContainer container, final ServerSubLevel root) {
        final Set<ServerSubLevel> members = new LinkedHashSet<>();
        final Deque<ServerSubLevel> queue = new ArrayDeque<>();
        members.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            final ServerSubLevel current = queue.poll();
            for (final ServerSubLevel joined : Pym.connections().assembly(current)) {
                if (members.add(joined)) queue.add(joined);
            }
            for (final UUID welded : CrossScaleWelds.componentIds(container, current.getUniqueId())) {
                if (container.getSubLevel(welded) instanceof final ServerSubLevel member
                        && !member.isRemoved() && members.add(member)) {
                    queue.add(member);
                }
            }
        }
        return List.copyOf(members);
    }

    private static boolean anchoredToWorld(final Level level, final List<ServerSubLevel> members) {
        for (final WeldRecord record : CrossScaleWelds.records(level)) {
            if (!record.worldAnchored()) continue;
            for (final ServerSubLevel member : members) {
                if (record.touches(member.getUniqueId())) return true;
            }
        }
        return false;
    }

    @Nullable
    private static List<ServerSubLevel> heldGroup(final ServerLevel level, final ItemStack stack) {
        final UUID id = held(stack);
        if (id == null) return null;
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        if (!(container.getSubLevel(id) instanceof final ServerSubLevel root) || root.isRemoved()) return null;
        return assemble(container, root);
    }

    private static void moveGroup(
            final ServerSubLevelContainer container,
            final List<ServerSubLevel> members,
            final Vector3dc rootPosition,
            final Quaterniondc rootOrientation,
            final boolean keepRelativeMotion
    ) {
        final ServerSubLevel root = members.get(0);
        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        final Vector3d rootFrom = new Vector3d(root.logicalPose().position());
        final Quaterniond turn = new Quaterniond(rootOrientation)
                .mul(new Quaterniond(root.logicalPose().orientation()).conjugate());
        final Vector3d rootVelocity = pipeline.getLinearVelocity(root, new Vector3d());

        for (final ServerSubLevel member : members) {
            final Pose3dc pose = member.logicalPose();
            final Vector3d position = turn.transform(new Vector3d(pose.position()).sub(rootFrom)).add(rootPosition);
            final Quaterniond orientation = new Quaterniond(turn).mul(pose.orientation());
            final Vector3d linear = pipeline.getLinearVelocity(member, new Vector3d()).sub(rootVelocity);
            final Vector3d angular = pipeline.getAngularVelocity(member, new Vector3d());

            place(pipeline, member, position, orientation);
            if (keepRelativeMotion && member != root) {
                pipeline.addLinearAndAngularVelocity(member, turn.transform(linear), turn.transform(angular));
            }
        }
    }

    private static void place(
            final PhysicsPipeline pipeline,
            final ServerSubLevel subLevel,
            final Vector3dc position,
            final Quaterniondc orientation
    ) {
        final Quaterniond rotation = new Quaterniond(orientation);
        pipeline.teleport(subLevel, position, rotation);
        pipeline.resetVelocity(subLevel);
        subLevel.logicalPose().position().set(position);
        subLevel.logicalPose().orientation().set(rotation);
        subLevel.updateBoundingBox();
    }

    // Pickup

    private static void pickUp(
            final ServerLevel level,
            final Player player,
            final ServerSubLevel subLevel,
            final ItemStack stack
    ) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final List<ServerSubLevel> members = assemble(container, subLevel);
        for (final ServerSubLevel member : members) {
            final double settled = Pym.scale().settled(member);
            if (Math.abs(Pym.scale().of(member) - settled) > PocketedSubLevelEvents.PICKUP_SCALE_TOLERANCE
                    || !Pym.scale().isAt(member, settled)) return;
            if (!PocketedSubLevelEvents.fitsPocket(player, settled)) return;
        }
        if (anchoredToWorld(level, members)) {
            player.displayClientMessage(Component.literal(CrossScaleWelds.POCKET_BLOCKED), true);
            return;
        }

        setHeld(stack, subLevel.getUniqueId());
        recordGroup(stack, members);
        for (final ServerSubLevel member : members) LiveCaseHolds.mark(member.getUniqueId());
    }

    // Release

    private static void release(
            final ServerLevel level,
            final Player player,
            final ItemStack stack,
            final UseOnContext context
    ) {
        final List<ServerSubLevel> members = heldGroup(level, stack);
        setHeld(stack, null);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (members == null || container == null) return;

        final ServerSubLevel root = members.get(0);
        final Pose3dc pose = root.logicalPose();
        final BoundingBox3ic bounds = root.getPlot().getBoundingBox();
        final Vector3d base = new Vector3d(
                (bounds.minX() + bounds.maxX() + 1) * 0.5D,
                bounds.minY(),
                (bounds.minZ() + bounds.maxZ() + 1) * 0.5D);
        final double halfFootprint = Math.max(
                bounds.maxX() - bounds.minX() + 1,
                bounds.maxZ() - bounds.minZ() + 1) * 0.5D * pose.scale().x();

        final Direction face = context.getClickedFace();
        final Vec3 clicked = Sable.HELPER.projectOutOfSubLevel(level, context.getClickLocation());
        final Vector3d target = new Vector3d(clicked.x, clicked.y + RELEASE_LIFT, clicked.z);
        if (face.getAxis().isHorizontal()) {
            target.add(face.getStepX() * halfFootprint, 0.0D, face.getStepZ() * halfFootprint);
        } else if (face == Direction.DOWN) {
            target.y -= (bounds.maxY() - bounds.minY() + 1) * pose.scale().y();
        }

        final Quaterniond facing = new Quaterniond().rotationY(Math.toRadians(-player.getYRot()));
        final Vector3d offset = facing.transform(base.sub(pose.rotationPoint()).mul(pose.scale()));
        moveGroup(container, members, target.sub(offset), facing, false);
    }

    // Cannon

    public static boolean isCannonPayload(final ItemStack stack) {
        return stack.getItem() instanceof LiveCaseItem && held(stack) != null;
    }

    public static boolean launchFromCannon(
            final ServerLevel level,
            final ItemStack payload,
            final Vec3 muzzle,
            final Vec3 motion,
            @Nullable final Entity owner,
            final CannonExpansionMode mode
    ) {
        final UUID id = held(payload);
        if (id == null) return false;
        if (owner instanceof final Player player && player.isCreative()) forgetHeld(player, id);

        final List<ServerSubLevel> members = heldGroup(level, payload);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (members == null || container == null) {
            if (owner instanceof final Player player && !player.isCreative()) {
                final ItemStack empty = payload.copyWithCount(1);
                setHeld(empty, null);
                if (!player.getInventory().add(empty)) player.drop(empty, false);
            }
            return false;
        }

        final Vector3d aim = new Vector3d(motion.x, motion.y, motion.z);
        final Quaterniond nose = aim.lengthSquared() > 1.0E-10D
                ? new Quaterniond().rotationTo(new Vector3d(0.0D, 0.0D, 1.0D), aim.normalize())
                : new Quaterniond(members.get(0).logicalPose().orientation());

        double mass = 0.0D;
        for (final ServerSubLevel member : members) {
            final var massTracker = member.getMassTracker();
            if (massTracker != null) mass += massTracker.getMass();
        }
        launch(level, container, members, muzzle, motion, nose, PocketCaseItem.shotSpeedFactor(mass), mode);
        return true;
    }

    private static void launch(
            final ServerLevel level,
            final ServerSubLevelContainer container,
            final List<ServerSubLevel> members,
            final Vec3 at,
            final Vec3 motion,
            final Quaterniondc orientation,
            final double speedFactor,
            final CannonExpansionMode mode
    ) {
        final ServerSubLevel root = members.get(0);
        final Pose3dc pose = root.logicalPose();
        final BoundingBox3ic bounds = root.getPlot().getBoundingBox();
        final Vector3d centre = new Vector3d(
                (bounds.minX() + bounds.maxX() + 1) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1) * 0.5D);
        final Vector3d offset = orientation.transform(centre.sub(pose.rotationPoint()).mul(pose.scale()), new Vector3d());
        moveGroup(container, members, new Vector3d(at.x, at.y, at.z).sub(offset), orientation, false);
        PocketCaseItem.launchFromMuzzle(level, root, members, at, motion, speedFactor, mode);
    }

    // Throw

    @Override
    public int getUseDuration(final ItemStack stack, final LivingEntity entity) {
        return THROW_USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(final ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (held(stack) == null || player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void releaseUsing(final ItemStack stack, final Level level, final LivingEntity entity, final int remaining) {
        if (!(entity instanceof final Player player) || !(level instanceof final ServerLevel serverLevel)) return;
        final float velocity = PackageItem.getPackageVelocity(getUseDuration(stack, entity) - remaining);
        if (velocity < MIN_THROW_VELOCITY) return;

        final List<ServerSubLevel> members = heldGroup(serverLevel, stack);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (members == null || container == null) return;

        setHeld(stack, null);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getInventory().setChanged();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.5F);

        final BoundingBox3ic bounds = members.get(0).getPlot().getBoundingBox();
        final double clearance = Math.max(bounds.maxX() - bounds.minX() + 1, Math.max(
                bounds.maxY() - bounds.minY() + 1, bounds.maxZ() - bounds.minZ() + 1))
                * 0.5D * members.get(0).logicalPose().scale().x();
        final Vec3 look = player.getLookAngle();
        final Vec3 motion = look.scale(velocity * 2.0D);
        final Vec3 at = new Vec3(player.getX(), player.getY() + player.getBoundingBox().getYsize() * 0.5D, player.getZ())
                .add(motion)
                .add(look.scale(clearance));
        final Quaterniond facing = new Quaterniond().rotationY(Math.toRadians(-player.getYRot()));
        launch(serverLevel, container, members, at, motion, facing, THROW_SPEED_FACTOR, CannonExpansionMode.NONE);
    }

    private static void forgetHeld(final Player player, final UUID id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() instanceof LiveCaseItem && id.equals(held(candidate))) {
                setHeld(candidate, null);
                player.getInventory().setChanged();
                return;
            }
        }
    }

    // Park

    private static void park(final ServerLevel level, final Entity holder, final ItemStack stack, final int slot) {
        final List<ServerSubLevel> members = heldGroup(level, stack);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (members == null || container == null) return;

        final Vec3 at = holder.position();
        final double y = Math.min(at.y + PARK_RISE, level.getMaxBuildHeight() + PARK_ABOVE_BUILD_LIMIT);
        final Vector3d spot = new Vector3d(
                at.x + (slot % PARK_ROW) * PARK_SPACING,
                y,
                at.z + (slot / PARK_ROW) * PARK_SPACING);
        moveGroup(container, members, spot, members.get(0).logicalPose().orientation(), true);
        recordGroup(stack, members);
        for (final ServerSubLevel member : members) LiveCaseHolds.mark(member.getUniqueId());
    }
}
