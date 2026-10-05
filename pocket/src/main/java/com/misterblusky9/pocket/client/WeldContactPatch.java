package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WeldContactPatch {
    public static final double SEAM_OOZE = 0.04D;

    public record Edge(double u0, double v0, double u1, double v1) {}
    public static List<Edge> silhouette(final Set<Long> cells, final double ooze) {
        final List<Edge> edges = new ArrayList<>();
        runs(cells, edges, true, -1, ooze);
        runs(cells, edges, true, 1, ooze);
        runs(cells, edges, false, -1, ooze);
        runs(cells, edges, false, 1, ooze);
        return edges;
    }

    private static void runs(
            final Set<Long> cells,
            final List<Edge> edges,
            final boolean horizontal,
            final int dir,
            final double ooze
    ) {
        final Map<Integer, List<Integer>> lines = new HashMap<>();
        for (final long cell : cells) {
            final int u = WeldContact.unpackU(cell);
            final int v = WeldContact.unpackV(cell);
            final long neighbour = horizontal
                    ? WeldContact.packed(u, v + dir)
                    : WeldContact.packed(u + dir, v);
            if (cells.contains(neighbour)) continue;
            lines.computeIfAbsent(horizontal ? v : u, ignored -> new ArrayList<>())
                    .add(horizontal ? u : v);
        }

        for (final Map.Entry<Integer, List<Integer>> line : lines.entrySet()) {
            final List<Integer> along = line.getValue();
            along.sort(Integer::compare);

            int runStart = along.get(0);
            int previous = runStart;
            for (int i = 1; i <= along.size(); i++) {
                if (i < along.size() && along.get(i) == previous + 1) {
                    previous = along.get(i);
                    continue;
                }
                edges.add(edge(cells, horizontal, dir, line.getKey(), runStart, previous, ooze));
                if (i < along.size()) {
                    runStart = along.get(i);
                    previous = runStart;
                }
            }
        }
    }

    private static Edge edge(
            final Set<Long> cells,
            final boolean horizontal,
            final int dir,
            final int fixed,
            final int from,
            final int to,
            final double ooze
    ) {
        final boolean outsideLo = !cells.contains(
                horizontal ? WeldContact.packed(from - 1, fixed) : WeldContact.packed(fixed, from - 1));
        final boolean outsideHi = !cells.contains(
                horizontal ? WeldContact.packed(to + 1, fixed) : WeldContact.packed(fixed, to + 1));
        final double line = fixed + 0.5D * dir + ooze * dir;
        final double lo = from - 0.5D + (outsideLo ? -ooze : ooze);
        final double hi = to + 0.5D + (outsideHi ? ooze : -ooze);

        return horizontal ? new Edge(lo, line, hi, line) : new Edge(line, lo, line, hi);
    }

    public static List<Edge> rectangles(final Set<Long> cells) {
        final List<Edge> rects = new ArrayList<>();
        final Set<Long> taken = new HashSet<>();

        final List<Long> ordered = new ArrayList<>(cells);
        ordered.sort((a, b) -> {
            final int byV = Integer.compare(WeldContact.unpackV(a), WeldContact.unpackV(b));
            return byV != 0 ? byV : Integer.compare(WeldContact.unpackU(a), WeldContact.unpackU(b));
        });

        for (final long start : ordered) {
            if (taken.contains(start)) continue;

            final int u0 = WeldContact.unpackU(start);
            final int v0 = WeldContact.unpackV(start);

            int u1 = u0;
            while (cells.contains(WeldContact.packed(u1 + 1, v0))
                    && !taken.contains(WeldContact.packed(u1 + 1, v0))) {
                u1++;
            }

            int v1 = v0;
            boolean growing = true;
            while (growing) {
                for (int u = u0; u <= u1; u++) {
                    final long probe = WeldContact.packed(u, v1 + 1);
                    if (!cells.contains(probe) || taken.contains(probe)) {
                        growing = false;
                        break;
                    }
                }
                if (growing) v1++;
            }

            for (int v = v0; v <= v1; v++) {
                for (int u = u0; u <= u1; u++) taken.add(WeldContact.packed(u, v));
            }
            rects.add(new Edge(u0 - 0.5D, v0 - 0.5D, u1 + 0.5D, v1 + 0.5D));
        }
        return rects;
    }

    public static void showFace(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final double span,
            final int color
    ) {
        final double half = Math.max(0.0D, span) * 0.5D;
        showFaces(
                key,
                targetPos,
                targetFacing,
                targetAnchor,
                WeldContact.identity(targetFacing),
                List.of(new Edge(-half, -half, half, half)),
                color);
    }

    public static void showGlueFace(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final double span
    ) {
        final double half = Math.max(0.0D, span) * 0.5D;
        WeldFaceRenderer.showGlue(
                key,
                targetPos,
                targetFacing,
                targetAnchor,
                new Edge(-half, -half, half, half));
    }

    public static void showFaces(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final WeldContact.Projection projection,
            final List<Edge> rects,
            final int color
    ) {
        WeldFaceRenderer.show(
                key,
                targetPos,
                targetFacing,
                targetAnchor,
                projection,
                rects,
                color);
    }

    public static void show(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final WeldContact.Projection projection,
            final List<Edge> edges,
            final int color,
            final float lineWidth
    ) {
        final double plane = WeldGeometry.facePlane(targetPos, targetFacing);
        final Direction.Axis targetU = WeldContact.uAxis(targetFacing);
        final Direction.Axis targetV = WeldContact.vAxis(targetFacing);

        final double anchorU = WeldContact.axisOf(targetAnchor, targetU);
        final double anchorV = WeldContact.axisOf(targetAnchor, targetV);

        int index = 0;
        for (final Edge edge : edges) {
            final double[] a = WeldContact.project(edge.u0(), edge.v0(), projection, targetU, targetV);
            final double[] b = WeldContact.project(edge.u1(), edge.v1(), projection, targetU, targetV);

            final Vector3d from = WeldGeometry.inPlane(
                    targetFacing, plane, anchorU + a[0], anchorV + a[1]);
            final Vector3d to = WeldGeometry.inPlane(
                    targetFacing, plane, anchorU + b[0], anchorV + b[1]);

            Outliner.getInstance()
                    .showLine(
                            key + "_" + index++,
                            new Vec3(from.x, from.y, from.z),
                            new Vec3(to.x, to.y, to.z))
                    .colored(color)
                    .lineWidth(lineWidth);
        }
    }

    private WeldContactPatch() {}
}
