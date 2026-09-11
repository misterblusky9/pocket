package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class WeldGeometry {
    public static final double PANEL_THICKNESS = 1.0D / 128.0D;
    private static final double CENTRE_MAGNET = 0.2D;
    private static final double QUARTER_TURN = Math.PI * 0.5D;

    public enum SnapMode {
        SMART,
        MAGNET,
        FREE,
        GRID
    }

    public static int divisor(final CompressionStage smaller, final CompressionStage larger) {
        if (smaller == null || larger == null) return 0;
        final int steps = smaller.depth() - larger.depth();
        return steps < 1 || steps >= CompressionStage.values().length ? 0 : 1 << steps;
    }

    public static double span(final CompressionStage own, final CompressionStage peer) {
        if (own == null || peer == null) return 1.0D;
        return Math.min(1.0D, peer.scale() / own.scale());
    }

    public static double snapAxis(final double value, final int divisor, final SnapMode mode) {
        final double raw = clampUnit(value);
        if (divisor <= 1) return raw;
        if (mode == SnapMode.FREE) return raw;

        final double span = 1.0D / divisor;
        final double half = span * 0.5D;

        if (mode == SnapMode.MAGNET) {
            return nearest(raw, half, 0.5D, 1.0D - half);
        }

        final int cell = Math.max(0, Math.min(divisor - 1, (int) Math.floor(raw * divisor)));
        final double grid = (cell + 0.5D) * span;
        if (mode == SnapMode.GRID) return grid;

        double best = grid;
        if (divisor <= 4 && Math.abs(raw - 0.5D) <= CENTRE_MAGNET * span) best = 0.5D;
        return clamp(best, half, 1.0D - half);
    }

    private static double nearest(final double value, final double... options) {
        double best = options[0];
        for (final double option : options) {
            if (Math.abs(value - option) < Math.abs(value - best)) best = option;
        }
        return best;
    }

    public static Vector3d anchor(
            final BlockPos pos,
            final Direction facing,
            final double hitX,
            final double hitY,
            final double hitZ,
            final int divisor,
            final SnapMode mode
    ) {
        double x = clampUnit(hitX);
        double y = clampUnit(hitY);
        double z = clampUnit(hitZ);

        if (facing.getAxis() != Direction.Axis.X) x = snapAxis(x, divisor, mode);
        if (facing.getAxis() != Direction.Axis.Y) y = snapAxis(y, divisor, mode);
        if (facing.getAxis() != Direction.Axis.Z) z = snapAxis(z, divisor, mode);

        switch (facing) {
            case WEST -> x = 0.0D;
            case EAST -> x = 1.0D;
            case DOWN -> y = 0.0D;
            case UP -> y = 1.0D;
            case NORTH -> z = 0.0D;
            case SOUTH -> z = 1.0D;
        }

        return new Vector3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    public static Vector3d faceCentre(final BlockPos pos, final Direction facing) {
        return anchor(pos, facing, 0.5D, 0.5D, 0.5D, 0, SnapMode.FREE);
    }

    public static AABB faceRect(
            final Direction facing,
            final double plane,
            final double u0,
            final double v0,
            final double u1,
            final double v1,
            final double thickness
    ) {
        final double minU = Math.min(u0, u1);
        final double maxU = Math.max(u0, u1);
        final double minV = Math.min(v0, v1);
        final double maxV = Math.max(v0, v1);

        return switch (facing.getAxis()) {
            case X -> new AABB(plane - thickness, minV, minU, plane + thickness, maxV, maxU);
            case Y -> new AABB(minU, plane - thickness, minV, maxU, plane + thickness, maxV);
            case Z -> new AABB(minU, minV, plane - thickness, maxU, maxV, plane + thickness);
        };
    }

    public static Direction.Axis uAxis(final Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis vAxis(final Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    public static double facePlane(final BlockPos pos, final Direction facing) {
        final int base = pos.get(facing.getAxis());
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? base + 1.0D : base;
    }

    public static Vector3d inPlane(
            final Direction facing,
            final double plane,
            final double u,
            final double v
    ) {
        return switch (facing.getAxis()) {
            case X -> new Vector3d(plane, v, u);
            case Y -> new Vector3d(u, plane, v);
            case Z -> new Vector3d(u, v, plane);
        };
    }

    public static Vector3d normal(final Direction facing) {
        return new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    public static Quaterniond alignment(
            final Quaterniondc current,
            final Vector3dc ownNormal,
            final Vector3dc peerNormal
    ) {
        final Vector3d seam = new Vector3d(ownNormal).negate().normalize();
        final Vector3d peer = new Vector3d(peerNormal).normalize();

        final Quaterniond aligned = new Quaterniond()
                .rotationTo(current.transform(new Vector3d(peer)), seam)
                .mul(current)
                .normalize();

        final Vector3d reference = perpendicular(seam);
        final Vector3d bitangent = new Vector3d(seam).cross(reference);
        final Vector3d probe = aligned.transform(perpendicular(peer));

        final double roll = Math.atan2(probe.dot(bitangent), probe.dot(reference));
        final double correction = Math.round(roll / QUARTER_TURN) * QUARTER_TURN - roll;

        return new Quaterniond()
                .rotationAxis(correction, seam.x, seam.y, seam.z)
                .mul(aligned)
                .normalize();
    }

    private static Vector3d perpendicular(final Vector3dc axis) {
        final Vector3d seed = Math.abs(axis.x()) < 0.5D
                ? new Vector3d(1.0D, 0.0D, 0.0D)
                : new Vector3d(0.0D, 1.0D, 0.0D);
        return seed.cross(axis).normalize();
    }

    private static double clampUnit(final double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(final double value, final double min, final double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private WeldGeometry() {}
}
