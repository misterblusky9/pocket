package com.misterblusky9.pocket.block;

import java.util.ArrayList;
import java.util.List;

public final class FacadeTiling {
    public static final int MAX_TILES_PER_AXIS = 64;
    private static final double EPSILON = 1.0E-5D;

    public record Piece(double[] a, double[] b, double[] u, double[] v) {}

    public static double[] impliedRect(final double[] a, final double[] b, final double[] u, final double[] v) {
        final double[] uMap = fit(a, b, u);
        final double[] vMap = fit(a, b, v);
        if (uMap == null || vMap == null) return null;
        double uMin = Double.POSITIVE_INFINITY, uMax = Double.NEGATIVE_INFINITY;
        double vMin = Double.POSITIVE_INFINITY, vMax = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            final double ca = corner & 1, cb = corner >> 1;
            final double cu = eval(uMap, ca, cb), cv = eval(vMap, ca, cb);
            uMin = Math.min(uMin, cu); uMax = Math.max(uMax, cu);
            vMin = Math.min(vMin, cv); vMax = Math.max(vMax, cv);
        }
        if (uMax - uMin < EPSILON * EPSILON || vMax - vMin < EPSILON * EPSILON) return null;
        return new double[] {uMin, uMax, vMin, vMax};
    }

    public static int[] tileRange(final int block, final double scale) {
        return new int[] {
                (int) Math.floor(block * scale + EPSILON),
                (int) Math.ceil((block + 1) * scale - EPSILON) - 1
        };
    }

    public static int tileOf(final int block, final double local, final double scale) {
        return (int) Math.floor((block + local) * scale);
    }

    public static int[] blocksAcross(final int tile, final double scale) {
        return new int[] {
                (int) Math.floor(tile / scale - EPSILON),
                (int) Math.floor((tile + 1) / scale + EPSILON)
        };
    }

    public static int blocksPerTile(final double scale) {
        return (int) Math.round(1.0D / scale);
    }

    public static int[] alignment(final int[] block, final double scale) {
        if (!(scale > 0.0D) || scale >= 1.0D) return null;
        final int span = blocksPerTile(scale);
        final int[] offset = new int[3];
        for (int axis = 0; axis < 3; axis++) offset[axis] = Math.floorMod(-block[axis], span);
        return offset;
    }

    public static int[] reduce(final int[] offset, final double scale) {
        final int[] reduced = new int[3];
        if (offset == null || offset.length != 3 || !(scale > 0.0D) || scale >= 1.0D) return reduced;
        final int span = blocksPerTile(scale);
        for (int axis = 0; axis < 3; axis++) reduced[axis] = Math.floorMod(offset[axis], span);
        return reduced;
    }

    public static List<Piece> retile(
            final double[] a,
            final double[] b,
            final double[] u,
            final double[] v,
            final int anchorA,
            final int anchorB,
            final double scale
    ) {
        if (!(scale > 0.0D) || !Double.isFinite(scale)) return null;
        for (int i = 0; i < 4; i++) {
            if (u[i] < -EPSILON || u[i] > 1.0D + EPSILON || v[i] < -EPSILON || v[i] > 1.0D + EPSILON) return null;
        }

        final double[] uMap = fit(a, b, u);
        final double[] vMap = fit(a, b, v);
        if (uMap == null || vMap == null) return null;

        final boolean straight = Math.abs(uMap[1]) < EPSILON && Math.abs(vMap[0]) < EPSILON;
        final boolean turned = Math.abs(uMap[0]) < EPSILON && Math.abs(vMap[1]) < EPSILON;
        if (!straight && !turned) return null;

        final double aMin = min(a), aMax = max(a), bMin = min(b), bMax = max(b);
        if (aMax - aMin < EPSILON || bMax - bMin < EPSILON) return null;

        final double qa0 = (anchorA + aMin) * scale, qa1 = (anchorA + aMax) * scale;
        final double qb0 = (anchorB + bMin) * scale, qb1 = (anchorB + bMax) * scale;

        final double[] alongA = straight ? uMap : vMap;
        final double[] alongB = straight ? vMap : uMap;
        final double[] cutsA = cuts(qa0, qa1, alongA[0], alongA[2]);
        final double[] cutsB = cuts(qb0, qb1, alongB[1], alongB[2]);
        if (cutsA == null || cutsB == null) return null;

        final boolean clockwise = signedArea(a, b) < 0.0D;
        final int startCorner = corner(a[0], b[0], aMin, aMax, bMin, bMax);
        final List<Piece> pieces = new ArrayList<>((cutsA.length - 1) * (cutsB.length - 1));
        for (int i = 0; i + 1 < cutsA.length; i++) {
            for (int j = 0; j + 1 < cutsB.length; j++) {
                pieces.add(piece(cutsA[i], cutsA[i + 1], cutsB[j], cutsB[j + 1],
                        anchorA, anchorB, scale, uMap, vMap, clockwise, startCorner));
            }
        }
        return pieces;
    }

    private static Piece piece(
            final double qa0, final double qa1, final double qb0, final double qb1,
            final int anchorA, final int anchorB, final double scale,
            final double[] uMap, final double[] vMap,
            final boolean clockwise, final int startCorner
    ) {
        final double midA = (qa0 + qa1) * 0.5D, midB = (qb0 + qb1) * 0.5D;
        final double uFloor = Math.floor(eval(uMap, midA, midB));
        final double vFloor = Math.floor(eval(vMap, midA, midB));

        final double[][] corners = {{qa0, qb0}, {qa1, qb0}, {qa1, qb1}, {qa0, qb1}};
        final double[] a = new double[4], b = new double[4], u = new double[4], v = new double[4];
        for (int k = 0; k < 4; k++) {
            final int index = clockwise ? Math.floorMod(startCorner - k, 4) : Math.floorMod(startCorner + k, 4);
            final double qa = corners[index][0], qb = corners[index][1];
            a[k] = qa / scale - anchorA;
            b[k] = qb / scale - anchorB;
            u[k] = clamp(eval(uMap, qa, qb) - uFloor);
            v[k] = clamp(eval(vMap, qa, qb) - vFloor);
        }
        return new Piece(a, b, u, v);
    }

    private static double[] cuts(final double from, final double to, final double slope, final double offset) {
        if (Math.abs(slope) < EPSILON) return new double[] {from, to};
        final double start = slope * from + offset, end = slope * to + offset;
        final double low = Math.min(start, end), high = Math.max(start, end);
        final int first = (int) Math.floor(low + EPSILON) + 1;
        final int last = (int) Math.ceil(high - EPSILON) - 1;
        if (last - first + 2 > MAX_TILES_PER_AXIS) return null;

        final List<Double> points = new ArrayList<>();
        points.add(from);
        for (int k = first; k <= last; k++) {
            final double q = (k - offset) / slope;
            if (q > from + EPSILON && q < to - EPSILON) points.add(q);
        }
        points.add(to);
        points.sort(Double::compare);
        final double[] result = new double[points.size()];
        for (int i = 0; i < result.length; i++) result[i] = points.get(i);
        return result;
    }

    private static double[] fit(final double[] a, final double[] b, final double[] value) {
        for (int skip = 3; skip >= 0; skip--) {
            final int i0 = skip == 0 ? 1 : 0;
            final int i1 = skip <= 1 ? 2 : 1;
            final int i2 = skip <= 2 ? 3 : 2;
            final double da1 = a[i1] - a[i0], db1 = b[i1] - b[i0];
            final double da2 = a[i2] - a[i0], db2 = b[i2] - b[i0];
            final double det = da1 * db2 - da2 * db1;
            if (Math.abs(det) < EPSILON) continue;
            final double dv1 = value[i1] - value[i0], dv2 = value[i2] - value[i0];
            final double ka = (dv1 * db2 - dv2 * db1) / det;
            final double kb = (da1 * dv2 - da2 * dv1) / det;
            return new double[] {ka, kb, value[i0] - ka * a[i0] - kb * b[i0]};
        }
        return null;
    }

    private static double eval(final double[] map, final double a, final double b) {
        return map[0] * a + map[1] * b + map[2];
    }

    private static double signedArea(final double[] a, final double[] b) {
        double area = 0.0D;
        for (int i = 0; i < 4; i++) {
            final int j = (i + 1) & 3;
            area += a[i] * b[j] - a[j] * b[i];
        }
        return area;
    }

    private static int corner(final double a, final double b,
                              final double aMin, final double aMax, final double bMin, final double bMax) {
        final boolean highA = Math.abs(a - aMax) < Math.abs(a - aMin);
        final boolean highB = Math.abs(b - bMax) < Math.abs(b - bMin);
        if (!highA && !highB) return 0;
        if (highA && !highB) return 1;
        if (highA) return 2;
        return 3;
    }

    private static double clamp(final double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double min(final double[] values) {
        return Math.min(Math.min(values[0], values[1]), Math.min(values[2], values[3]));
    }

    private static double max(final double[] values) {
        return Math.max(Math.max(values[0], values[1]), Math.max(values[2], values[3]));
    }

    private FacadeTiling() {}
}
