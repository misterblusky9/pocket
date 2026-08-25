package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;

public final class ScaleContractTest {
    public static void main(final String[] args) {
        nonFiniteScalesAreRejected();
        clampAloneIsNotAValidator();
        clampLandsInsideTheBand();
        everyStageIsSafeToHandToSable();
        theLadderIsFiveDescendingHalves();
        stepTowardMovesOneRungAndArrives();
        depthAndCycleNeverThrow();
        nearestRoundTripsEveryStage();
        rpmThresholdsOnlyDeepen();
        System.out.println("ScaleContractTest: PASS");
    }

    private static void nonFiniteScalesAreRejected() {
        check(!PocketSized.isValidScale(Double.NaN), "NaN must never cross the Sable boundary");
        check(!PocketSized.isValidScale(Double.POSITIVE_INFINITY), "+Inf must be rejected");
        check(!PocketSized.isValidScale(Double.NEGATIVE_INFINITY), "-Inf must be rejected");

        check(!PocketSized.isValidScale(0.0D), "zero scale is a degenerate body, not a small one");
        check(!PocketSized.isValidScale(-0.5D), "a negative scale mirrors the craft");
        check(!PocketSized.isValidScale(2.0D), "above 1x is out of band");
        check(!PocketSized.isValidScale(PocketSized.MIN_SCALE / 2.0D), "below 1/16 is out of band");

        check(PocketSized.isValidScale(PocketSized.MIN_SCALE), "1/16 is in band");
        check(PocketSized.isValidScale(PocketSized.MAX_SCALE), "1x is in band");
        check(PocketSized.isValidScale(0.3D), "an interpolated scale between stages is in band");

        check(PocketSized.isValidScale(PocketSized.MAX_SCALE + PocketSized.EPSILON / 2.0D),
                "a hair over 1x from accumulation must not be treated as corruption");
    }

    private static void clampAloneIsNotAValidator() {
        check(Double.isNaN(PocketSized.clampScale(Double.NaN)),
                "clampScale is documented as passing NaN through - if it no longer does, "
                        + "isValidScale's reason for existing has changed");
    }

    private static void clampLandsInsideTheBand() {
        final double[] wild = {
                -100.0D, -1.0D, 0.0D, PocketSized.MIN_SCALE / 4.0D, 0.3D, 1.0D, 5.0D, 1.0E9D,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        };
        for (final double scale : wild) {
            final double clamped = PocketSized.clampScale(scale);
            check(clamped >= PocketSized.MIN_SCALE && clamped <= PocketSized.MAX_SCALE,
                    "clampScale(" + scale + ") left the band at " + clamped);
            check(PocketSized.isValidScale(clamped),
                    "anything clampScale returns from a finite input must pass the validator");
        }
    }

    private static void everyStageIsSafeToHandToSable() {
        for (final CompressionStage stage : CompressionStage.values()) {
            check(PocketSized.isValidScale(stage.scale()),
                    stage + " commands a scale the Sable boundary rejects: " + stage.scale());
            check(PocketSized.clampScale(stage.scale()) == stage.scale(),
                    stage + " is altered by clamping, so it is not a reachable resting point");
        }
    }

    private static void theLadderIsFiveDescendingHalves() {
        final CompressionStage[] all = CompressionStage.values();
        check(all.length == 5, "the ladder is five stages, found " + all.length);

        check(all[0] == CompressionStage.NORMAL, "the ladder starts at 1x");
        check(all[0].scale() == PocketSized.MAX_SCALE, "NORMAL must be exactly MAX_SCALE");
        check(all[all.length - 1].scale() == PocketSized.MIN_SCALE,
                "the deepest stage must be exactly MIN_SCALE");

        check(!CompressionStage.NORMAL.isCompressed(), "1x is not a compressed state");
        for (int i = 0; i < all.length; i++) {
            check(all[i].depth() == i, all[i] + " reports depth " + all[i].depth() + ", expected " + i);
            if (i == 0) continue;

            check(all[i].scale() * 2.0D == all[i - 1].scale(),
                    all[i] + " is not exactly half of " + all[i - 1]);
            check(all[i].isDeeperThan(all[i - 1]), all[i] + " must be deeper than " + all[i - 1]);
            check(all[i].isCompressed(), all[i] + " is a compressed state");
        }
    }

