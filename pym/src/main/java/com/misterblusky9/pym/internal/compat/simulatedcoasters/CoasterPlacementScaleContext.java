package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayDeque;

public final class CoasterPlacementScaleContext {
    private static final ThreadLocal<ArrayDeque<Double>> PYM$STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Double> PYM$PLACEMENT = ThreadLocal.withInitial(() -> 1.0D);
    private static final Vector3d PYM$BOGEY_BEARING_LOCAL = new Vector3d(0.5D, 0.5D, 0.5D);

    public static void reset() {
        PYM$PLACEMENT.remove();
        PYM$STACK.remove();
    }

    public static void remember(double scale) {
        PYM$PLACEMENT.set(sanitize(scale));
    }

    public static double remembered() {
        return sanitize(PYM$PLACEMENT.get());
    }

    public static void push(double scale) {
        PYM$STACK.get().push(sanitize(scale));
    }

    public static void pop() {
        ArrayDeque<Double> stack = PYM$STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) PYM$STACK.remove();
    }

    public static boolean active() {
        return !PYM$STACK.get().isEmpty();
    }

    public static double current() {
        ArrayDeque<Double> stack = PYM$STACK.get();
        return stack.isEmpty() ? remembered() : stack.peek();
    }

    public static double placementOr(double fallback) {
        double placement = remembered();
        if (Math.abs(placement - 1.0D) > ScaleBounds.EPSILON) return placement;
        return sanitize(fallback);
    }

    public static double scaleForPlacementPose(Level level, Object placementPose, Double partialTick) {
        if (placementPose == null) return 1.0D;
        try {
            Method graphHit = placementPose.getClass().getMethod("graphHit");
            return CoasterScaleLookup.scaleForGraphHit(level, graphHit.invoke(placementPose), partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return remembered();
        }
    }

    public static void initializeCartScale(ServerSubLevel cart, double requestedScale) {
        if (cart == null || cart.isRemoved()) return;
        double scale = sanitize(requestedScale);
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return;

        var raw = SubLevelContainer.getContainer(cart.getLevel());
        if (!(raw instanceof ServerSubLevelContainer container)) return;

        Pose3d unscaledPose = new Pose3d(cart.logicalPose());
        unscaledPose.scale().set(1.0D, 1.0D, 1.0D);
        Vector3d bearingBefore = unscaledPose.transformPosition(new Vector3d(PYM$BOGEY_BEARING_LOCAL));

        if (!Pym.resize().request(cart).scaleTo(scale).immediate().submit().accepted()) return;

        Vector3d bearingAfter = cart.logicalPose().transformPosition(new Vector3d(PYM$BOGEY_BEARING_LOCAL));
        Vector3d correction = bearingBefore.sub(bearingAfter);

        if (correction.lengthSquared() > 1.0E-20D) {
            Vector3d correctedPosition = new Vector3d(cart.logicalPose().position()).add(correction);
            container.physicsSystem().getPipeline().teleport(
                    cart,
                    correctedPosition,
                    cart.logicalPose().orientation()
            );
            cart.logicalPose().position().set(correctedPosition);
            cart.updateBoundingBox();
            cart.updateLastPose();
        }
    }

    private static double sanitize(double scale) {
        if (!ScaleBounds.isValid(scale)) return 1.0D;
        return ScaleBounds.clampValid(scale);
    }

    private CoasterPlacementScaleContext() {}
}
