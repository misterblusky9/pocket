package com.misterblusky9.pocket.physics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class ShapeRegistry {
    public static final int MAX_PACKABLE_HANDLE = 0xFFFE;

    private static final Map<ColliderShapeKey, Entry> INTERNED = new HashMap<>();
    private static final ArrayDeque<Entry> FREE = new ArrayDeque<>();

    private static long allocations;
    private static long activeHits;
    private static long inactiveHits;
    private static long recycles;
    private static int activeShapes;
    private static boolean allocationCeilingReached;

    public static final class CapacityExceededException extends IllegalStateException {
        private CapacityExceededException(final String message) {
            super(message);
        }
    }

    private static final class Entry {
        private final int handle;
        private int references;

        private Entry(final int handle) {
            this.handle = handle;
        }
    }

    public static synchronized int retain(final ColliderShapeKey key) {
        if (key == null || key.faces().isEmpty()) {
            throw new IllegalArgumentException("cannot retain an empty collider shape");
        }

        Entry entry = INTERNED.get(key);
        if (entry != null) {
            activeHits++;
            entry.references++;
            return entry.handle;
        }

        entry = FREE.pollFirst();
        if (entry != null) {
            RapierBridge.reprogramCollider(entry.handle, key);
            recycles++;
            inactiveHits++;
        } else {
            if (allocationCeilingReached) throw capacityExceeded(MAX_PACKABLE_HANDLE + 1);
            final int handle = RapierBridge.createCollider(key).handle();
            if (handle < 0 || handle > MAX_PACKABLE_HANDLE) {
                allocationCeilingReached = true;
                throw capacityExceeded(handle);
            }
            if (handle == MAX_PACKABLE_HANDLE) allocationCeilingReached = true;
            entry = new Entry(handle);
            allocations++;
        }

        entry.references = 1;
        INTERNED.put(key, entry);
        activeShapes++;
        return entry.handle;
    }

    public static synchronized int handle(final ColliderShapeKey key) {
        final Entry entry = INTERNED.get(key);
        if (entry == null || entry.references <= 0) {
            throw new IllegalStateException("shape is not retained: " + key);
        }
        ensurePackable(entry.handle);
        return entry.handle;
    }

    public static synchronized void release(final ColliderShapeKey key) {
        if (key == null) return;
        final Entry entry = INTERNED.get(key);
        if (entry == null || entry.references <= 0) return;

        entry.references--;
        if (entry.references != 0) return;

        INTERNED.remove(key, entry);
        FREE.addLast(entry);
        activeShapes--;
    }

    public static synchronized String stats() {
        return "activeShapes=" + activeShapes
                + " internedShapes=" + INTERNED.size()
                + " inactiveShapes=" + FREE.size()
                + " activeHits=" + activeHits
                + " inactiveHits=" + inactiveHits
                + " allocations=" + allocations
                + " recycles=" + recycles;
    }

    private static void ensurePackable(final int handle) {
        if (handle < 0 || handle > MAX_PACKABLE_HANDLE) throw capacityExceeded(handle);
    }

    private static CapacityExceededException capacityExceeded(final int handle) {
        return new CapacityExceededException(
                "Sable collider handle cannot fit packed 16-bit voxel state: " + handle
                        + " (max " + MAX_PACKABLE_HANDLE + ")");
    }

    private ShapeRegistry() {}
}
