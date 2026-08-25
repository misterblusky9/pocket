package com.misterblusky9.pocket.physics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ColliderBudgetTest {
    private static final double SCALE = 0.0625D;

    public static void main(final String[] args) {
        shellDropsOnlyEnclosedCells();
        shellEqualsSolidPrismWhenThereIsNoInterior();
        shellCellArithmeticMatchesTheCellsEmitted();
        shellStaysQuadraticWhereTheSolidPrismIsCubic();
        cellCapBucketsInsteadOfSolidifying();
        cellCapLeavesAffordableCellsAlone();
        fragmentEstimateBoundsTheRealSplit();
        ladderCoarsensAndOrders();
        ladderStaysBlockAccurate();
        theFloorOnlyEverRises();
        System.out.println("ColliderBudgetTest: PASS");
    }

    private static void shellDropsOnlyEnclosedCells() {
        final List<ColliderGeometry.CellBox> solid =
                ColliderGeometry.prism(0.0D, 0.0D, 0.0D, 7.0D, 5.0D, 6.0D, SCALE);
        final List<ColliderGeometry.CellBox> shell =
                ColliderGeometry.prismShell(0.0D, 0.0D, 0.0D, 7.0D, 5.0D, 6.0D, SCALE);

        final Set<String> kept = new HashSet<>();
        for (final ColliderGeometry.CellBox box : shell) kept.add(cell(box));

        check(shell.size() < solid.size(), "the shell must actually drop something");
        for (final ColliderGeometry.CellBox box : solid) {
            if (kept.contains(cell(box))) continue;
            final boolean interior = box.cellX() > 0 && box.cellX() < 6
                    && box.cellY() > 0 && box.cellY() < 4
                    && box.cellZ() > 0 && box.cellZ() < 5;
            check(interior, "dropped a reachable cell at " + cell(box));
        }

        final Set<String> present = new HashSet<>();
        for (final ColliderGeometry.CellBox box : solid) present.add(cell(box));
        for (final String key : kept) check(present.contains(key), "shell invented cell " + key);
    }

    private static void shellEqualsSolidPrismWhenThereIsNoInterior() {
        for (final double thinY : new double[] { 1.0D, 2.0D }) {
            final List<ColliderGeometry.CellBox> solid =
                    ColliderGeometry.prism(0.0D, 0.0D, 0.0D, 9.0D, thinY, 9.0D, SCALE);
            final List<ColliderGeometry.CellBox> shell =
                    ColliderGeometry.prismShell(0.0D, 0.0D, 0.0D, 9.0D, thinY, 9.0D, SCALE);
            check(solid.size() == shell.size(),
                    "a " + thinY + "-cell-thick prism is all surface: " + solid.size() + " vs " + shell.size());
        }
    }

    private static void shellCellArithmeticMatchesTheCellsEmitted() {
        final int[][] cases = {
                { 3, 3, 3 }, { 1, 1, 1 }, { 8, 2, 5 }, { 10, 10, 10 }, { 16, 4, 1 },
        };
        for (final int[] size : cases) {
            final List<ColliderGeometry.CellBox> shell = ColliderGeometry.prismShell(
                    0.0D, 0.0D, 0.0D, size[0], size[1], size[2], SCALE);
            final long predicted = ColliderGeometry.shellCells(
                    0, 0, 0, size[0] - 1, size[1] - 1, size[2] - 1);
            check(predicted == shell.size(),
                    "shellCells said " + predicted + " for " + size[0] + "x" + size[1] + "x" + size[2]
                            + " but " + shell.size() + " cells were emitted");
        }
    }

    private static void shellStaysQuadraticWhereTheSolidPrismIsCubic() {
        final long solid = 100L * 100L * 100L;
        final long shell = ColliderGeometry.shellCells(0, 0, 0, 99, 99, 99);
        check(shell == solid - 98L * 98L * 98L, "unexpected shell count " + shell);
        check(shell < 60_000L, "a 100-cube shell should be around 58,808 cells, got " + shell);
        check(solid > 262_144L && shell < 262_144L,
                "the old volume budget rejected this craft and the surface budget must accept it");
    }

    private static void cellCapBucketsInsteadOfSolidifying() {
        final List<ColliderShapeKey.Face> faces = new ArrayList<>();
        faces.add(ColliderShapeKey.face(SCALE, 0.0D, 0.0D, 0.0D, 0.1D, 0.1D, 0.1D));
        faces.add(ColliderShapeKey.face(SCALE, 0.9D, 0.9D, 0.9D, 1.0D, 1.0D, 1.0D));
        for (int i = 0; i < 600; i++) {
            final double at = 0.4D + (i % 8) * 0.01D;
            faces.add(ColliderShapeKey.face(SCALE, at, at, at, at + 0.02D, at + 0.02D, at + 0.02D));
        }

        final List<ColliderShapeKey.Face> capped =
                ColliderGeometry.capCell(faces, SCALE, ColliderDetail.MAX_CELL_BOXES);

        check(capped.size() <= ColliderDetail.MAX_CELL_BOXES,
                "the cap must bound the compound, got " + capped.size());
        check(capped.size() > 1,
                "the cap must not solidify the cell into one box, got " + capped.size());

        for (final ColliderShapeKey.Face face : capped) {
            final double spanX = face.maxXd() - face.minXd();
            final double spanY = face.maxYd() - face.minYd();
            final double spanZ = face.maxZd() - face.minZd();
            check(spanX < 0.9D || spanY < 0.9D || spanZ < 0.9D,
                    "a capped box spans the whole cell - this is the solid-cube bug");
        }

        final double bucket = 1.0D / 8.0D;
        for (final ColliderShapeKey.Face face : capped) {
            check(face.maxXd() - face.minXd() <= bucket + 1.0E-6D
                            && face.maxYd() - face.minYd() <= bucket + 1.0E-6D
                            && face.maxZd() - face.minZd() <= bucket + 1.0E-6D,
                    "a capped box is wider than its sub-cell");
        }
    }

    private static void cellCapLeavesAffordableCellsAlone() {
        final List<ColliderShapeKey.Face> faces = List.of(
                ColliderShapeKey.face(SCALE, 0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 0.5D),
                ColliderShapeKey.face(SCALE, 0.5D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D));
        check(ColliderGeometry.capCell(faces, SCALE, ColliderDetail.MAX_CELL_BOXES) == faces,
                "a cell under the cap must be handed back untouched");
    }

    private static void fragmentEstimateBoundsTheRealSplit() {
        final List<PlotShape.Box> boxes = List.of(
                new PlotShape.Box(0.0D, 0.0D, 0.0D, 32.0D, 4.0D, 32.0D),
                new PlotShape.Box(5.0D, 4.0D, 5.0D, 6.0D, 20.0D, 6.0D),
                new PlotShape.Box(1.5D, 0.5D, 1.5D, 2.5D, 1.5D, 2.5D));

        for (final double scale : new double[] { 1.0D, 0.5D, 0.25D, 0.125D, 0.0625D }) {
            final long estimate = ColliderDetail.estimateFragments(boxes, scale);
            long actual = 0L;
            for (final PlotShape.Box box : boxes) {
                actual += crossed(box.maxX() - box.minX(), scale)
                        * crossed(box.maxY() - box.minY(), scale)
                        * crossed(box.maxZ() - box.minZ(), scale);
            }
            check(estimate >= actual,
                    "estimate " + estimate + " under-counted " + actual + " at scale " + scale);
        }

        check(ColliderDetail.estimateFragments(boxes, 0.0625D)
                        <= ColliderDetail.estimateFragments(boxes, 1.0D),
                "a smaller craft must not be estimated as more fragmented");
    }

    private static void ladderCoarsensAndOrders() {
        final ColliderDetail.Level exact = ColliderDetail.FINEST;
        final ColliderDetail.Level blocks = ColliderDetail.coarsen(exact);

        check(exact.exact(), "the finest rung keeps sub-block shapes");
        check(!blocks.exact() && blocks.quantum() == 1,
                "the first coarsening drops sub-block shapes before it drops resolution");
        check(!ColliderDetail.atLeastAsCoarse(exact, blocks),
                "EXACT/1 and BLOCKS/1 share a quantum and must not be treated as interchangeable");
        check(ColliderDetail.atLeastAsCoarse(blocks, exact), "BLOCKS/1 is coarser than EXACT/1");

        ColliderDetail.Level walk = exact;
        int steps = 0;
        while (ColliderDetail.canCoarsen(walk)) {
            final ColliderDetail.Level next = ColliderDetail.coarsen(walk);
            check(ColliderDetail.atLeastAsCoarse(next, walk), "the ladder must not go finer");
            walk = next;
            steps++;
            check(steps < 32, "the ladder must terminate");
        }
        check(ColliderDetail.coarsen(walk).equals(walk), "the top rung is a fixed point");

        check(new ColliderDetail.Level(ColliderDetail.Fidelity.BLOCKS, 4).worldError(0.0625D) == 0.25D,
                "four blocks at 1/16 is a quarter of a world block");
    }

    private static void ladderStaysBlockAccurate() {
        ColliderDetail.Level walk = ColliderDetail.FINEST;
        while (ColliderDetail.canCoarsen(walk)) {
            walk = ColliderDetail.coarsen(walk);
            check(walk.quantum() == 1,
                    "the default ladder must never group blocks, but reached " + walk);
        }
        check(walk.equals(new ColliderDetail.Level(ColliderDetail.Fidelity.BLOCKS, 1)),
                "the coarsest default rung is full-cube blocks, got " + walk);
        check(walk.worldError(1.0D) == 1.0D,
                "a full-cube rung is accurate to the block bounds at any scale");
    }

    private static void theFloorOnlyEverRises() {
        final UUID craft = UUID.nameUUIDFromBytes("collider-budget-test".getBytes());
        ColliderDetail.forget(craft);
        try {
            check(ColliderDetail.start(craft, null).equals(ColliderDetail.FINEST),
                    "an unmeasured craft starts at the finest rung");

            check(!ColliderDetail.recordRebuild(craft, ColliderDetail.FINEST, 2_000, 1L),
                    "a fast rebuild must not cost the craft any detail");
            check(ColliderDetail.start(craft, null).equals(ColliderDetail.FINEST),
                    "still finest after a fast rebuild");

            check(!ColliderDetail.recordRebuild(
                            craft, ColliderDetail.FINEST, 2_000, ColliderDetail.SLOW_REBUILD_MS + 4L),
                    "a single slow rebuild must not permanently degrade a craft");

            for (int i = 0; i < 10; i++) {
                check(!ColliderDetail.recordRebuild(craft, ColliderDetail.FINEST, 4, 500L),
                        "a 4-box craft must never lose its sub-block shapes to wall time");
            }
            check(ColliderDetail.start(craft, null).equals(ColliderDetail.FINEST),
                    "a trivial craft keeps exact collision no matter what the clock said");

            boolean raisedNow = false;
            for (int i = 0; i < ColliderDetail.SLOW_REBUILDS_TO_DEGRADE; i++) {
                raisedNow = ColliderDetail.recordRebuild(
                        craft, ColliderDetail.FINEST, 2_000, ColliderDetail.SLOW_REBUILD_MS + 4L);
            }
            check(raisedNow, "sustained slow rebuilds on a complex craft must raise the floor");
            final ColliderDetail.Level raised = ColliderDetail.start(craft, null);
            check(ColliderDetail.atLeastAsCoarse(raised, ColliderDetail.coarsen(ColliderDetail.FINEST)),
                    "the floor should have moved at least one rung, got " + raised);

            check(!ColliderDetail.recordRebuild(craft, ColliderDetail.FINEST, 2_000, 99L),
                    "the floor must not be raised twice for the same measurement");

            for (int settle = 0; settle < 64; settle++) {
                ColliderDetail.recordRebuild(craft, raised, 12, 1L);
                ColliderDetail.recordRebuild(craft, raised, 8, ColliderDetail.SLOW_REBUILD_MS + 1L);
                check(ColliderDetail.atLeastAsCoarse(ColliderDetail.start(craft, null), raised),
                        "the floor fell back to " + ColliderDetail.start(craft, null)
                                + " on settle " + settle + "; this is the loop that froze the server");
            }
        } finally {
            ColliderDetail.forget(craft);
        }
    }

    private static long crossed(final double length, final double scale) {
        return (long) Math.floor(length * scale) + 1L;
    }

    private static boolean same(final double left, final double right) {
        return Math.abs(left - right) < 1.0E-9D;
    }

    private static String cell(final ColliderGeometry.CellBox box) {
        return box.cellX() + "," + box.cellY() + "," + box.cellZ();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ColliderBudgetTest() {}
}
