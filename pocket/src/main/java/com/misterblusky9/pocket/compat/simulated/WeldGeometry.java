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
    public static final int UNSTEPPED = 1;
    private static final double RATIO_TOLERANCE = 1.0E-6D;
    private static final int MAX_FRACTION_DENOMINATOR = 16;
    private static final double CENTRE_MAGNET = 0.2D;
    private static final double QUARTER_TURN = Math.PI * 0.5D;

    public enum SnapMode {
        SMART,
        MAGNET,
        FREE,
        GRID
    }

    public static int divisor(CompressionStage smaller, CompressionStage larger) {
        if (smaller == null || larger == null) return 0;
        return divisor(smaller.scale(), larger.scale());
    }

    public static int divisor(double smaller, double larger) {
        if (!Double.isFinite(smaller) || !Double.isFinite(larger) || smaller <= 0 || larger <= smaller) return 0;
        double ratio = larger / smaller;
        long whole = Math.round(ratio);
        if (whole >= 2L && whole <= Integer.MAX_VALUE && Math.abs(ratio / whole - 1.0D) <= RATIO_TOLERANCE) {
            return (int) whole;
        }
        double footprint = smaller / larger;
        for (int denominator = 2; denominator <= MAX_FRACTION_DENOMINATOR; denominator++) {
            int numerator = (int) Math.round(footprint * denominator);
            if (numerator <= 1 || numerator >= denominator) continue;
            if (Math.abs(footprint - (double) numerator / denominator) <= RATIO_TOLERANCE) {
                return denominator;
            }
        }
        return UNSTEPPED;
    }

    public static double span(CompressionStage own, CompressionStage peer) {
        if (own == null || peer == null) return 1.0D;
        return span(own.scale(), peer.scale());
    }

    public static double span(double own, double peer) {
        if (!Double.isFinite(own) || !Double.isFinite(peer) || own <= 0 || peer <= 0) return 1.0D;
        return Math.min(1.0D, peer / own);
    }

    public static double snapAxis(double value, int divisor, SnapMode mode) {
        double raw = clampUnit(value);
        if (divisor <= 1) return raw;
        if (mode == SnapMode.FREE) return raw;

        double span = 1.0D / divisor;
        double half = span * 0.5D;

        if (mode == SnapMode.MAGNET) {
            return nearest(raw, half, 0.5D, 1.0D - half);
        }

        int cell = Math.max(0, Math.min(divisor - 1, (int) Math.floor(raw * divisor)));
        double grid = (cell + 0.5D) * span;
        if (mode == SnapMode.GRID) return grid;

        double best = grid;
        if (divisor <= 4 && Math.abs(raw - 0.5D) <= CENTRE_MAGNET * span) best = 0.5D;
        return clamp(best, half, 1.0D - half);
    }

    private static double nearest(double value, double... options) {
        double best = options[0];
        for (double option : options) {
            if (Math.abs(value - option) < Math.abs(value - best)) best = option;
        }
        return best;
    }

    public static double placeAxis(
            double value,
            int divisor,
            double footprint,
            SnapMode mode
    ) {
        if (divisor > UNSTEPPED && mode != SnapMode.FREE && footprint > 0.0D && footprint < 1.0D) {
            int cells = (int) Math.round(footprint * divisor);
            if (cells > 1 && cells < divisor
                    && Math.abs(footprint - (double) cells / divisor) <= RATIO_TOLERANCE) {
                double first = footprint * 0.5D;
                int last = divisor - cells;
                double raw = clampUnit(value);
                if (mode == SnapMode.MAGNET) {
                    int middle = Math.max(0, Math.min(last,
                            (int) Math.round((0.5D - first) * divisor)));
                    return nearest(raw, first, first + (double) middle / divisor,
                            first + (double) last / divisor);
                }
                int index = Math.max(0, Math.min(last,
                        (int) Math.round((raw - first) * divisor)));
                return first + (double) index / divisor;
            }
        }
        if (divisor > UNSTEPPED || mode == SnapMode.FREE || !(footprint > 0.0D && footprint < 1.0D)) {
            return snapAxis(value, divisor, mode);
        }
        double half = footprint * 0.5D;
        return clamp(clampUnit(value), half, 1.0D - half);
    }

    public static Vector3d anchor(
            BlockPos pos,
            Direction facing,
            double hitX,
            double hitY,
            double hitZ,
            int divisor,
            SnapMode mode
    ) {
        return anchor(pos, facing, hitX, hitY, hitZ, divisor, 1.0D, mode);
    }

    public static Vector3d anchor(
            BlockPos pos,
            Direction facing,
            double hitX,
            double hitY,
            double hitZ,
            int divisor,
            double footprint,
            SnapMode mode
    ) {
        double x = clampUnit(hitX);
        double y = clampUnit(hitY);
        double z = clampUnit(hitZ);

        if (facing.getAxis() != Direction.Axis.X) x = placeAxis(x, divisor, footprint, mode);
        if (facing.getAxis() != Direction.Axis.Y) y = placeAxis(y, divisor, footprint, mode);
        if (facing.getAxis() != Direction.Axis.Z) z = placeAxis(z, divisor, footprint, mode);

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

    public static Vector3d faceCentre(BlockPos pos, Direction facing) {
        return anchor(pos, facing, 0.5D, 0.5D, 0.5D, 0, SnapMode.FREE);
    }

    public static AABB faceRect(
            Direction facing,
            double plane,
            double u0,
            double v0,
            double u1,
            double v1,
            double thickness
    ) {
        double minU = Math.min(u0, u1);
        double maxU = Math.max(u0, u1);
        double minV = Math.min(v0, v1);
        double maxV = Math.max(v0, v1);

        return switch (facing.getAxis()) {
            case X -> new AABB(plane - thickness, minV, minU, plane + thickness, maxV, maxU);
            case Y -> new AABB(minU, plane - thickness, minV, maxU, plane + thickness, maxV);
            case Z -> new AABB(minU, minV, plane - thickness, maxU, maxV, plane + thickness);
        };
    }

    public static Direction.Axis uAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis vAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    public static double facePlane(BlockPos pos, Direction facing) {
        int base = pos.get(facing.getAxis());
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? base + 1.0D : base;
    }

    public static Vector3d inPlane(
            Direction facing,
            double plane,
            double u,
            double v
    ) {
        return switch (facing.getAxis()) {
            case X -> new Vector3d(plane, v, u);
            case Y -> new Vector3d(u, plane, v);
            case Z -> new Vector3d(u, v, plane);
        };
    }

    public static Vector3d normal(Direction facing) {
        return new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    public static Quaterniond alignment(
            Quaterniondc current,
            Vector3dc ownNormal,
            Vector3dc peerNormal
    ) {
        Vector3d seam = new Vector3d(ownNormal).negate().normalize();
        Vector3d peer = new Vector3d(peerNormal).normalize();

        Quaterniond aligned = new Quaterniond()
                .rotationTo(current.transform(new Vector3d(peer)), seam)
                .mul(current)
                .normalize();

        Vector3d reference = perpendicular(seam);
        Vector3d bitangent = new Vector3d(seam).cross(reference);
        Vector3d probe = aligned.transform(perpendicular(peer));

        double roll = Math.atan2(probe.dot(bitangent), probe.dot(reference));
        double correction = Math.round(roll / QUARTER_TURN) * QUARTER_TURN - roll;

        return new Quaterniond()
                .rotationAxis(correction, seam.x, seam.y, seam.z)
                .mul(aligned)
                .normalize();
    }

    private static Vector3d perpendicular(Vector3dc axis) {
        Vector3d seed = Math.abs(axis.x()) < 0.5D
                ? new Vector3d(1.0D, 0.0D, 0.0D)
                : new Vector3d(0.0D, 1.0D, 0.0D);
        return seed.cross(axis).normalize();
    }

    private static double clampUnit(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private WeldGeometry() {}
}
