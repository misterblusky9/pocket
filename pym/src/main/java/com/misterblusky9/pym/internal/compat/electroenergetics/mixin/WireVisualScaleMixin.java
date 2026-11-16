package com.misterblusky9.pym.internal.compat.electroenergetics.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.client.WireVisual", remap = false)
public abstract class WireVisualScaleMixin {
    @Shadow(remap = false) private double prevLength;
    @Shadow(remap = false) @Final private List<TransformedInstance> instances;
    @Shadow(remap = false) private TransformedInstance startInstance;
    @Shadow(remap = false) private TransformedInstance endInstance;

    @Unique private static Method pym$size;
    @Unique private static Method pym$getX;
    @Unique private static Method pym$getY;
    @Unique private static Method pym$getZ;
    @Unique private static Method pym$set;
    @Unique private static Method pym$clear;
    @Unique private static Method pym$add;
    @Unique private static boolean pym$pointsUnavailable;

    @Unique private BlockPos pym$node1;
    @Unique private BlockPos pym$node2;
    @Unique private boolean pym$lookedUpNodes;
    @Unique private boolean pym$rebuilt;
    @Unique private double pym$scale = 1.0D;
    @Unique private double pym$previousScale = Double.NaN;
    @Unique private Vec3 pym$anchor;

    @Inject(method = "recreateInstances", at = @At("HEAD"), remap = false, require = 1)
    private void pym$beforeRebuild(ClientLevel level, float partialTick, boolean force, CallbackInfo ci) {
        pym$rebuilt = false;
        pym$anchor = null;
        pym$scale = pym$ownerScale(level, partialTick);
        if (Double.isNaN(pym$previousScale) || Math.abs(pym$previousScale - pym$scale) > 1.0E-6D) {
            prevLength = -1.0D;
            pym$previousScale = pym$scale;
        }
    }

    @ModifyArgs(
            method = "recreateInstances",
            at = @At(value = "INVOKE", target =
                    "Lcom/george_vi/electroenergetics/foundation/QuadraticWireHelper;"
                    + "wirePoints(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;F"
                    + "Lcom/george_vi/electroenergetics/foundation/WirePoints;)"
                    + "Lcom/george_vi/electroenergetics/foundation/WirePoints;"),
            remap = false, require = 1
    )
    private void pym$nominalCurve(Args args) {
        pym$shrinkToNominal(args);
    }

    @ModifyArgs(
            method = "recreateInstances",
            at = @At(value = "INVOKE", target =
                    "Lcom/george_vi/electroenergetics/foundation/QuadraticWireHelper;"
                    + "wirePointsRaw(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FF"
                    + "Lcom/george_vi/electroenergetics/foundation/WirePoints;)"
                    + "Lcom/george_vi/electroenergetics/foundation/WirePoints;"),
            remap = false, require = 1
    )
    private void pym$nominalRawCurve(Args args) {
        pym$shrinkToNominal(args);
    }

    @Unique
    private void pym$shrinkToNominal(Args args) {
        if (Math.abs(pym$scale - 1.0D) < 1.0E-6D) return;
        Vec3 a = args.get(0);
        Vec3 b = args.get(1);
        pym$anchor = a;
        args.set(1, a.add(b.subtract(a).scale(1.0D / pym$scale)));
    }

