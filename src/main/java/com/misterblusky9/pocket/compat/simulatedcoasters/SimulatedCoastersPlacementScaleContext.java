package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.scale.ScaleController;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayDeque;

public final class SimulatedCoastersPlacementScaleContext {
    private static final ThreadLocal<ArrayDeque<Double>> POCKET$STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Double> POCKET$PLACEMENT = ThreadLocal.withInitial(() -> 1.0D);
    private static final Vector3d POCKET$BOGEY_BEARING_LOCAL = new Vector3d(0.5D, 0.5D, 0.5D);

    public static void reset() {
        POCKET$PLACEMENT.remove();
        POCKET$STACK.remove();
    }

    public static void remember(final double scale) {
        POCKET$PLACEMENT.set(sanitize(scale));
    }

    public static double remembered() {
        return sanitize(POCKET$PLACEMENT.get());
    }

    public static void push(final double scale) {
        POCKET$STACK.get().push(sanitize(scale));
    }

    public static void pop() {
        final ArrayDeque<Double> stack = POCKET$STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) POCKET$STACK.remove();
    }

    public static boolean active() {
        return !POCKET$STACK.get().isEmpty();
    }

    public static double current() {
        final ArrayDeque<Double> stack = POCKET$STACK.get();
        return stack.isEmpty() ? remembered() : stack.peek();
    }

    public static double placementOr(final double fallback) {
        final double placement = remembered();
        if (Math.abs(placement - 1.0D) > PocketSized.EPSILON) return placement;
        return sanitize(fallback);
    }

    public static double scaleForPlacementPose(final Level level, final Object placementPose, final Double partialTick) {
        if (placementPose == null) return 1.0D;
        try {
            final Method graphHit = placementPose.getClass().getMethod("graphHit");
            return SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit.invoke(placementPose), partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return remembered();
        }
    }

    public static void initializeCartScale(final ServerSubLevel cart, final double requestedScale) {
        if (cart == null || cart.isRemoved()) return;
        final double scale = sanitize(requestedScale);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final var raw = SubLevelContainer.getContainer(cart.getLevel());
        if (!(raw instanceof final ServerSubLevelContainer container)) return;

        final Pose3d unscaledPose = new Pose3d(cart.logicalPose());
        unscaledPose.scale().set(1.0D, 1.0D, 1.0D);
        final Vector3d bearingBefore = unscaledPose.transformPosition(new Vector3d(POCKET$BOGEY_BEARING_LOCAL));

        ScaleController.adoptRestoredScale(cart, scale);

        final Vector3d bearingAfter = cart.logicalPose().transformPosition(new Vector3d(POCKET$BOGEY_BEARING_LOCAL));
        final Vector3d correction = bearingBefore.sub(bearingAfter);

        if (correction.lengthSquared() > 1.0E-20D) {
            final Vector3d correctedPosition = new Vector3d(cart.logicalPose().position()).add(correction);
            container.physicsSystem().getPipeline().teleport(
                    cart,
                    correctedPosition,
                    cart.logicalPose().orientation()
            );
            cart.logicalPose().position().set(correctedPosition);
            cart.updateBoundingBox();
            cart.updateLastPose();
        }

        ScaleNetwork.sendScale(cart, scale, scale, true);
    }

    private static double sanitize(final double scale) {
        if (!PocketSized.isValidScale(scale)) return 1.0D;
        return PocketSized.clampScale(scale);
    }

    private SimulatedCoastersPlacementScaleContext() {}
}
