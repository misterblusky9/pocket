package com.misterblusky9.pocket.explosion;

public final class BlastReachTest {
    private static final double[] SCALES = { 1.0D, 0.25D, 0.0625D };

    private static final double SHIP_BLOCKS = 16.0D;

    public static void main(final String[] args) {
        aBlastTouchingTheHullIsMirrored();
        aBlastBeyondAirReachIsSkipped();
        aBlastInsideTheBoundsIsMirrored();
        degenerateInputsAreSkipped();
        System.out.println("BlastReachTest: PASS (" + SCALES.length + " compression scales)");
    }

    private static void aBlastTouchingTheHullIsMirrored() {
        for (final double scale : SCALES) {
            final double half = SHIP_BLOCKS * scale * 0.5D;
            final double radius = 4.0D;

            check(reaches(half + radius, 0.0D, 0.0D, radius, half),
                    "a blast one radius off the hull was skipped at scale " + scale);
            check(reaches(half + radius * BlastReach.AIR_REACH_FACTOR * 0.99D, 0.0D, 0.0D, radius, half),
                    "a blast just inside air reach was skipped at scale " + scale);
        }
    }

    private static void aBlastBeyondAirReachIsSkipped() {
        for (final double scale : SCALES) {
            final double half = SHIP_BLOCKS * scale * 0.5D;
            final double radius = 4.0D;
            final double beyond = half + radius * BlastReach.AIR_REACH_FACTOR * 1.01D;

            check(!reaches(beyond, 0.0D, 0.0D, radius, half),
                    "a blast beyond air reach was mirrored at scale " + scale);
            check(!reaches(beyond, beyond, beyond, radius, half),
                    "a diagonal blast beyond air reach was mirrored at scale " + scale);
        }
    }

    private static void aBlastInsideTheBoundsIsMirrored() {
        for (final double scale : SCALES) {
            final double half = SHIP_BLOCKS * scale * 0.5D;
            check(reaches(0.0D, 0.0D, 0.0D, 4.0D, half),
                    "a blast inside the hull was skipped at scale " + scale);
        }
    }

    private static void degenerateInputsAreSkipped() {
        check(!reaches(0.0D, 0.0D, 0.0D, 0.0D, 8.0D), "a zero-radius blast was mirrored");
        check(!reaches(0.0D, 0.0D, 0.0D, -4.0D, 8.0D), "a negative-radius blast was mirrored");
        check(!reaches(Double.NaN, 0.0D, 0.0D, 4.0D, 8.0D), "a NaN blast centre was mirrored");
        check(!BlastReach.reaches(
                        0.0D, 0.0D, 0.0D, 4.0D,
                        Double.NaN, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D),
                "unbounded sublevel bounds were mirrored");
    }

    private static boolean reaches(
            final double centerX,
            final double centerY,
            final double centerZ,
            final double radius,
            final double halfExtent
    ) {
        return BlastReach.reaches(
                centerX, centerY, centerZ, radius,
                -halfExtent, -halfExtent, -halfExtent,
                halfExtent, halfExtent, halfExtent
        );
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private BlastReachTest() {}
}
