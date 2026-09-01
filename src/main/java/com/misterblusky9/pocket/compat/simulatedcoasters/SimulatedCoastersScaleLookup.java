package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public final class SimulatedCoastersScaleLookup {
    private static volatile Class<?> pocket$hitClass;
    private static volatile Method pocket$edgeMethod;
    private static volatile Class<?> pocket$edgeClass;
    private static volatile Method pocket$fromMethod;
    private static volatile Method pocket$toMethod;

    public static double scaleForGraphHit(final Level level, final Object graphHit, final Double partialTick) {
        if (level == null || graphHit == null) return 1.0D;

        try {
            final Object edge = edge(graphHit);
            if (edge == null) return 1.0D;

            final BlockPos from = (BlockPos) from(edge);
            final BlockPos to = (BlockPos) to(edge);

            SubLevel subLevel = from == null ? null : Sable.HELPER.getContaining(level, from);
            if (subLevel == null && to != null) subLevel = Sable.HELPER.getContaining(level, to);
            return scaleOf(subLevel, partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 1.0D;
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
            scale = ScaleFrame.scaleOf(server);
        } else if (subLevel instanceof final ClientSubLevel client) {
            scale = partialTick == null
                    ? client.renderPose().scale().x()
                    : client.renderPose(partialTick.floatValue()).scale().x();
        } else {
            scale = subLevel.logicalPose().scale().x();
        }

        if (!PocketSized.isValidScale(scale)) return 1.0D;
        return PocketSized.clampScale(scale);
    }

    private static Object edge(final Object graphHit) throws ReflectiveOperationException {
        Method method = pocket$edgeMethod;
        if (method == null || pocket$hitClass != graphHit.getClass()) {
            synchronized (SimulatedCoastersScaleLookup.class) {
                if (pocket$edgeMethod == null || pocket$hitClass != graphHit.getClass()) {
                    pocket$hitClass = graphHit.getClass();
                    pocket$edgeMethod = pocket$hitClass.getMethod("edge");
                }
                method = pocket$edgeMethod;
            }
        }
        return method.invoke(graphHit);
    }

    private static Object from(final Object edge) throws ReflectiveOperationException {
        ensureEdgeMethods(edge);
        return pocket$fromMethod.invoke(edge);
    }

    private static Object to(final Object edge) throws ReflectiveOperationException {
        ensureEdgeMethods(edge);
        return pocket$toMethod.invoke(edge);
    }

    private static void ensureEdgeMethods(final Object edge) throws NoSuchMethodException {
        if (edge == null) return;
        if (pocket$fromMethod != null && pocket$toMethod != null && pocket$edgeClass == edge.getClass()) return;

        synchronized (SimulatedCoastersScaleLookup.class) {
            if (pocket$fromMethod == null || pocket$toMethod == null || pocket$edgeClass != edge.getClass()) {
                pocket$edgeClass = edge.getClass();
                pocket$fromMethod = pocket$edgeClass.getMethod("from");
                pocket$toMethod = pocket$edgeClass.getMethod("to");
            }
        }
    }

    private SimulatedCoastersScaleLookup() {}
}
