package com.misterblusky9.pocket.compat;

import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry.SnapMode;
import com.misterblusky9.pocket.scale.CompressionStage;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class WeldSnapTest {
    private static final double TOLERANCE = 1.0E-9D;

    private static final Vector3d DOWN = new Vector3d(0.0D, -1.0D, 0.0D);
    private static final Vector3d UP = new Vector3d(0.0D, 1.0D, 0.0D);
    private static final Vector3d NORTH = new Vector3d(0.0D, 0.0D, -1.0D);
    private static final Vector3d SOUTH = new Vector3d(0.0D, 0.0D, 1.0D);
    private static final Vector3d WEST = new Vector3d(-1.0D, 0.0D, 0.0D);
    private static final Vector3d EAST = new Vector3d(1.0D, 0.0D, 0.0D);
    private static final Vector3d[] FACES = {DOWN, UP, NORTH, SOUTH, WEST, EAST};

    public static void main(final String[] args) {
        everyStagePairHasAWholeDivisor();
        spanIsTheJointFootprint();
        freeSnapStaysOnTheFacePlane();
        smartSnapLandsOnTheGrid();
        everyCellOfTheGridIsReachable();
        theEdgeCellsCoverTheSeam();
        magnetKeepsTheFaceCentre();
        everyFacePairMeetsFlush();
        everyFacePairLandsOnTheGrid();
        alignmentAgreesWithUpstreamOnItsOwnDomain();
        tumbledCraftIsPulledStraight();
        System.out.println("WeldSnapTest: PASS");
    }

    private static void everyStagePairHasAWholeDivisor() {
        for (final CompressionStage small : CompressionStage.values()) {
            for (final CompressionStage big : CompressionStage.values()) {
                final int divisor = WeldGeometry.divisor(small, big);
                if (small.depth() <= big.depth()) {
                    check(divisor == 0, "a weld needs a strictly smaller first side: "
                            + small + " -> " + big);
                    continue;
                }
                final int expected = 1 << (small.depth() - big.depth());
                check(divisor == expected,
                        small + " welded to " + big + " should divide the face " + expected
                                + " ways, got " + divisor);
            }
        }
    }

    private static void spanIsTheJointFootprint() {
        check(near(WeldGeometry.span(CompressionStage.NORMAL, CompressionStage.EIGHTH), 0.125D),
                "the big side carries a footprint of one small block");
        check(near(WeldGeometry.span(CompressionStage.EIGHTH, CompressionStage.NORMAL), 1.0D),
                "the small side carries its whole face, never more");
        check(near(WeldGeometry.span(CompressionStage.QUARTER, CompressionStage.QUARTER), 1.0D),
                "equal scales are a whole face");
        check(near(WeldGeometry.span(null, CompressionStage.NORMAL), 1.0D),
                "a missing stage falls back to a whole face");
    }

    private static void freeSnapStaysOnTheFacePlane() {
        for (final int divisor : new int[] {2, 4, 8, 16}) {
            for (final double raw : new double[] {-5.0D, 0.0D, 0.001D, 0.5D, 0.999D, 1.0D, 7.0D}) {
                final double snapped = WeldGeometry.snapAxis(raw, divisor, SnapMode.FREE);
                check(snapped >= -TOLERANCE && snapped <= 1.0D + TOLERANCE,
                        "a free-placed joint at " + raw + " must stay on the face plane");
            }
            check(near(WeldGeometry.snapAxis(0.31D, divisor, SnapMode.FREE), 0.31D),
                    "free placement must not move");

            // the whole point of free placement: the patch may hang over the seam
            final double half = 0.5D / divisor;
            check(near(WeldGeometry.snapAxis(0.0D, divisor, SnapMode.FREE), 0.0D),
                    "free placement must reach the seam itself, overhang and all");
            check(WeldGeometry.snapAxis(0.0D, divisor, SnapMode.FREE) < half - TOLERANCE,
                    "free placement must not be pushed back inside the face at 1/" + divisor);
        }
    }

    private static void everyCellOfTheGridIsReachable() {
        for (final int divisor : new int[] {2, 4, 8, 16}) {
            final double span = 1.0D / divisor;
            for (int cell = 0; cell < divisor; cell++) {
                final double centre = (cell + 0.5D) * span;
                final double snapped = WeldGeometry.snapAxis(centre, divisor, SnapMode.SMART);
                check(near(snapped, centre) || near(snapped, 0.5D),
                        "cell " + cell + " of " + divisor + " must be reachable, got " + snapped);
            }

            final java.util.Set<Long> reached = new java.util.HashSet<>();
            for (double raw = 0.0D; raw <= 1.0D; raw += 0.0005D) {
                reached.add(Math.round(WeldGeometry.snapAxis(raw, divisor, SnapMode.SMART) * 1.0E6D));
            }
            check(reached.size() >= divisor,
                    "1/" + divisor + " should expose at least " + divisor
                            + " positions per axis, got " + reached.size());
        }
    }

    private static void smartSnapLandsOnTheGrid() {
        final int divisor = 8;
        final double span = 1.0D / divisor;
        for (double raw = 0.0D; raw <= 1.0D; raw += 0.013D) {
            final double snapped = WeldGeometry.snapAxis(raw, divisor, SnapMode.SMART);
            final double cells = snapped / span - 0.5D;
            final boolean onGrid = near(cells, Math.round(cells));
            check(onGrid || near(snapped, 0.5D),
                    "smart snapping must land on a cell centre or the face centre, got " + snapped);
        }
    }

    private static void theEdgeCellsCoverTheSeam() {
        final int divisor = 8;
        final double span = 1.0D / divisor;
        check(near(WeldGeometry.snapAxis(0.0D, divisor, SnapMode.SMART), span * 0.5D),
                "a hit on the seam takes the edge cell, never off the face");
        check(near(WeldGeometry.snapAxis(1.0D, divisor, SnapMode.SMART), 1.0D - span * 0.5D),
                "the far seam takes the far edge cell");
        check(near(WeldGeometry.snapAxis(0.10D, divisor, SnapMode.SMART), span * 0.5D),
                "a hit inside the first cell stays in the first cell");
        check(near(WeldGeometry.snapAxis(0.20D, divisor, SnapMode.SMART), span * 1.5D),
                "a hit inside the second cell must reach the second cell, not flush to the edge");
    }

    private static void magnetKeepsTheFaceCentre() {
        for (final int divisor : new int[] {2, 4, 8, 16}) {
            final double half = 0.5D / divisor;
            final java.util.Set<Long> reached = new java.util.HashSet<>();
            for (double raw = 0.0D; raw <= 1.0D; raw += 0.0005D) {
                reached.add(Math.round(WeldGeometry.snapAxis(raw, divisor, SnapMode.MAGNET) * 1.0E6D));
            }
            check(reached.size() <= 3,
                    "magnet snapping must stay coarse at 1/" + divisor + ", got " + reached.size());
            check(near(WeldGeometry.snapAxis(0.0D, divisor, SnapMode.MAGNET), half),
                    "the near edge must flush inside the face");
            check(near(WeldGeometry.snapAxis(0.5D, divisor, SnapMode.MAGNET), 0.5D),
                    "the face centre must remain a magnet target");
            check(near(WeldGeometry.snapAxis(1.0D, divisor, SnapMode.MAGNET), 1.0D - half),
                    "the far edge must flush inside the face");
        }
    }

    private static void everyFacePairMeetsFlush() {
        for (final Vector3d own : FACES) {
            for (final Vector3d peer : FACES) {
                for (final Quaterniond start : poses()) {
                    final Quaterniond end = WeldGeometry.alignment(start, own, peer);
                    final Vector3d settled = end.transform(new Vector3d(peer));
                    final Vector3d seam = new Vector3d(own).negate();
                    check(near(settled.x, seam.x) && near(settled.y, seam.y) && near(settled.z, seam.z),
                            own + " welded to " + peer + " must end up face to face, got " + settled);
                }
            }
        }
    }

    private static void everyFacePairLandsOnTheGrid() {
        for (final Vector3d own : FACES) {
            for (final Vector3d peer : FACES) {
                for (final Quaterniond start : poses()) {
                    final Quaterniond end = WeldGeometry.alignment(start, own, peer);
                    for (final Vector3d axis : FACES) {
                        final Vector3d mapped = end.transform(new Vector3d(axis));
                        check(isUnitAxis(mapped),
                                own + " welded to " + peer + " must leave the grids square, "
                                        + axis + " became " + mapped);
                    }
                }
            }
        }
    }

    private static void alignmentAgreesWithUpstreamOnItsOwnDomain() {
        final Quaterniond identity = new Quaterniond();
        check(isIdentity(WeldGeometry.alignment(identity, NORTH, SOUTH)),
                "opposing horizontal faces already line up and must not be rotated");
        check(isIdentity(WeldGeometry.alignment(identity, UP, DOWN)),
                "opposing vertical faces already line up and must not be rotated");

        final Vector3d flipped = WeldGeometry.alignment(identity, NORTH, NORTH)
                .transform(new Vector3d(NORTH));
        check(near(flipped.z, 1.0D), "two faces on the same axis must turn about to meet");
    }

    private static void tumbledCraftIsPulledStraight() {
        final Quaterniond tumbled = new Quaterniond()
                .rotateY(Math.toRadians(37.0D))
                .rotateX(Math.toRadians(11.0D));
        final Quaterniond end = WeldGeometry.alignment(tumbled, EAST, UP);
        for (final Vector3d axis : FACES) {
            check(isUnitAxis(end.transform(new Vector3d(axis))),
                    "a tumbled craft must still settle square, " + axis + " did not");
        }
    }

    private static Quaterniond[] poses() {
        return new Quaterniond[] {
                new Quaterniond(),
                new Quaterniond().rotateY(Math.toRadians(90.0D)),
                new Quaterniond().rotateY(Math.toRadians(180.0D)),
                new Quaterniond().rotateX(Math.toRadians(90.0D)),
                new Quaterniond().rotateZ(Math.toRadians(-90.0D)),
                new Quaterniond().rotateY(Math.toRadians(43.0D)).rotateZ(Math.toRadians(17.0D))
        };
    }

    private static boolean isUnitAxis(final Vector3d vector) {
        int axes = 0;
        for (final double component : new double[] {vector.x, vector.y, vector.z}) {
            if (near(Math.abs(component), 1.0D)) axes++;
            else if (!near(component, 0.0D)) return false;
        }
        return axes == 1;
    }

    private static boolean isIdentity(final Quaterniond quaternion) {
        for (final Vector3d axis : FACES) {
            final Vector3d mapped = quaternion.transform(new Vector3d(axis));
            if (!near(mapped.x, axis.x) || !near(mapped.y, axis.y) || !near(mapped.z, axis.z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean near(final double a, final double b) {
        return Math.abs(a - b) <= 1.0E-6D;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private WeldSnapTest() {}
}
