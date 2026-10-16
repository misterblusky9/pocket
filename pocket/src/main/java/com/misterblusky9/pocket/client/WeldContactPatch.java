package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.LineEmitter;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
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
    public static final float LINE_WIDTH = 1.0F / 16.0F;

    public static float lineWidth(SubLevel drawnOn, SubLevel other) {
        double own = scaleOf(drawnOn);
        return (float) (LINE_WIDTH * Math.min(own, scaleOf(other)) / own);
    }

    private static double scaleOf(SubLevel subLevel) {
        return subLevel == null ? ScaleBounds.FULL : ScaleBounds.clampValid(Pym.scale().of(subLevel));
    }

    public record Edge(double u0, double v0, double u1, double v1) {}
    public static List<Edge> silhouette(Set<Long> cells, double ooze) {
        List<Edge> edges = new ArrayList<>();
        runs(cells, edges, true, -1, ooze);
        runs(cells, edges, true, 1, ooze);
        runs(cells, edges, false, -1, ooze);
        runs(cells, edges, false, 1, ooze);
        return edges;
    }

    private static void runs(
            Set<Long> cells,
            List<Edge> edges,
            boolean horizontal,
            int dir,
            double ooze
    ) {
        Map<Integer, List<Integer>> lines = new HashMap<>();
        for (long cell : cells) {
            int u = WeldContact.unpackU(cell);
            int v = WeldContact.unpackV(cell);
            long neighbour = horizontal
                    ? WeldContact.packed(u, v + dir)
                    : WeldContact.packed(u + dir, v);
            if (cells.contains(neighbour)) continue;
            lines.computeIfAbsent(horizontal ? v : u, ignored -> new ArrayList<>())
                    .add(horizontal ? u : v);
        }

        for (Map.Entry<Integer, List<Integer>> line : lines.entrySet()) {
            List<Integer> along = line.getValue();
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
            Set<Long> cells,
            boolean horizontal,
            int dir,
            int fixed,
            int from,
            int to,
            double ooze
    ) {
        boolean outsideLo = !cells.contains(
                horizontal ? WeldContact.packed(from - 1, fixed) : WeldContact.packed(fixed, from - 1));
        boolean outsideHi = !cells.contains(
                horizontal ? WeldContact.packed(to + 1, fixed) : WeldContact.packed(fixed, to + 1));
        double line = fixed + 0.5D * dir + ooze * dir;
        double lo = from - 0.5D + (outsideLo ? -ooze : ooze);
        double hi = to + 0.5D + (outsideHi ? ooze : -ooze);

        return horizontal ? new Edge(lo, line, hi, line) : new Edge(line, lo, line, hi);
    }

    public static List<Edge> rectangles(Set<Long> cells) {
        List<Edge> rects = new ArrayList<>();
        Set<Long> taken = new HashSet<>();

        List<Long> ordered = new ArrayList<>(cells);
        ordered.sort((a, b) -> {
            int byV = Integer.compare(WeldContact.unpackV(a), WeldContact.unpackV(b));
            return byV != 0 ? byV : Integer.compare(WeldContact.unpackU(a), WeldContact.unpackU(b));
        });

        for (long start : ordered) {
            if (taken.contains(start)) continue;

            int u0 = WeldContact.unpackU(start);
            int v0 = WeldContact.unpackV(start);

            int u1 = u0;
            while (cells.contains(WeldContact.packed(u1 + 1, v0))
                    && !taken.contains(WeldContact.packed(u1 + 1, v0))) {
                u1++;
            }

            int v1 = v0;
            boolean growing = true;
            while (growing) {
                for (int u = u0; u <= u1; u++) {
                    long probe = WeldContact.packed(u, v1 + 1);
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
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            double span,
            int color
    ) {
        double half = Math.max(0.0D, span) * 0.5D;
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
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            double span
    ) {
        double half = Math.max(0.0D, span) * 0.5D;
        WeldFaceRenderer.showGlue(
                key,
                targetPos,
                targetFacing,
                targetAnchor,
                new Edge(-half, -half, half, half));
    }

    public static void showFaces(
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContact.Projection projection,
            List<Edge> rects,
            int color
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
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContact.Projection projection,
            List<Edge> edges,
            int color,
            float lineWidth
    ) {
        double plane = WeldGeometry.facePlane(targetPos, targetFacing);
        Direction.Axis targetU = WeldContact.uAxis(targetFacing);
        Direction.Axis targetV = WeldContact.vAxis(targetFacing);

        double anchorU = WeldContact.axisOf(targetAnchor, targetU);
        double anchorV = WeldContact.axisOf(targetAnchor, targetV);

        Minecraft minecraft = Minecraft.getInstance();
        SubLevel containing = minecraft.level == null
                ? null : Sable.HELPER.getContaining(minecraft.level, targetPos);
        ClientSubLevel owner = containing instanceof ClientSubLevel client && !client.isRemoved()
                ? client : null;

        int index = 0;
        for (Edge edge : edges) {
            double[] a = WeldContact.project(edge.u0(), edge.v0(), projection, targetU, targetV);
            double[] b = WeldContact.project(edge.u1(), edge.v1(), projection, targetU, targetV);

            Vector3d from = WeldGeometry.inPlane(
                    targetFacing, plane, anchorU + a[0], anchorV + a[1]);
            Vector3d to = WeldGeometry.inPlane(
                    targetFacing, plane, anchorU + b[0], anchorV + b[1]);

            LineEmitter.weldLine(
                    Outliner.getInstance()
                            .showLine(
                                    key + "_" + index++,
                                    new Vec3(from.x, from.y, from.z),
                                    new Vec3(to.x, to.y, to.z))
                            .colored(color)
                            .lineWidth(lineWidth),
                    owner);
        }
    }

    private WeldContactPatch() {}
}
