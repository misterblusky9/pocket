package com.misterblusky9.pocket.physics;

import java.util.HashMap;
import java.util.Map;

public final class ShapeRegistry {
    private static final Map<ColliderShapeKey, Entry> INTERNED = new HashMap<>();

    private static long allocations;
    private static long activeHits;
    private static long inactiveHits;
    private static int activeShapes;

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
        if (entry == null) {
            entry = new Entry(RapierBridge.createCollider(key).handle());
            INTERNED.put(key, entry);
            allocations++;
        } else if (entry.references > 0) {
            activeHits++;
        } else {
            inactiveHits++;
        }

        if (entry.references == 0) activeShapes++;
        entry.references++;
        return entry.handle;
    }

    public static synchronized int handle(final ColliderShapeKey key) {
        final Entry entry = INTERNED.get(key);
        if (entry == null || entry.references <= 0) {
            throw new IllegalStateException("shape is not retained: " + key);
        }
        return entry.handle;
    }

    public static synchronized void release(final ColliderShapeKey key) {
        if (key == null) return;
        final Entry entry = INTERNED.get(key);
        if (entry == null || entry.references <= 0) return;

        entry.references--;
        if (entry.references == 0) activeShapes--;
    }

    public static synchronized String stats() {
        return "activeShapes=" + activeShapes
                + " internedShapes=" + INTERNED.size()
                + " inactiveShapes=" + (INTERNED.size() - activeShapes)
                + " activeHits=" + activeHits
                + " inactiveHits=" + inactiveHits
                + " allocations=" + allocations
                + " recycles=0";
    }

    private ShapeRegistry() {}
}
