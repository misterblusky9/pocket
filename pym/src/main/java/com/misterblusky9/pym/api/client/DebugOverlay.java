package com.misterblusky9.pym.api.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public final class DebugOverlay {
    private static final List<BooleanSupplier> CONDITIONS = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled;

    public static void showCollidersWhile(final BooleanSupplier condition) {
        if (condition != null) CONDITIONS.add(condition);
    }

    public static boolean showingColliders() {
        if (enabled) return true;
        for (final BooleanSupplier condition : CONDITIONS) {
            if (condition.getAsBoolean()) return true;
        }
        return false;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    private DebugOverlay() {}
}
