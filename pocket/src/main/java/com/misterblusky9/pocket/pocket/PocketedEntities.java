package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.block.SwitchBearingBlockEntity;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.mixin.create.ControlledContraptionEntityControllerInvoker;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class PocketedEntities {
    private static final String ENTITIES_KEY = "pocket_entities";
    private static final String WORLD_SPACE_KEY = "pocket_world_space";

    private static final double CAPTURE_MARGIN = 1.0D;

    public static int capture(final ServerLevel level, final ServerSubLevel subLevel, final CompoundTag target) {
        final ListTag saved = new ListTag();

        for (final Entity entity : collect(level, subLevel)) {
            final SubLevel containing = Sable.HELPER.getContaining(entity);
            if (containing != null && containing != subLevel) continue;
            final boolean worldSpace = containing == null;
            if (worldSpace && entity instanceof BlockAttachedEntity) continue;

            final CompoundTag entityTag = new CompoundTag();

            if (!entity.save(entityTag)) continue;
            if (worldSpace) {
                transform(entityTag, subLevel.logicalPose(), true);
                entityTag.putBoolean(WORLD_SPACE_KEY, true);
            }
            saved.add(entityTag);
            entity.discard();
        }

        if (saved.isEmpty()) return 0;
        target.put(ENTITIES_KEY, saved);
        PocketTrace.scale("captured {} entities with sub-level uuid={}", saved.size(), subLevel.getUniqueId());
        return saved.size();
    }

    public static int restore(final ServerLevel level, final ServerSubLevel subLevel, final CompoundTag source) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;

        final ListTag saved = source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND);
        int restored = 0;

        for (int i = 0; i < saved.size(); i++) {
            final CompoundTag entityTag = saved.getCompound(i);
            try {
                entityTag.remove("UUID");
                if (entityTag.getBoolean(WORLD_SPACE_KEY)) {
                    entityTag.remove(WORLD_SPACE_KEY);
                    transform(entityTag, subLevel.logicalPose(), false);
                }
                final Entity entity = EntityType.loadEntityRecursive(entityTag, level, e -> e);
                if (entity == null) continue;
                if (level.addFreshEntity(entity)) restored++;
            } catch (final RuntimeException exception) {
                PocketTrace.warn("could not restore a pocketed entity: {}", exception.toString());
            }
        }

        if (restored > 0) PocketTrace.scale("restored {} pocketed entities", restored);
        return restored;
    }

    public static int count(final CompoundTag source) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;
        return source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND).size();
    }

    public static int rebase(
            final CompoundTag source,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;

        final ListTag saved = source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            rebaseEntity(saved.getCompound(i), deltaX, deltaY, deltaZ);
        }
        return saved.size();
    }

    private static void rebaseEntity(
            final CompoundTag entity,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        if (entity.contains("Pos", Tag.TAG_LIST)) {
            final ListTag pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                pos.set(0, DoubleTag.valueOf(pos.getDouble(0) + deltaX));
                pos.set(1, DoubleTag.valueOf(pos.getDouble(1) + deltaY));
                pos.set(2, DoubleTag.valueOf(pos.getDouble(2) + deltaZ));
            }
        }

        if (!entity.contains("Passengers", Tag.TAG_LIST)) return;
        final ListTag passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            rebaseEntity(passengers.getCompound(i), deltaX, deltaY, deltaZ);
        }
    }

    private static void transform(final CompoundTag entity, final Pose3dc pose, final boolean inverse) {
        if (entity.contains("Pos", Tag.TAG_LIST)) {
            final ListTag pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                final Vec3 from = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
                writeVec(pos, inverse ? pose.transformPositionInverse(from) : pose.transformPosition(from));
            }
        }

        if (entity.contains("Motion", Tag.TAG_LIST)) {
            final ListTag motion = entity.getList("Motion", Tag.TAG_DOUBLE);
            if (motion.size() >= 3) writeVec(motion, turn(pose, inverse,
                    new Vec3(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2))));
        }

        if (entity.contains("Rotation", Tag.TAG_LIST)) {
            final ListTag rotation = entity.getList("Rotation", Tag.TAG_FLOAT);
            if (rotation.size() >= 2) {
                final float yaw = rotation.getFloat(0);
                final float pitch = rotation.getFloat(1);
                final Vec3 facing = turn(pose, inverse, Vec3.directionFromRotation(0.0F, yaw));
                final Vec3 look = turn(pose, inverse, Vec3.directionFromRotation(pitch, yaw)).normalize();
                rotation.set(0, FloatTag.valueOf(facing.horizontalDistanceSqr() < 1.0E-12D ? yaw
                        : Mth.wrapDegrees((float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90.0F)));
                rotation.set(1, FloatTag.valueOf(Mth.wrapDegrees(
                        (float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG))));
            }
        }

        if (!entity.contains("Passengers", Tag.TAG_LIST)) return;
        final ListTag passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            transform(passengers.getCompound(i), pose, inverse);
        }
    }

    private static Vec3 turn(final Pose3dc pose, final boolean inverse, final Vec3 vector) {
        return inverse ? pose.transformNormalInverse(vector) : pose.transformNormal(vector);
    }

    private static void writeVec(final ListTag list, final Vec3 vector) {
        list.set(0, DoubleTag.valueOf(vector.x));
        list.set(1, DoubleTag.valueOf(vector.y));
        list.set(2, DoubleTag.valueOf(vector.z));
    }

    public static int disassembleContraptions(final ServerLevel level, final ServerSubLevel subLevel) {
        final List<Entity> contraptions = level.getEntities(
                (Entity) null,
                region(subLevel),
                entity -> entity instanceof ControlledContraptionEntity && !entity.isRemoved()
        );

        for (final Entity entity : contraptions) {
            final ControlledContraptionEntity contraption = (ControlledContraptionEntity) entity;
            final IControlContraption controller =
                    ((ControlledContraptionEntityControllerInvoker) contraption).pocket$invokeGetController();

            if (controller instanceof final LinearActuatorBlockEntity actuator) {
                actuator.disassemble();
            } else if (controller instanceof final SwitchBearingBlockEntity bearing) {
                bearing.disassemble();
            } else if (controller instanceof final MechanicalBearingBlockEntity bearing) {
                bearing.disassemble();
            } else {
                contraption.disassemble();
            }
        }

        if (!contraptions.isEmpty()) {
            PocketTrace.scale("disassembled {} contraptions before pocketing sub-level uuid={}",
                    contraptions.size(), subLevel.getUniqueId());
        }
        return contraptions.size();
    }

    private static List<Entity> collect(final ServerLevel level, final ServerSubLevel subLevel) {
        return level.getEntities((Entity) null, region(subLevel), PocketedEntities::isCapturable);
    }

    private static AABB region(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return new AABB(
                bounds.minX() - CAPTURE_MARGIN, bounds.minY() - CAPTURE_MARGIN, bounds.minZ() - CAPTURE_MARGIN,
                bounds.maxX() + 1.0D + CAPTURE_MARGIN,
                bounds.maxY() + 1.0D + CAPTURE_MARGIN,
                bounds.maxZ() + 1.0D + CAPTURE_MARGIN
        );
    }

    private static boolean isCapturable(final Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (entity instanceof Player) return false;

        return !isCreateContraption(entity);
    }

    private static boolean isCreateContraption(final Entity entity) {
        return entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity;
    }

    private PocketedEntities() {}
}
