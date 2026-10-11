package com.misterblusky9.pym.internal.compat.pehkui;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class PehkuiEntityScaling {
    private static final String BACKEND =
            "com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScalingBackend";

    private static volatile boolean present;
    private static volatile Backend backend;

    public static synchronized void initialize() {
        present = ModList.get().isLoaded("pehkui");
        if (!present) return;

        try {
            backend = (Backend) Class.forName(BACKEND).getDeclaredConstructor().newInstance();
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PehkuiEntityScaling::onJoin);
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError exception) {
            PymTrace.warn("Pehkui integration unavailable: {}", exception.toString());
        }
    }

    public static boolean present() {
        return present;
    }

    public static boolean active() {
        return backend != null;
    }

    public static double scale(final Entity entity, final boolean target) {
        final Backend current = backend;
        if (current == null || entity == null) return 1.0D;
        try {
            return current.scale(entity, target);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
            return 1.0D;
        }
    }

    public static double renderScale(final Entity entity, final float partialTick) {
        final Backend current = backend;
        if (current == null || entity == null) return 1.0D;
        try {
            return current.renderScale(entity, partialTick);
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
            return 1.0D;
        }
    }

    public static boolean setScale(final Entity entity, final double scale, final int ticks) {
        final Backend current = backend;
        if (current == null || entity == null || !ScaleBounds.isValid(scale)) return false;
        try {
            current.setScale(entity, scale, Math.max(0, ticks));
            return true;
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
            return false;
        }
    }

    private static void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        final Backend current = backend;
        if (current == null) return;
        try {
            current.settle(event.getEntity());
        } catch (final RuntimeException | LinkageError exception) {
            fail(current, exception);
        }
    }

    public static boolean changing(final Entity entity) {
        final Backend current = backend;
        if (current == null || entity == null) return false;
        try {
            return current.changing(entity);
        } catch (final RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static synchronized void fail(final Backend failed, final Throwable exception) {
        if (backend != failed) return;
        backend = null;

        try {
            failed.disable();
        } catch (final RuntimeException | LinkageError ignored) {
        }

        PymTrace.warn("Pehkui integration disabled after runtime failure: {}", exception.toString());
    }

    interface Backend {
        double scale(Entity entity, boolean target);
        double renderScale(Entity entity, float partialTick);
        void setScale(Entity entity, double scale, int ticks);
        boolean changing(Entity entity);
        void settle(Entity entity);
        void disable();
    }

    private PehkuiEntityScaling() {
    }
}
