package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class CoasterScaleLookup {
    private static volatile Class<?> pym$hitClass;
    private static volatile Method pym$edgeMethod;
    private static volatile Class<?> pym$pointHitClass;
    private static volatile Method pym$pointMethod;
    private static volatile Class<?> pym$edgeClass;
    private static volatile Method pym$fromMethod;
    private static volatile Method pym$toMethod;

    public static double scaleForGraphHit(final Level level, final Object graphHit, final Double partialTick) {
        return scaleForGraphHit(level, graphHit, partialTick, 1.0D);
    }

    public static double scaleForGraphHit(
            final Level level,
            final Object graphHit,
            final Double partialTick,
            final double failureFallback
    ) {
        final double fallback = sanitizeFallback(failureFallback);
        if (level == null || graphHit == null) return fallback;

        try {
            final Object edge = edge(graphHit);
            if (edge == null) return fallback;

            final BlockPos from = (BlockPos) from(edge);
            final BlockPos to = (BlockPos) to(edge);

            SubLevel subLevel = from == null ? null : Sable.HELPER.getContaining(level, from);
            if (subLevel == null && to != null) subLevel = Sable.HELPER.getContaining(level, to);
            return subLevel == null ? 1.0D : scaleOf(subLevel, partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    public static Vec3 pointForGraphHit(final Object graphHit) {
        if (graphHit == null) return null;
        try {
            Method method = pym$pointMethod;
            if (method == null || pym$pointHitClass != graphHit.getClass()) {
                synchronized (CoasterScaleLookup.class) {
                    if (pym$pointMethod == null || pym$pointHitClass != graphHit.getClass()) {
                        pym$pointHitClass = graphHit.getClass();
                        pym$pointMethod = pym$pointHitClass.getMethod("point");
                    }
                    method = pym$pointMethod;
                }
            }
            final Object point = method.invoke(graphHit);
            return point instanceof Vec3 vec ? vec : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static ServerSubLevel serverSubLevelForEdge(final ServerLevel level, final Object edge) {
        if (level == null || edge == null) return null;
        try {
            final BlockPos from = (BlockPos) from(edge);
            final BlockPos to = (BlockPos) to(edge);
            SubLevel subLevel = from == null ? null : Sable.HELPER.getContaining(level, from);
            if (subLevel == null && to != null) subLevel = Sable.HELPER.getContaining(level, to);
            return subLevel instanceof ServerSubLevel server ? server : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static double scaleOf(final SubLevel subLevel, final Double partialTick) {
        if (subLevel == null) return 1.0D;

        final double scale;
        if (subLevel instanceof final ServerSubLevel server) {
            scale = Pym.scale().ofBody(server);
        } else if (subLevel instanceof final ClientSubLevel client) {
            scale = partialTick == null
                    ? client.renderPose().scale().x()
                    : client.renderPose(partialTick.floatValue()).scale().x();
        } else {
            scale = subLevel.logicalPose().scale().x();
        }

        if (!ScaleBounds.isValid(scale)) return 1.0D;
        return ScaleBounds.clampValid(scale);
    }

    private static double sanitizeFallback(final double scale) {
        if (!ScaleBounds.isValid(scale)) return 1.0D;
        return ScaleBounds.clampValid(scale);
    }

    private static Object edge(final Object graphHit) throws ReflectiveOperationException {
        Method method = pym$edgeMethod;
        if (method == null || pym$hitClass != graphHit.getClass()) {
            synchronized (CoasterScaleLookup.class) {
                if (pym$edgeMethod == null || pym$hitClass != graphHit.getClass()) {
                    pym$hitClass = graphHit.getClass();
                    pym$edgeMethod = pym$hitClass.getMethod("edge");
                }
                method = pym$edgeMethod;
            }
        }
        return method.invoke(graphHit);
    }

    private static Object from(final Object edge) throws ReflectiveOperationException {
        ensureEdgeMethods(edge);
        return pym$fromMethod.invoke(edge);
    }

    private static Object to(final Object edge) throws ReflectiveOperationException {
        ensureEdgeMethods(edge);
        return pym$toMethod.invoke(edge);
    }

    private static void ensureEdgeMethods(final Object edge) throws NoSuchMethodException {
        if (edge == null) return;
        if (pym$fromMethod != null && pym$toMethod != null && pym$edgeClass == edge.getClass()) return;

        synchronized (CoasterScaleLookup.class) {
            if (pym$fromMethod == null || pym$toMethod == null || pym$edgeClass != edge.getClass()) {
                pym$edgeClass = edge.getClass();
                pym$fromMethod = pym$edgeClass.getMethod("from");
                pym$toMethod = pym$edgeClass.getMethod("to");
            }
        }
    }

    private CoasterScaleLookup() {}
}
