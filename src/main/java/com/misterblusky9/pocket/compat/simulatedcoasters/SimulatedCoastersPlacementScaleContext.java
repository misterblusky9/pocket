package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.scale.ScaleController;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayDeque;

public final class SimulatedCoastersPlacementScaleContext {
    private static final ThreadLocal<ArrayDeque<Double>> POCKET$STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Double> POCKET$LAST = ThreadLocal.withInitial(() -> 1.0D);
    private static final Vector3d POCKET$BOGEY_BEARING_LOCAL = new Vector3d(0.5D, 0.5D, 0.5D);

    public static void remember(final double scale) {
        POCKET$LAST.set(sanitize(scale));
    }

    public static double remembered() {
        return sanitize(POCKET$LAST.get());
    }

    public static void push(final double scale) {
        final double clean = sanitize(scale);
        remember(clean);
        POCKET$STACK.get().push(clean);
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
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    public static double scaleForPlacementPose(final Level level, final Object placementPose, final Double partialTick) {
        if (placementPose == null) return 1.0D;
        try {
            final Method graphHit = placementPose.getClass().getMethod("graphHit");
            return SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit.invoke(placementPose), partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 1.0D;
        }
    }

    public static void initializeCartScale(final ServerSubLevel cart, final double requestedScale) {
        if (cart == null || cart.isRemoved()) return;
        final double scale = sanitize(requestedScale);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final Vector3d bearingBefore = cart.logicalPose().transformPosition(new Vector3d(POCKET$BOGEY_BEARING_LOCAL));

        ScaleController.adoptRestoredScale(cart, scale);

        final Vector3d bearingAfter = cart.logicalPose().transformPosition(new Vector3d(POCKET$BOGEY_BEARING_LOCAL));
        final Vector3d correction = bearingBefore.sub(bearingAfter);

        if (correction.lengthSquared() > 1.0E-20D) {
            final Vector3d correctedPosition = new Vector3d(cart.logicalPose().position()).add(correction);
            final var raw = SubLevelContainer.getContainer(cart.getLevel());
            if (raw instanceof final ServerSubLevelContainer container) {
                container.physicsSystem().getPipeline().teleport(
                        cart,
                        correctedPosition,
                        cart.logicalPose().orientation()
                );
            }
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
