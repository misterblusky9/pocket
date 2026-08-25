package com.misterblusky9.pocket.physics;

import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColliderGeometry {
    public record SectionKey(int x, int y, int z) {}

    public record CellBox(int cellX, int cellY, int cellZ, ColliderShapeKey.Face face) {}

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int cellX, int cellY, int cellZ, ColliderShapeKey.Face face);
    }

    public static void visitDetailed(
            final PlotShape shape,
            final Vector3dc pivot,
            final double scale,
            final CellConsumer consumer
    ) {
        if (shape == null || pivot == null || consumer == null || !validScale(scale)) return;
        for (final PlotShape.Box box : shape.boxes()) {
            appendFragments(
                    consumer, scale,
                    ScaleFrame.contract(pivot.x(), box.minX(), scale),
                    ScaleFrame.contract(pivot.y(), box.minY(), scale),
                    ScaleFrame.contract(pivot.z(), box.minZ(), scale),
                    ScaleFrame.contract(pivot.x(), box.maxX(), scale),
                    ScaleFrame.contract(pivot.y(), box.maxY(), scale),
                    ScaleFrame.contract(pivot.z(), box.maxZ(), scale));
        }
    }

    public static void visitDetailedSections(
            final PlotShape shape,
            final Vector3dc pivot,
            final double scale,
            final Set<SectionKey> requested,
            final CellConsumer consumer
    ) {
        if (shape == null || pivot == null || requested == null || requested.isEmpty()
                || consumer == null || !validScale(scale)) return;

        for (final PlotShape.Box source : shape.boxes()) {
            final double minX = ScaleFrame.contract(pivot.x(), source.minX(), scale);
            final double minY = ScaleFrame.contract(pivot.y(), source.minY(), scale);
            final double minZ = ScaleFrame.contract(pivot.z(), source.minZ(), scale);
            final double maxX = ScaleFrame.contract(pivot.x(), source.maxX(), scale);
            final double maxY = ScaleFrame.contract(pivot.y(), source.maxY(), scale);
            final double maxZ = ScaleFrame.contract(pivot.z(), source.maxZ(), scale);
            if (maxX <= minX || maxY <= minY || maxZ <= minZ) continue;

            final int firstSectionX = Math.floorDiv(floor(minX), 16);
            final int firstSectionY = Math.floorDiv(floor(minY), 16);
            final int firstSectionZ = Math.floorDiv(floor(minZ), 16);
            final int lastSectionX = Math.floorDiv(ceilExclusive(maxX) - 1, 16);
            final int lastSectionY = Math.floorDiv(ceilExclusive(maxY) - 1, 16);
            final int lastSectionZ = Math.floorDiv(ceilExclusive(maxZ) - 1, 16);

            for (int sx = firstSectionX; sx <= lastSectionX; sx++) {
                final double sectionMinX = sx * 16.0D;
                final double sectionMaxX = sectionMinX + 16.0D;
                for (int sy = firstSectionY; sy <= lastSectionY; sy++) {
                    final double sectionMinY = sy * 16.0D;
                    final double sectionMaxY = sectionMinY + 16.0D;
                    for (int sz = firstSectionZ; sz <= lastSectionZ; sz++) {
                        final SectionKey key = new SectionKey(sx, sy, sz);
                        if (!requested.contains(key)) continue;
                        final double sectionMinZ = sz * 16.0D;
                        final double sectionMaxZ = sectionMinZ + 16.0D;
                        appendFragments(
                                consumer, scale,
                                Math.max(minX, sectionMinX),
                                Math.max(minY, sectionMinY),
                                Math.max(minZ, sectionMinZ),
                                Math.min(maxX, sectionMaxX),
                                Math.min(maxY, sectionMaxY),
                                Math.min(maxZ, sectionMaxZ));
                    }
                }
            }
        }
    }

    public static List<CellBox> prism(
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final double scale
    ) {
        if (!validScale(scale)) return List.of();
        final List<CellBox> result = new ArrayList<>();
        appendFragments((x, y, z, face) -> result.add(new CellBox(x, y, z, face)), scale,
                minX, minY, minZ, maxX, maxY, maxZ);
        return result;
    }

    public static List<CellBox> prismShell(
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final double scale
    ) {
        if (!validScale(scale)) return List.of();
        final List<CellBox> result = new ArrayList<>();
        visitPrismShell(minX, minY, minZ, maxX, maxY, maxZ, scale,
                (x, y, z, face) -> result.add(new CellBox(x, y, z, face)));
        return result;
    }

    public static long shellCells(
            final int firstX, final int firstY, final int firstZ,
            final int lastX, final int lastY, final int lastZ
    ) {
        final long x = (long) lastX - firstX + 1L;
        final long y = (long) lastY - firstY + 1L;
        final long z = (long) lastZ - firstZ + 1L;
        if (x <= 0L || y <= 0L || z <= 0L) return 0L;
        final long full = saturatedMultiply(saturatedMultiply(x, y), z);
        if (full == Long.MAX_VALUE) return Long.MAX_VALUE;
        final long inner = saturatedMultiply(
                saturatedMultiply(Math.max(0L, x - 2L), Math.max(0L, y - 2L)),
                Math.max(0L, z - 2L));
        if (inner == Long.MAX_VALUE) return Long.MAX_VALUE;
        return full - inner;
    }

    public static void visitPrismShell(
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ,
            final double scale,
            final CellConsumer consumer
    ) {
        if (!validScale(scale) || consumer == null) return;
        final int firstX = floor(minX), lastX = ceilExclusive(maxX) - 1;
        final int firstY = floor(minY), lastY = ceilExclusive(maxY) - 1;
        final int firstZ = floor(minZ), lastZ = ceilExclusive(maxZ) - 1;
        if (lastX < firstX || lastY < firstY || lastZ < firstZ) return;

        for (int y = firstY; y <= lastY; y++) {
            final boolean capY = y == firstY || y == lastY;
            for (int z = firstZ; z <= lastZ; z++) {
                final boolean capZ = z == firstZ || z == lastZ;
                if (capY || capZ) {
                    for (int x = firstX; x <= lastX; x++) {
                        emitCell(consumer, scale, minX, minY, minZ, maxX, maxY, maxZ, x, y, z);
                    }
                    continue;
                }
                emitCell(consumer, scale, minX, minY, minZ, maxX, maxY, maxZ, firstX, y, z);
                if (lastX != firstX) {
                    emitCell(consumer, scale, minX, minY, minZ, maxX, maxY, maxZ, lastX, y, z);
                }
            }
        }
    }

    public static List<ColliderShapeKey.Face> reduceExact(final List<ColliderShapeKey.Face> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<ColliderShapeKey.Face> faces = new ArrayList<>(ColliderShapeKey.of(input).faces());
        if (faces.size() < 2) return List.copyOf(faces);

        faces = removeContained(faces);
        boolean changed;
        do {
            final int before = faces.size();
            faces = mergeAxis(faces, 0);
            faces = mergeAxis(faces, 1);
            faces = mergeAxis(faces, 2);
            faces = removeContained(faces);
            changed = faces.size() < before;
        } while (changed && faces.size() > 1);
        return ColliderShapeKey.of(faces).faces();
    }

    private record AxisKey(long a0, long a1, long a2, long a3) {}

    private static List<ColliderShapeKey.Face> mergeAxis(
            final List<ColliderShapeKey.Face> faces,
            final int axis
    ) {
        if (faces.size() < 2) return faces;
        final Map<AxisKey, List<ColliderShapeKey.Face>> groups = new HashMap<>();
        for (final ColliderShapeKey.Face face : faces) {
            groups.computeIfAbsent(axisKey(face, axis), ignored -> new ArrayList<>()).add(face);
        }

        final ArrayList<ColliderShapeKey.Face> result = new ArrayList<>(faces.size());
        for (final List<ColliderShapeKey.Face> group : groups.values()) {
            group.sort(java.util.Comparator
                    .comparingDouble((ColliderShapeKey.Face face) -> axisMin(face, axis))
                    .thenComparingDouble(face -> axisMax(face, axis)));
            ColliderShapeKey.Face current = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                final ColliderShapeKey.Face next = group.get(i);
                if (axisMin(next, axis) <= axisMax(current, axis)) {
                    current = extendAxis(current, next, axis);
                } else {
                    result.add(current);
                    current = next;
                }
            }
            result.add(current);
        }
        return result;
    }

    private static AxisKey axisKey(final ColliderShapeKey.Face face, final int axis) {
        return switch (axis) {
            case 0 -> new AxisKey(face.minY(), face.minZ(), face.maxY(), face.maxZ());
            case 1 -> new AxisKey(face.minX(), face.minZ(), face.maxX(), face.maxZ());
            default -> new AxisKey(face.minX(), face.minY(), face.maxX(), face.maxY());
        };
    }

    private static double axisMin(final ColliderShapeKey.Face face, final int axis) {
        return axis == 0 ? face.minXd() : axis == 1 ? face.minYd() : face.minZd();
    }

    private static double axisMax(final ColliderShapeKey.Face face, final int axis) {
        return axis == 0 ? face.maxXd() : axis == 1 ? face.maxYd() : face.maxZd();
    }

    private static ColliderShapeKey.Face extendAxis(
            final ColliderShapeKey.Face a,
            final ColliderShapeKey.Face b,
            final int axis
    ) {
        return switch (axis) {
            case 0 -> rawFace(Math.min(a.minXd(), b.minXd()), a.minYd(), a.minZd(),
                    Math.max(a.maxXd(), b.maxXd()), a.maxYd(), a.maxZd());
            case 1 -> rawFace(a.minXd(), Math.min(a.minYd(), b.minYd()), a.minZd(),
                    a.maxXd(), Math.max(a.maxYd(), b.maxYd()), a.maxZd());
            default -> rawFace(a.minXd(), a.minYd(), Math.min(a.minZd(), b.minZd()),
                    a.maxXd(), a.maxYd(), Math.max(a.maxZd(), b.maxZd()));
        };
    }

    private static List<ColliderShapeKey.Face> removeContained(final List<ColliderShapeKey.Face> input) {
        final ArrayList<ColliderShapeKey.Face> faces = new ArrayList<>(input);
        for (int i = faces.size() - 1; i >= 0; i--) {
            final ColliderShapeKey.Face inner = faces.get(i);
            for (int j = 0; j < faces.size(); j++) {
                if (i == j) continue;
                if (contains(faces.get(j), inner)) {
                    faces.remove(i);
                    break;
                }
            }
        }
        return faces;
    }

    private static boolean contains(final ColliderShapeKey.Face outer, final ColliderShapeKey.Face inner) {
        return outer.minXd() <= inner.minXd() && outer.minYd() <= inner.minYd()
                && outer.minZd() <= inner.minZd() && outer.maxXd() >= inner.maxXd()
                && outer.maxYd() >= inner.maxYd() && outer.maxZd() >= inner.maxZd();
    }

    private static ColliderShapeKey.Face rawFace(
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ
    ) {
        return new ColliderShapeKey.Face(
                Double.doubleToLongBits(minX), Double.doubleToLongBits(minY), Double.doubleToLongBits(minZ),
                Double.doubleToLongBits(maxX), Double.doubleToLongBits(maxY), Double.doubleToLongBits(maxZ));
    }

    public static List<ColliderShapeKey.Face> capCell(
            final List<ColliderShapeKey.Face> faces,
            final double scale,
            final int maxBoxes
    ) {
        if (faces == null || faces.size() <= maxBoxes) return faces;

        final int side = bucketSide(maxBoxes);
        final double[] bounds = new double[side * side * side * 6];
        final boolean[] used = new boolean[side * side * side];

        for (final ColliderShapeKey.Face face : faces) {
            final int bucket = bucketOf(face, side);
            final int at = bucket * 6;
            if (!used[bucket]) {
                used[bucket] = true;
                bounds[at] = face.minXd();
                bounds[at + 1] = face.minYd();
                bounds[at + 2] = face.minZd();
                bounds[at + 3] = face.maxXd();
                bounds[at + 4] = face.maxYd();
                bounds[at + 5] = face.maxZd();
                continue;
            }
            bounds[at] = Math.min(bounds[at], face.minXd());
            bounds[at + 1] = Math.min(bounds[at + 1], face.minYd());
            bounds[at + 2] = Math.min(bounds[at + 2], face.minZd());
            bounds[at + 3] = Math.max(bounds[at + 3], face.maxXd());
            bounds[at + 4] = Math.max(bounds[at + 4], face.maxYd());
            bounds[at + 5] = Math.max(bounds[at + 5], face.maxZd());
        }

        final List<ColliderShapeKey.Face> capped = new ArrayList<>();
        for (int bucket = 0; bucket < used.length; bucket++) {
            if (!used[bucket]) continue;
            final int at = bucket * 6;
            capped.add(ColliderShapeKey.face(scale,
                    bounds[at], bounds[at + 1], bounds[at + 2],
                    bounds[at + 3], bounds[at + 4], bounds[at + 5]));
        }
        return capped;
    }

    private static int bucketSide(final int maxBoxes) {
        int side = 1;
        while (((side * 2) * (side * 2) * (side * 2)) <= maxBoxes) side *= 2;
        return side;
    }

    private static int bucketOf(final ColliderShapeKey.Face face, final int side) {
        final int x = axisBucket((face.minXd() + face.maxXd()) * 0.5D, side);
        final int y = axisBucket((face.minYd() + face.maxYd()) * 0.5D, side);
        final int z = axisBucket((face.minZd() + face.maxZd()) * 0.5D, side);
        return x + side * (z + side * y);
    }

    private static int axisBucket(final double centre, final int side) {
        return Math.max(0, Math.min(side - 1, (int) (centre * side)));
    }

    private static void appendFragments(
            final CellConsumer consumer,
            final double scale,
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ
    ) {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return;
        final int firstX = floor(minX), lastX = ceilExclusive(maxX) - 1;
        final int firstY = floor(minY), lastY = ceilExclusive(maxY) - 1;
        final int firstZ = floor(minZ), lastZ = ceilExclusive(maxZ) - 1;
        for (int x = firstX; x <= lastX; x++) {
            for (int z = firstZ; z <= lastZ; z++) {
                for (int y = firstY; y <= lastY; y++) {
                    emitCell(consumer, scale, minX, minY, minZ, maxX, maxY, maxZ, x, y, z);
                }
            }
        }
    }

    private static void emitCell(
            final CellConsumer consumer,
            final double scale,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final int x, final int y, final int z
    ) {
        final double cellMinX = Math.max(minX, x) - x;
        final double cellMaxX = Math.min(maxX, x + 1.0D) - x;
        if (cellMaxX <= cellMinX) return;
        final double cellMinZ = Math.max(minZ, z) - z;
        final double cellMaxZ = Math.min(maxZ, z + 1.0D) - z;
        if (cellMaxZ <= cellMinZ) return;
        final double cellMinY = Math.max(minY, y) - y;
        final double cellMaxY = Math.min(maxY, y + 1.0D) - y;
        if (cellMaxY <= cellMinY) return;

        final ColliderShapeKey.Face face = ColliderShapeKey.face(
                scale, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ);
        if (!face.degenerate()) consumer.accept(x, y, z, face);
    }

    private static long saturatedMultiply(final long left, final long right) {
        if (left == 0L || right == 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static boolean validScale(final double scale) {
        return Double.isFinite(scale) && scale > 0.0D && scale <= 1.0D;
    }

    private static int floor(final double value) {
        return (int) Math.floor(value);
    }

    private static int ceilExclusive(final double value) {
        return (int) Math.ceil(value - 1.0E-10D);
    }

    private ColliderGeometry() {}
}
