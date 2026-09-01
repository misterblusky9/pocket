package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterTrackGuideConstraint", remap = false)
public abstract class CoasterTrackGuideConstraintScaleMixin {
    private static final String POCKET$UPDATE =
            "updateAfterSnap("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathEdge;"
                    + "Lorg/joml/Vector3d;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$TrackBasis;"
                    + "Lnet/minecraft/world/phys/Vec3;)V";

    private static final ThreadLocal<ArrayDeque<FrameContext>> POCKET$CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = POCKET$UPDATE, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enter(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object edge,
            final Vector3d bearingPlotCenter,
            @Coerce final Object trackBasis,
            final Vec3 railAnchorWorld,
            final CallbackInfo ci
    ) {
        POCKET$CONTEXT.get().push(new FrameContext(
                cart,
                SimulatedCoastersScaleLookup.serverSubLevelForEdge(level, edge)));
    }

    @WrapOperation(
            method = POCKET$UPDATE,
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;"
                            + "setFrame1(Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V",
                    ordinal = 1),
            remap = false,
            require = 1
    )
    private static void pocket$scaleHostedTrackFrame(
            final GenericConstraintHandle handle,
            final Vector3dc position,
            final Quaterniondc orientation,
            final Operation<Void> original
    ) {
        final FrameContext context = pocket$current();
        final ServerSubLevel track = context == null ? null : context.track();
        original.call(handle, track == null ? position : ScaleFrame.toBodyMetric(track, position), orientation);
    }

    @WrapOperation(
            method = POCKET$UPDATE,
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;"
                            + "setFrame2(Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V"),
            remap = false,
            require = 1
    )
    private static void pocket$scaleCartFrame(
            final GenericConstraintHandle handle,
            final Vector3dc position,
            final Quaterniondc orientation,
            final Operation<Void> original
    ) {
        final FrameContext context = pocket$current();
        final ServerSubLevel cart = context == null ? null : context.cart();
        original.call(handle, cart == null ? position : ScaleFrame.toBodyMetric(cart, position), orientation);
    }

    @Inject(method = POCKET$UPDATE, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exit(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object edge,
            final Vector3d bearingPlotCenter,
            @Coerce final Object trackBasis,
            final Vec3 railAnchorWorld,
            final CallbackInfo ci
    ) {
        final ArrayDeque<FrameContext> stack = POCKET$CONTEXT.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) POCKET$CONTEXT.remove();
    }

    private static FrameContext pocket$current() {
        final ArrayDeque<FrameContext> stack = POCKET$CONTEXT.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private record FrameContext(ServerSubLevel cart, ServerSubLevel track) {}
}
