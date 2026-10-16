package com.misterblusky9.pocket.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class ShellVoxels {
    public static int packPosition(int x, int y, int z) {
        return (x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16);
    }

    public static int unpackX(int packed) { return packed & 0xFF; }
    public static int unpackY(int packed) { return (packed >>> 8) & 0xFF; }
    public static int unpackZ(int packed) { return (packed >>> 16) & 0xFF; }

    public static boolean isExposed(
            BlockGetter level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int y,
            int z
    ) {
        return isAirAt(level, cursor, x - 1, y, z)
                || isAirAt(level, cursor, x + 1, y, z)
                || isAirAt(level, cursor, x, y - 1, z)
                || isAirAt(level, cursor, x, y + 1, z)
                || isAirAt(level, cursor, x, y, z - 1)
                || isAirAt(level, cursor, x, y, z + 1);
    }

    private static boolean isAirAt(
            BlockGetter level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int y,
            int z
    ) {
        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);
        return state.isAir() || !state.canOcclude();
    }

    public static int averageColour(
            BlockGetter level,
            BlockPos pos,
            BlockState state
    ) {
        int colour = state.getMapColor(level, pos).col;
        return colour == 0 ? 0x8A8A8A : (colour & 0xFFFFFF);
    }

    private ShellVoxels() {}
}
