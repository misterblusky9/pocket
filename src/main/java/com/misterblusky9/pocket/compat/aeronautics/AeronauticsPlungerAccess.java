package com.misterblusky9.pocket.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AeronauticsPlungerAccess {
    private static final String CLASS_NAME = "dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity";
    private static Class<?> type;
    private static Method isPlunged;
    private static Method getOther;
    private static Method getAttachmentPos;
    private static Method setData;
    private static EntityDataAccessor<Boolean> isPlungedAccessor;
    private static EntityDataAccessor<BlockPos> blockPosAccessor;
    private static EntityDataAccessor<Direction> directionAccessor;
    private static boolean resolved;

    public static boolean isPlunger(final Entity entity) {
        resolve();
        return entity != null && type != null && type.isInstance(entity);
    }

    public static boolean isPlunged(final Entity entity) {
        resolve();
        if (!isPlunger(entity) || isPlunged == null) return false;
        try {
            return (boolean) isPlunged.invoke(entity);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static Entity getOther(final Entity entity) {
        resolve();
        if (!isPlunger(entity) || getOther == null) return null;
        try {
            return (Entity) getOther.invoke(entity);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Vec3 getAttachmentPos(final Entity entity) {
        resolve();
        if (!isPlunger(entity) || getAttachmentPos == null) return entity != null ? entity.position() : Vec3.ZERO;
        try {
            return (Vec3) getAttachmentPos.invoke(entity);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return entity.position();
        }
    }

    public static void setPlunged(final Entity entity, final boolean value) {
        set(entity, isPlungedAccessor, value);
    }

    public static void setBlockPos(final Entity entity, final BlockPos value) {
        set(entity, blockPosAccessor, value);
    }

    public static void setDirection(final Entity entity, final Direction value) {
        set(entity, directionAccessor, value);
    }

    private static <T> void set(final Entity entity, final EntityDataAccessor<T> accessor, final T value) {
        resolve();
        if (!isPlunger(entity) || setData == null || accessor == null) return;
        try {
            setData.invoke(entity, accessor, value);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            type = Class.forName(CLASS_NAME);
            isPlunged = type.getMethod("isPlunged");
            getOther = type.getMethod("getOther");
            getAttachmentPos = type.getMethod("getAttachmentPos");
            setData = type.getMethod("setData", EntityDataAccessor.class, Object.class);
            isPlungedAccessor = (EntityDataAccessor<Boolean>) field("IS_PLUNGED");
            blockPosAccessor = (EntityDataAccessor<BlockPos>) field("PLUNGED_BLOCK_POS");
            directionAccessor = (EntityDataAccessor<Direction>) field("PLUNGED_DIRECTION");
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            type = null;
            isPlunged = null;
            getOther = null;
            getAttachmentPos = null;
            setData = null;
            isPlungedAccessor = null;
            blockPosAccessor = null;
            directionAccessor = null;
        }
    }

    private static Object field(final String name) throws ReflectiveOperationException {
        final Field field = type.getField(name);
        return field.get(null);
    }

    private AeronauticsPlungerAccess() {}
}
