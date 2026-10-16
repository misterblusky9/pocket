package com.misterblusky9.pocket.compat.simulated;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WeldContact {
    public static final int RADIUS = 256;

    public record Projection(Vector3d axisU, Vector3d axisV, double cell) {}

    public static Projection projection(
            Direction sourceFacing,
            Quaterniondc toTarget,
            double cell
    ) {
        Quaterniond rotation = new Quaterniond(toTarget);
        return new Projection(
                rotation.transform(unit(uAxis(sourceFacing))),
                rotation.transform(unit(vAxis(sourceFacing))),
                cell);
    }

    public static Projection projectionFor(WeldRecord record, boolean sourceIsSmall) {
        Quaterniond orientation = new Quaterniond(record.orientation());
        Quaterniond toTarget = sourceIsSmall ? orientation.invert() : orientation;
        double smallSpan = record.spanFor(true);
        double bigSpan = record.spanFor(false);
        double smallToBig = smallSpan <= 0.0D || bigSpan <= 0.0D ? 1.0D : bigSpan / smallSpan;
        return projection(record.facingFor(sourceIsSmall), toTarget, sourceIsSmall ? smallToBig : 1.0D / smallToBig);
    }

    public static Projection identity(Direction facing) {
        return projection(facing, new Quaterniond(), 1.0D);
    }

    public static Set<Long> faceCells(
            Level level,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing
    ) {
        return faceCells(level, source, sourcePos, sourceFacing, RADIUS);
    }

    public static Set<Long> faceCells(
            Level level,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing,
            int radius
    ) {
        Set<Long> found = new HashSet<>();
        if (level == null || (source != null && source.isRemoved())) return found;

        Window window = window(source, sourcePos, sourceFacing, radius);
        if (window.empty()) return found;

        Reader reader = new Reader(level);
        for (int du = window.uMin(); du <= window.uMax(); du++) {
            for (int dv = window.vMin(); dv <= window.vMax(); dv++) {
                if (isFace(reader, source, sourcePos, sourceFacing, du, dv)) found.add(packed(du, dv));
            }
        }
        return found;
    }

    public static Set<Long> cells(
            Level level,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            Projection projection
    ) {
        return cells(level, source, sourcePos, sourceFacing, null, targetPos, targetFacing, targetAnchor, projection);
    }

    public static Set<Long> cells(
            Level level,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing,
            SubLevel target,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            Projection projection
    ) {
        return contactCells(level, source, sourcePos, sourceFacing, target,
                targetPos, targetFacing, targetAnchor, projection, false);
    }

    private static Set<Long> contactCells(
            Level level,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing,
            SubLevel target,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            Projection projection,
            boolean firstOnly
    ) {
        Set<Long> found = new HashSet<>();
        if (level == null || source == null || source.isRemoved()
                || (target != null && target.isRemoved())) return found;

        Window window = window(source, sourcePos, sourceFacing, RADIUS);
        if (window.empty()) return found;

        Reader reader = new Reader(level);
        for (int du = window.uMin(); du <= window.uMax(); du++) {
            for (int dv = window.vMin(); dv <= window.vMax(); dv++) {
                if (contact(reader, source, sourcePos, sourceFacing, target,
                        targetPos, targetFacing, targetAnchor, projection, du, dv)) {
                    found.add(packed(du, dv));
                    if (firstOnly) return found;
                }
            }
        }
        return found;
    }

    public static boolean hasContact(
            Level level,
            SubLevel small,
            SubLevel big,
            WeldRecord record
    ) {
        if (level == null || small == null || record == null) return false;
        if (!record.worldAnchored() && big == null) return false;
        return !contactCells(
                level,
                small,
                record.smallPos(),
                record.smallFacing(),
                big,
                record.bigPos(),
                record.bigFacing(),
                record.anchorFor(false),
                projectionFor(record, true),
                true).isEmpty();
    }

    private static boolean contact(
            Reader reader,
            SubLevel source,
            BlockPos sourcePos,
            Direction sourceFacing,
            SubLevel target,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            Projection projection,
            int du,
            int dv
    ) {
        if (!isFace(reader, source, sourcePos, sourceFacing, du, dv)) return false;

        Direction.Axis targetU = uAxis(targetFacing);
        Direction.Axis targetV = vAxis(targetFacing);
        double[] centre = project(du, dv, projection, targetU, targetV);

        BlockPos against = inPlaneBlock(
                targetPos, targetU, targetV,
                axisOf(targetAnchor, targetU) + centre[0],
                axisOf(targetAnchor, targetV) + centre[1]);

        if (target != null && !target.getPlot().contains(against.getX() + 0.5D, against.getZ() + 0.5D)) {
            return false;
        }

        return !reader.air(against.getX(), against.getY(), against.getZ())
                && reader.air(
                        against.getX() + targetFacing.getStepX(),
                        against.getY() + targetFacing.getStepY(),
                        against.getZ() + targetFacing.getStepZ());
    }

    private static final int BOUNDS_PADDING = 1;
    private static final Window NO_WINDOW = new Window(0, -1, 0, -1);

    private record Window(int uMin, int uMax, int vMin, int vMax) {
        private boolean empty() {
            return this.uMin > this.uMax || this.vMin > this.vMax;
        }
    }

    private static Window window(
            SubLevel source,
            BlockPos origin,
            Direction facing,
            int radius
    ) {
        if (source == null) return new Window(-radius, radius, -radius, radius);

        BoundingBox3ic bounds = source.getPlot().getBoundingBox();
        if (bounds == null
                || bounds.minX() > bounds.maxX()
                || bounds.minY() > bounds.maxY()
                || bounds.minZ() > bounds.maxZ()) {
            return NO_WINDOW;
        }

        Direction.Axis normal = facing.getAxis();
        int plane = origin.get(normal);
        if (plane < boundsMin(bounds, normal) - BOUNDS_PADDING
                || plane > boundsMax(bounds, normal) + BOUNDS_PADDING) {
            return NO_WINDOW;
        }

        Direction.Axis uAxis = uAxis(facing);
        Direction.Axis vAxis = vAxis(facing);
        return new Window(
                Math.max(-radius, boundsMin(bounds, uAxis) - BOUNDS_PADDING - origin.get(uAxis)),
                Math.min(radius, boundsMax(bounds, uAxis) + BOUNDS_PADDING - origin.get(uAxis)),
                Math.max(-radius, boundsMin(bounds, vAxis) - BOUNDS_PADDING - origin.get(vAxis)),
                Math.min(radius, boundsMax(bounds, vAxis) + BOUNDS_PADDING - origin.get(vAxis)));
    }

    private static int boundsMin(BoundingBox3ic bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.minX();
            case Y -> bounds.minY();
            case Z -> bounds.minZ();
        };
    }

    private static int boundsMax(BoundingBox3ic bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.maxX();
            case Y -> bounds.maxY();
            case Z -> bounds.maxZ();
        };
    }

    private static boolean isFace(
            Reader reader,
            SubLevel source,
            BlockPos origin,
            Direction facing,
            int du,
            int dv
    ) {
        Direction.Axis uAxis = uAxis(facing);
        Direction.Axis vAxis = vAxis(facing);
        int x = origin.getX() + (uAxis == Direction.Axis.X ? du : 0) + (vAxis == Direction.Axis.X ? dv : 0);
        int y = origin.getY() + (uAxis == Direction.Axis.Y ? du : 0) + (vAxis == Direction.Axis.Y ? dv : 0);
        int z = origin.getZ() + (uAxis == Direction.Axis.Z ? du : 0) + (vAxis == Direction.Axis.Z ? dv : 0);

        if (source != null && !source.getPlot().contains(x + 0.5D, z + 0.5D)) return false;
        if (reader.air(x, y, z)) return false;
        return reader.air(x + facing.getStepX(), y + facing.getStepY(), z + facing.getStepZ());
    }

    private static final class Reader {
        private final Level level;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private LevelChunk chunk;

        private Reader(Level level) {
            this.level = level;
        }

        private boolean air(int x, int y, int z) {
            if (this.level.isOutsideBuildHeight(y)) return true;
            int cx = SectionPos.blockToSectionCoord(x);
            int cz = SectionPos.blockToSectionCoord(z);
            if (this.chunk == null || cx != this.chunkX || cz != this.chunkZ) {
                this.chunk = this.level.getChunk(cx, cz);
                this.chunkX = cx;
                this.chunkZ = cz;
            }
            return this.chunk.getBlockState(this.cursor.set(x, y, z)).isAir();
        }
    }

    public static double seamDistance(Level level, WeldRecord record, Vec3 point) {
        if (level == null || record == null || point == null) return Double.MAX_VALUE;

        SubLevel containing = Sable.HELPER.getContaining(level, point);
        if (containing == null) {
            return record.worldAnchored() ? worldSeamDistance(level, record, point) : Double.MAX_VALUE;
        }
        if (containing.getUniqueId() == null) return Double.MAX_VALUE;
        UUID id = containing.getUniqueId();

        for (boolean sourceIsSmall : new boolean[] {true, false}) {
            if (!id.equals(record.subLevelFor(sourceIsSmall))) continue;

            Direction facing = record.facingFor(sourceIsSmall);
            BlockPos origin = record.posFor(sourceIsSmall);
            Direction.Axis uAxis = uAxis(facing);
            Direction.Axis vAxis = vAxis(facing);

            Set<Long> cells = cells(
                    level,
                    containing,
                    origin,
                    facing,
                    record.posFor(!sourceIsSmall),
                    record.facingFor(!sourceIsSmall),
                    record.anchorFor(!sourceIsSmall),
                    projectionFor(record, sourceIsSmall));
            if (cells.isEmpty()) continue;

            double plane = WeldGeometry.facePlane(origin, facing);
            double u = axisOf(point, uAxis) - origin.get(uAxis) - 0.5D;
            double v = axisOf(point, vAxis) - origin.get(vAxis) - 0.5D;
            double normal = axisOf(point, facing.getAxis()) - plane;

            double best = Double.MAX_VALUE;
            for (long cell : cells) {
                double du = clamp(u, unpackU(cell) - 0.5D, unpackU(cell) + 0.5D) - u;
                double dv = clamp(v, unpackV(cell) - 0.5D, unpackV(cell) + 0.5D) - v;
                best = Math.min(best, Math.sqrt(du * du + dv * dv + normal * normal));
            }
            if (best < Double.MAX_VALUE) return best;
        }
        return Double.MAX_VALUE;
    }

    private static double worldSeamDistance(Level level, WeldRecord record, Vec3 point) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return Double.MAX_VALUE;
        SubLevel small = container.getSubLevel(record.smallSubLevel());
        if (small == null || small.isRemoved()) return Double.MAX_VALUE;

        Projection projection = projectionFor(record, true);
        Set<Long> cells = cells(
                level,
                small,
                record.smallPos(),
                record.smallFacing(),
                null,
                record.bigPos(),
                record.bigFacing(),
                record.anchorFor(false),
                projection);
        if (cells.isEmpty()) return Double.MAX_VALUE;

        Direction facing = record.bigFacing();
        Direction.Axis uAxis = uAxis(facing);
        Direction.Axis vAxis = vAxis(facing);
        Vector3d anchor = record.anchorFor(false);
        double plane = WeldGeometry.facePlane(record.bigPos(), facing);
        double normal = axisOf(point, facing.getAxis()) - plane;
        double pointU = axisOf(point, uAxis);
        double pointV = axisOf(point, vAxis);
        double half = Math.max(1.0D / 64.0D, Math.abs(projection.cell()) * 0.5D);

        double best = Double.MAX_VALUE;
        for (long cell : cells) {
            double[] projected = project(unpackU(cell), unpackV(cell), projection, uAxis, vAxis);
            double centreU = axisOf(anchor, uAxis) + projected[0];
            double centreV = axisOf(anchor, vAxis) + projected[1];
            double du = clamp(pointU, centreU - half, centreU + half) - pointU;
            double dv = clamp(pointV, centreV - half, centreV + half) - pointV;
            best = Math.min(best, Math.sqrt(du * du + dv * dv + normal * normal));
        }
        return best;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double axisOf(Vec3 value, Direction.Axis axis) {
        return switch (axis) {
            case X -> value.x;
            case Y -> value.y;
            case Z -> value.z;
        };
    }

    public static double[] project(
            double du,
            double dv,
            Projection projection,
            Direction.Axis targetU,
            Direction.Axis targetV
    ) {
        Vector3d displacement = new Vector3d(projection.axisU())
                .mul(du)
                .fma(dv, projection.axisV())
                .mul(projection.cell());
        return new double[] {axisOf(displacement, targetU), axisOf(displacement, targetV)};
    }

    public static Direction.Axis uAxis(Direction facing) {
        return WeldGeometry.uAxis(facing);
    }

    public static Direction.Axis vAxis(Direction facing) {
        return WeldGeometry.vAxis(facing);
    }

    public static double axisOf(Vector3d value, Direction.Axis axis) {
        return switch (axis) {
            case X -> value.x;
            case Y -> value.y;
            case Z -> value.z;
        };
    }

    public static long packed(int du, int dv) {
        return ((long) (du + 512) << 20) | (dv + 512);
    }

    public static int unpackU(long packed) {
        return (int) (packed >> 20) - 512;
    }

    public static int unpackV(long packed) {
        return (int) (packed & 0xFFFFF) - 512;
    }

    public static long originCell() {
        return packed(0, 0);
    }

    private static Vector3d unit(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    private static BlockPos inPlaneBlock(
            BlockPos reference,
            Direction.Axis uAxis,
            Direction.Axis vAxis,
            double u,
            double v
    ) {
        int x = reference.getX();
        int y = reference.getY();
        int z = reference.getZ();

        switch (uAxis) {
            case X -> x = (int) Math.floor(u);
            case Y -> y = (int) Math.floor(u);
            case Z -> z = (int) Math.floor(u);
        }
        switch (vAxis) {
            case X -> x = (int) Math.floor(v);
            case Y -> y = (int) Math.floor(v);
            case Z -> z = (int) Math.floor(v);
        }
        return new BlockPos(x, y, z);
    }

    private WeldContact() {}
}
