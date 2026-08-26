package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.debug.PocketTrace;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

public final class PehkuiScaleBridge {
    private static final String BACKEND =
            "com.misterblusky9.pocket.entity.PehkuiScaleBackend";

    private static boolean initialized;
    private static boolean present;
    private static Backend backend;

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        present = ModList.get().isLoaded("pehkui");

        if (!present) return;

        try {
            final Class<?> type = Class.forName(BACKEND);
            backend = (Backend) type.getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            backend = null;
            PocketTrace.warn("Pehkui integration unavailable: {}", exception.toString());
        }
    }

    public static boolean ownsScaling() {
        if (!initialized) initialize();
        return present;
    }

    public static boolean isOperational() {
        if (!initialized) initialize();
        return backend != null;
    }

    public static void apply(
            final Entity entity,
            final double inheritedBaseScale,
            final double containedModelScale
    ) {
        if (entity == null || !ownsScaling()) return;

        final Backend current = backend;
        if (current == null) return;

        try {
            current.apply(entity, inheritedBaseScale, containedModelScale);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
        }
    }

    public static void clear(final Entity entity) {
        if (entity == null || !ownsScaling()) return;

        final Backend current = backend;
        if (current == null) return;

        try {
            current.clear(entity);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
        }
    }

    public static void setPersonalScale(final Entity entity, final double scale) {
        if (entity == null || !ownsScaling()) return;

        final Backend current = backend;
        if (current == null) return;

        try {
            current.setPersonalScale(entity, scale);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
        }
    }

    public static void clearPersonalScale(final Entity entity) {
        if (entity == null || !ownsScaling()) return;

        final Backend current = backend;
        if (current == null) return;

        try {
            current.clearPersonalScale(entity);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
        }
    }

    private static synchronized void fail(
            final Backend failed,
            final Throwable exception
    ) {
        if (backend != failed) return;

        try {
            failed.disable();
        } catch (final RuntimeException | LinkageError ignored) {
        }

        backend = null;
        PocketTrace.warn("Pehkui integration disabled after runtime failure: {}", exception.toString());
    }

    public interface Backend {
        void apply(Entity entity, double inheritedBaseScale, double containedModelScale);
        void clear(Entity entity);
        void setPersonalScale(Entity entity, double scale);
        void clearPersonalScale(Entity entity);
        void disable();
    }

    private PehkuiScaleBridge() {
    }
}