    @Inject(method = "createWire", at = @At("HEAD"), remap = false, require = 1)
    private void pym$expandToWorld(CallbackInfo ci) {
        if (pym$anchor == null || Math.abs(pym$scale - 1.0D) < 1.0E-6D) return;
        Object points = pym$points();
        if (points == null) return;
        try {
            int size = (int) pym$size.invoke(points);
            double s = pym$scale;
            double ax = pym$anchor.x;
            double ay = pym$anchor.y;
            double az = pym$anchor.z;
            if (size == 0) return;
            double[] world = new double[size * 3];
            for (int i = 0; i < size; i++) {
                world[i * 3] = ax + ((double) pym$getX.invoke(points, i) - ax) * s;
                world[i * 3 + 1] = ay + ((double) pym$getY.invoke(points, i) - ay) * s;
                world[i * 3 + 2] = az + ((double) pym$getZ.invoke(points, i) - az) * s;
            }
            pym$clear.invoke(points);
            for (int i = 0; i < size; i++) {
                pym$add.invoke(points, world[i * 3], world[i * 3 + 1], world[i * 3 + 2]);
            }
            pym$set.invoke(points, 0, world[0], world[1], world[2]);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Inject(method = "createWire", at = @At("TAIL"), remap = false, require = 1)
    private void pym$scaleSegments(CallbackInfo ci) {
        pym$rebuilt = true;
        if (Math.abs(pym$scale - 1.0D) < 1.0E-6D) return;
        float s = (float) pym$scale;
        for (TransformedInstance segment : instances) {
            segment.scale(s, s, 1.0F).setChanged();
        }
    }

    @Inject(method = "recreateInstances", at = @At("TAIL"), remap = false, require = 1)
    private void pym$scaleEndpoints(ClientLevel level, float partialTick, boolean force, CallbackInfo ci) {
        if (!pym$rebuilt || Math.abs(pym$scale - 1.0D) < 1.0E-6D) return;
        float s = (float) pym$scale;
        if (startInstance != null) startInstance.scale(s, s, s).setChanged();
        if (endInstance != null) endInstance.scale(s, s, s).setChanged();
    }

    @Unique
    private Object pym$points() {
        if (pym$pointsUnavailable) return null;
        try {
            Field field = ((Object) this).getClass().getDeclaredField("points");
            field.setAccessible(true);
            Object points = field.get(this);
            if (points == null) return null;
            if (pym$set == null) {
                Class<?> type = points.getClass();
                pym$size = type.getMethod("size");
                pym$getX = type.getMethod("getX", int.class);
                pym$getY = type.getMethod("getY", int.class);
                pym$getZ = type.getMethod("getZ", int.class);
                pym$clear = type.getMethod("clear");
                pym$add = type.getMethod("add", double.class, double.class, double.class);
                pym$set = type.getMethod("set", int.class, double.class, double.class, double.class);
            }
            return points;
        } catch (ReflectiveOperationException | RuntimeException e) {
            pym$pointsUnavailable = true;
            return null;
        }
    }

    @Unique
    private double pym$ownerScale(ClientLevel level, float partialTick) {
        if (level == null) return 1.0D;
        if (!pym$lookedUpNodes) pym$resolveNodes();
        if (pym$node1 == null || pym$node2 == null) return 1.0D;

        double first = pym$endScale(level, pym$node1, partialTick);
        double second = pym$endScale(level, pym$node2, partialTick);
        if (Double.isNaN(first) || Double.isNaN(second)) return 1.0D;
        return Math.min(first, second);
    }

    @Unique
    private static double pym$endScale(ClientLevel level, BlockPos node, float partialTick) {
        SubLevel containing = Sable.HELPER.getContaining(level, node);
        if (containing == null) return 1.0D;
        if (!(containing instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) return Double.NaN;
        var scale = subLevel.renderPose(partialTick).scale();
        if (Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON
                || !ScaleBounds.isValid(scale.x())) return Double.NaN;
        return scale.x();
    }

    @Unique
    private void pym$resolveNodes() {
        pym$lookedUpNodes = true;
        try {
            Field field = ((Object) this).getClass().getDeclaredField("connection");
            field.setAccessible(true);
            Object connection = field.get(this);
            if (connection == null) return;
            Method node1 = connection.getClass().getMethod("node1");
            Method node2 = connection.getClass().getMethod("node2");
            Object first = node1.invoke(connection);
            Object second = node2.invoke(connection);
            Method sourcePos = first.getClass().getMethod("sourcePos");
            pym$node1 = (BlockPos) sourcePos.invoke(first);
            pym$node2 = (BlockPos) sourcePos.invoke(second);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