    private static void stepTowardMovesOneRungAndArrives() {
        final CompressionStage[] all = CompressionStage.values();

        for (final CompressionStage from : all) {
            check(from.stepToward(from) == from, from + " toward itself must be a fixed point");
            check(from.stepToward(null) == from, "a null target must leave the stage alone");

            for (final CompressionStage to : all) {
                final CompressionStage next = from.stepToward(to);
                final int moved = Math.abs(next.depth() - from.depth());
                check(moved <= 1, from + " -> " + to + " jumped " + moved + " rungs");
                if (from != to) {
                    check(moved == 1, from + " -> " + to + " did not advance");
                    check(Math.abs(next.depth() - to.depth()) < Math.abs(from.depth() - to.depth()),
                            from + " -> " + to + " stepped the wrong way");
                }

                CompressionStage walk = from;
                int steps = 0;
                while (walk != to) {
                    walk = walk.stepToward(to);
                    steps++;
                    check(steps <= all.length, from + " -> " + to + " did not converge");
                }
            }
        }
    }

    private static void depthAndCycleNeverThrow() {
        final int[] depths = {Integer.MIN_VALUE, -7, -1, 0, 2, 4, 5, 99, Integer.MAX_VALUE};
        for (final int depth : depths) {
            final CompressionStage stage = CompressionStage.fromDepth(depth);
            check(stage != null, "fromDepth(" + depth + ") returned null");
            if (depth >= 0 && depth < CompressionStage.values().length) {
                check(stage.depth() == depth, "fromDepth(" + depth + ") lost its depth");
            }
        }
        check(CompressionStage.fromDepth(Integer.MIN_VALUE) == CompressionStage.NORMAL,
                "an underflowed depth clamps to 1x");
        check(CompressionStage.fromDepth(Integer.MAX_VALUE) == CompressionStage.SIXTEENTH,
                "an overflowed depth clamps to the deepest stage");

        final int[] steps = {Integer.MIN_VALUE, -6, -1, 0, 1, 5, 12, Integer.MAX_VALUE};
        for (final CompressionStage from : CompressionStage.values()) {
            check(from.cycle(0) == from, "cycling by zero must not move");
            for (final int step : steps) {
                check(from.cycle(step) != null, from + ".cycle(" + step + ") returned null");
            }
            check(from.cycle(CompressionStage.values().length) == from,
                    "a full turn of the wheel returns to " + from);
            check(from.cycle(1).cycle(-1) == from, "cycling forward then back must round-trip");
        }
    }

    private static void nearestRoundTripsEveryStage() {
        for (final CompressionStage stage : CompressionStage.values()) {
            check(CompressionStage.nearest(stage.scale()) == stage,
                    stage + " does not survive a scale round-trip");
        }

        check(CompressionStage.nearest(Double.NaN) != null, "a NaN scale must still name a stage");
        check(CompressionStage.nearest(-5.0D) == CompressionStage.SIXTEENTH,
                "a scale under the band clamps to MIN_SCALE, which is the deepest stage");
        check(CompressionStage.nearest(99.0D) == CompressionStage.NORMAL,
                "a scale over the band clamps to MAX_SCALE, which is 1x");

        check(CompressionStage.nearest(0.4D) == CompressionStage.HALF, "0.4 is nearest 1/2");
        check(CompressionStage.nearest(0.2D) == CompressionStage.QUARTER, "0.2 is nearest 1/4");
    }

    private static void rpmThresholdsOnlyDeepen() {
        final float[] rpms = {0.0F, 1.0F, 15.9F, 16.0F, 31.0F, 32.0F, 63.0F, 64.0F, 127.0F, 128.0F, 4096.0F};

        CompressionStage previous = CompressionStage.deepestForRpm(rpms[0]);
        for (final float rpm : rpms) {
            final CompressionStage forward = CompressionStage.deepestForRpm(rpm);
            final CompressionStage reverse = CompressionStage.deepestForRpm(-rpm);
            check(forward == reverse, "rotation direction changed the stage at " + rpm + " rpm");
            check(forward.depth() >= previous.depth(),
                    "more rpm gave a shallower stage at " + rpm + ": " + previous + " -> " + forward);
            previous = forward;
        }

        check(CompressionStage.deepestForRpm(0.0F) == CompressionStage.NORMAL,
                "a stopped compressor must not compress");
        check(CompressionStage.deepestForRpm(4096.0F) == CompressionStage.SIXTEENTH,
                "the fastest rotation reaches the deepest stage");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ScaleContractTest() {}
}
