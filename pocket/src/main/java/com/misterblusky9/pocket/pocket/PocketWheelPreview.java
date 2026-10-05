package com.misterblusky9.pocket.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

public final class PocketWheelPreview {
    public static final String WHEELS_KEY = "preview_wheels";

    public static final int STRIDE = 4;

    public static final int RADIUS_UNIT = 1024;

    public static final int NO_ITEM = -1;

    private static final ResourceLocation WHEEL_MOUNT =
            ResourceLocation.fromNamespaceAndPath("offroad", "wheel_mount");
    private static final ResourceLocation TIRE_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("offroad", "tire");

    private static final String HELD_STACK_KEY = "CurrentStack";
    private static final String FACING_PROPERTY = "facing";

    private static final float MIN_RADIUS = 0.05F;
    private static final float MAX_RADIUS = 8.0F;

    /*
     * Shell coordinates are unsigned whole blocks. Wheels are different: their centre can sit
     * half a block outside the shell and coarse previews need fractional centres. Keep the same
     * four-int payload, but mark wheel-centre positions and store each axis in half-block units.
     * The 10-bit fields cover -0.5 through 511 blocks, comfortably beyond the preview grid.
     */
    private static final int CENTRE_FORMAT_BIT = 1 << 30;
    private static final int CENTRE_BITS = 10;
    private static final int CENTRE_MASK = (1 << CENTRE_BITS) - 1;
    private static final int CENTRE_UNIT = 2;
    private static final int CENTRE_BIAS = 1;

    public record Wheel(float x, float y, float z, Direction facing, float radius, int itemId) {}

    public static Wheel capture(
            final Level level,
            final BlockPos pos,
            final BlockState state,
            final int originX,
            final int originY,
            final int originZ
    ) {
        if (level == null || state == null) return null;
        if (!WHEEL_MOUNT.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return null;

        final Direction facing = horizontalFacing(state);
        if (facing == null) return null;

        final ItemStack tire = heldStack(level, pos);
        if (tire == null || tire.isEmpty()) return null;

        final float radius = tireRadius(tire);
        if (!(radius > 0.0F)) return null;

        final BlockPos centre = pos.relative(facing);
        return new Wheel(
                centre.getX() - originX + 0.5F,
                centre.getY() - originY + 0.5F,
                centre.getZ() - originZ + 0.5F,
                facing,
                Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, radius)),
                BuiltInRegistries.ITEM.getId(tire.getItem())
        );
    }

    private static Direction horizontalFacing(final BlockState state) {
        for (final Property<?> property : state.getProperties()) {
            if (!FACING_PROPERTY.equals(property.getName())) continue;
            final Comparable<?> value = state.getValue(property);
            if (value instanceof final Direction direction && direction.getAxis().isHorizontal()) {
                return direction;
            }
        }
        return null;
    }

    private static ItemStack heldStack(final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        try {
            final CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
            if (!tag.contains(HELD_STACK_KEY)) return null;
            return ItemStack.parseOptional(level.registryAccess(), tag.getCompound(HELD_STACK_KEY));
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static float tireRadius(final ItemStack stack) {
        for (final TypedDataComponent<?> component : stack.getComponents()) {
            final ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            if (!TIRE_COMPONENT.equals(key)) continue;
            return radiusOf(component.value());
        }
        return -1.0F;
    }

    private static float radiusOf(final Object tire) {
        if (tire == null) return -1.0F;
        try {
            final Object radius = tire.getClass().getMethod("radius").invoke(tire);
            return radius instanceof final Number number ? number.floatValue() : -1.0F;
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return -1.0F;
        }
    }

    public static int[] encode(final List<Wheel> wheels) {
        if (wheels == null || wheels.isEmpty()) return new int[0];
        final int[] data = new int[wheels.size() * STRIDE];
        int i = 0;
        for (final Wheel wheel : wheels) {
            data[i++] = packCentre(wheel.x(), wheel.y(), wheel.z());
            data[i++] = wheel.facing().get3DDataValue();
            data[i++] = Math.round(wheel.radius() * RADIUS_UNIT);
            data[i++] = wheel.itemId();
        }
        return data;
    }

    public static List<Wheel> decode(final int[] data) {
        if (data == null || data.length < STRIDE) return List.of();
        final List<Wheel> wheels = new ArrayList<>(data.length / STRIDE);
        for (int i = 0; i + STRIDE - 1 < data.length; i += STRIDE) {
            final int packed = data[i];
            final Direction facing = Direction.from3DDataValue(data[i + 1]);
            final float radius = data[i + 2] / (float) RADIUS_UNIT;
            if (!(radius > 0.0F)) continue;

            final float x;
            final float y;
            final float z;
            if ((packed & CENTRE_FORMAT_BIT) != 0) {
                x = unpackCentreAxis(packed, 0);
                y = unpackCentreAxis(packed, CENTRE_BITS);
                z = unpackCentreAxis(packed, CENTRE_BITS * 2);
            } else {
                // Compatibility with the first wheel-preview payload: whole block coordinate,
                // interpreted as that block's centre. Negative edge positions were not representable.
                x = ShellVoxels.unpackX(packed) + 0.5F;
                y = ShellVoxels.unpackY(packed) + 0.5F;
                z = ShellVoxels.unpackZ(packed) + 0.5F;
            }

            wheels.add(new Wheel(x, y, z, facing, radius, data[i + 3]));
        }
        return wheels;
    }

    public static Item itemOf(final Wheel wheel) {
        if (wheel == null || wheel.itemId() == NO_ITEM) return null;
        return BuiltInRegistries.ITEM.byId(wheel.itemId());
    }

    private static int packCentre(final float x, final float y, final float z) {
        return CENTRE_FORMAT_BIT
                | encodeCentreAxis(x)
                | (encodeCentreAxis(y) << CENTRE_BITS)
                | (encodeCentreAxis(z) << (CENTRE_BITS * 2));
    }

    private static int encodeCentreAxis(final float value) {
        final int encoded = Math.round(value * CENTRE_UNIT) + CENTRE_BIAS;
        return Math.max(0, Math.min(CENTRE_MASK, encoded));
    }

    private static float unpackCentreAxis(final int packed, final int shift) {
        final int encoded = (packed >>> shift) & CENTRE_MASK;
        return (encoded - CENTRE_BIAS) / (float) CENTRE_UNIT;
    }

    private PocketWheelPreview() {}
}
