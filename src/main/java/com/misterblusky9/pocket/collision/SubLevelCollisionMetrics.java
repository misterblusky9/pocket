package com.misterblusky9.pocket.collision;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class SubLevelCollisionMetrics {
    private static final ThreadLocal<Long2ObjectOpenHashMap<BlockState>> STATES = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);
    private static final ThreadLocal<CountingBoxIterator> BOX_ITERATOR = ThreadLocal.withInitial(CountingBoxIterator::new);

    private static long blockVisits;
    private static long voxelBoxes;
    private static long satCalls;
    private static long maxIterPasses;
    private static long stepProbes;

    public static void begin() {
        STATES.get().clear();
    }

    public static void end() {
        STATES.get().clear();
    }

    public static Iterable<BlockPos> candidates(final LevelAccelerator accel, final BlockPos min, final BlockPos max) {
        final Long2ObjectOpenHashMap<BlockState> states = STATES.get();
        states.clear();
        final LongArrayList packed = new LongArrayList();

        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            blockVisits++;
            final BlockState state = accel.getBlockState(pos);
            if (state.isAir()) continue;
            final long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            packed.add(key);
            states.put(key, state);
        }

        final long[] positions = packed.toLongArray();
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
                final long packedPos = positions[index++];
                return pos.set(BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos));
            }
        };
    }

    public static BlockState state(final LevelAccelerator accel, final BlockPos pos) {
        blockVisits++;
        final BlockState cached = STATES.get().get(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        return cached != null ? cached : accel.getBlockState(pos);
    }

    public static Iterator<BoundingBox3dc> boxes(final Iterator<BoundingBox3dc> iterator) {
        return BOX_ITERATOR.get().set(iterator);
    }

    public static void sat() {
        satCalls++;
    }

    public static void maxIter() {
        maxIterPasses++;
    }

    public static void stepProbe() {
        stepProbes++;
    }

    public static Snapshot snapshot() {
        return new Snapshot(blockVisits, voxelBoxes, satCalls, maxIterPasses, stepProbes);
    }

    public static Snapshot reset() {
        final Snapshot snapshot = snapshot();
        blockVisits = 0L;
        voxelBoxes = 0L;
        satCalls = 0L;
        maxIterPasses = 0L;
        stepProbes = 0L;
        return snapshot;
    }

    public record Snapshot(long blockVisits, long voxelBoxes, long satCalls, long maxIterPasses, long stepProbes) {}

    private static final class CountingBoxIterator implements Iterator<BoundingBox3dc> {
        private Iterator<BoundingBox3dc> delegate;

        private CountingBoxIterator set(final Iterator<BoundingBox3dc> delegate) {
            this.delegate = delegate;
            return this;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public BoundingBox3dc next() {
            voxelBoxes++;
            return delegate.next();
        }
    }

    private SubLevelCollisionMetrics() {}
}
