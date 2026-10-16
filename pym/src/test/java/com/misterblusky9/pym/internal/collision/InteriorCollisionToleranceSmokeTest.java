package com.misterblusky9.pym.internal.collision;

/** No test framework dependency: run directly with `java` after compiling both files. */
public final class InteriorCollisionToleranceSmokeTest {
    public static void main(String[] args) {
        check(1.0, 0.01);
        check(2.0, 0.01);
        check(0.5, 0.005);
        check(1.0 / 256.0, 0.01 / 256.0);
        check(1.0 / 4096.0, 0.01 / 4096.0);
        check(0.0, 0.0);
        check(Double.NaN, 0.01);
        double thinVolume = 1.0 / 256.0; // e.g., 1/16 x 1/16 x 1 voxel
        double actualDifference = thinVolume / 2.0;
        if (!(actualDifference < 0.01)) throw new AssertionError("Vanilla must discard the example");
        if (!(actualDifference >= InteriorCollisionTolerance.forShape(0.01, thinVolume))) {
            throw new AssertionError("Scaled contact was still discarded");
        }
        System.out.println("InteriorCollisionToleranceSmokeTest: passed 8 checks");
    }
    private static void check(double volume, double wanted) {
        double found = InteriorCollisionTolerance.forShape(0.01, volume);
        if (Math.abs(found - wanted) > 1.0e-14) {
            throw new AssertionError(volume + " -> " + found + ", expected " + wanted);
        }
    }
}
