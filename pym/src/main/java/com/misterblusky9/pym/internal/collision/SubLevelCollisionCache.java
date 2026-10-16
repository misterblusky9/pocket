package com.misterblusky9.pym.internal.collision;

import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class SubLevelCollisionCache {
    private static final ThreadLocal<Long2ObjectOpenHashMap<BlockState>> STATES =
            ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);

    public static Iterable<BlockPos> candidates(
            LevelAccelerator accel,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        Long2ObjectOpenHashMap<BlockState> states = STATES.get();
        states.clear();

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return java.util.List.of();
        }

        LongArrayList packed = new LongArrayList();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = accel.getBlockState(pos);
            if (state.isAir()) continue;

            long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            packed.add(key);
            states.put(key, state);
        }

        long[] positions = packed.toLongArray();
        return () -> new Iterator<>() {
            private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            private int index;

            @Override
            public boolean hasNext() {
                return index < positions.length;
            }

            @Override
            public BlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();
                long packedPos = positions[index++];
                return pos.set(BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos));
            }
        };
    }

    public static BlockState state(LevelAccelerator accel, BlockPos pos) {
        BlockState cached = STATES.get().get(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        return cached != null ? cached : accel.getBlockState(pos);
    }

    private SubLevelCollisionCache() {}
}
