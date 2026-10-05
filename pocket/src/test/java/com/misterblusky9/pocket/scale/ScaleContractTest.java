package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleBounds;

public final class ScaleContractTest {
    public static void main(final String[] args) {
        everyStageIsSafeToHandToSable();
        theLadderIsFiveDescendingHalves();
        stepTowardMovesOneRungAndArrives();
        depthAndCycleNeverThrow();
        nearestRoundTripsEveryStage();
        rpmThresholdsOnlyDeepen();
        arbitraryTargetsRemainExact();
        arbitraryStepsArriveWithoutOvershooting();
        safeLadderGrowthUsesEveryOverclockRung();
        oneSafeRangeOwnsPocketGameplay();
        safeLadderCoversTheRange();
        toolLimitsNeverPushACraftFurtherOut();
        System.out.println("ScaleContractTest: PASS");
    }

    private static void arbitraryTargetsRemainExact() {
        for (final double value : new double[] {0.3D, 1.0D / 3, 0.75D, 0.09D, 0.999D, 1.5D, 3.2D, 0.04D, 6.75D, 16.5D}) {
            check(CompressionStage.exact(value) == null, "custom size must not acquire a preset identity");
            check(CompressionStage.snap(value) == value, "custom size must survive snapping");
        }
        check(CompressionStage.snap(0.5D + 0.0000001D) == 0.5D, "roundoff near a preset should canonicalize");
    }

    private static void arbitraryStepsArriveWithoutOvershooting() {
        final double[] scales = {4, 2.5, 1, 0.75, 0.5, 1.0 / 3, 0.3, 0.25, 0.125, 0.09, 0.0625, 0.04, 1.0 / 32};
        for (final double from : scales) for (final double target : scales) {
            double current = from;
            for (int step = 0; step < 10 && current != target; step++) {
                final double next = ScaleLadder.stepToward(current, target);
                check(next >= Math.min(current, target) && next <= Math.max(current, target), "step overshot its target");
                check(next != current, "step stalled at an intermediate size");
                current = next;
            }
            check(current == target, "ladder steps must arrive at an exact custom endpoint");
        }
    }

    private static void safeLadderGrowthUsesEveryOverclockRung() {
        check(ScaleLadder.stepToward(1.0D, 8.0D) == 2.0D, "1x -> 8x must step to 2x first");
        check(ScaleLadder.stepToward(2.0D, 8.0D) == 4.0D, "2x -> 8x must step to 4x next");
        check(ScaleLadder.stepToward(4.0D, 8.0D) == 8.0D, "4x -> 8x may take the final step");

        check(ScaleLadder.stepToward(8.0D, 1.0D) == 4.0D, "8x -> 1x must step to 4x first");
        check(ScaleLadder.stepToward(4.0D, 1.0D) == 2.0D, "4x -> 1x must step to 2x next");
        check(ScaleLadder.stepToward(2.0D, 1.0D) == 1.0D, "2x -> 1x may take the final step");

        check(ScaleLadder.stepToward(1.5D, 8.0D) == 2.0D, "off-rung growth must join at 2x");
        check(ScaleLadder.stepToward(3.2D, 8.0D) == 4.0D, "off-rung growth must join at 4x");
        check(ScaleLadder.stepToward(8.0D, 3.2D) == 4.0D, "off-rung shrink must stop at 4x before 3.2x");
    }

    private static void oneSafeRangeOwnsPocketGameplay() {
        check(ScaleBounds.SAFE.min() == 1.0D / 16.0D, "Pocket safe floor is 1/16x");
        check(ScaleBounds.SAFE.max() == 8.0D, "Pocket safe ceiling is 8x");
        check(ScaleLimits.SAFE.equals(ScaleBounds.SAFE), "Pocket exposes Pym's safe range directly");
        for (final CompressionStage stage : CompressionStage.values()) {
            check(ScaleBounds.SAFE.contains(stage.scale()), stage + " sits inside the safe range");
        }
    }

    private static void toolLimitsNeverPushACraftFurtherOut() {
        check(ScaleLimits.SAFE.permits(1.0D, 8.0D), "normal Pocket tools can reach 8x");
        check(ScaleLimits.SAFE.permits(1.0D, 1.0D / 16.0D), "normal Pocket tools can reach 1/16x");
        check(!ScaleLimits.SAFE.permits(1.0D, 16.0D), "normal Pocket tools stop above 8x");
        check(!ScaleLimits.SAFE.permits(1.0D / 16.0D, 1.0D / 32.0D), "normal Pocket tools stop below 1/16x");
        check(ScaleLimits.SAFE.permits(16.0D, 8.0D), "a craft above the range may be brought back down");
        check(ScaleLimits.SAFE.permits(1.0D / 32.0D, 1.0D / 16.0D), "a craft below the range may be brought back up");
        check(!ScaleLimits.SAFE.permits(16.0D, 32.0D), "a craft above the range cannot be pushed farther out");
        check(!ScaleLimits.SAFE.permits(1.0D, Double.NaN), "NaN is never a permitted goal");
    }

    private static void safeLadderCoversTheRange() {
        final double[] ladder = ScaleLadder.SAFE;
        check(ladder[0] == ScaleBounds.SAFE.max(), "the ladder starts at the safe ceiling");
        check(ladder[ladder.length - 1] == ScaleBounds.SAFE.min(), "the ladder ends at the safe floor");
        for (int i = 1; i < ladder.length; i++) {
            check(ladder[i] * 2.0D == ladder[i - 1], "safe rungs are exact halves");
        }
        for (final CompressionStage stage : CompressionStage.values()) {
            check(ladder[ScaleLadder.nearestIndex(ladder, stage.scale())] == stage.scale(),
                    stage + " must stay selectable on the safe ladder");
        }
        check(ScaleLadder.cycle(ladder, ScaleBounds.SAFE.min(), 1) == ScaleBounds.SAFE.max(),
                "scrolling past the smallest rung wraps to the largest");
    }

    private static void everyStageIsSafeToHandToSable() {
        for (final CompressionStage stage : CompressionStage.values()) {
            check(ScaleBounds.isValid(stage.scale()),
                    stage + " commands a scale the Sable boundary rejects: " + stage.scale());
            check(ScaleBounds.clampValid(stage.scale()) == stage.scale(),
                    stage + " is altered by clamping, so it is not a reachable resting point");
        }
    }

    private static void theLadderIsFiveDescendingHalves() {
        final CompressionStage[] all = CompressionStage.values();
        check(all.length == 5, "the ladder is five stages, found " + all.length);

        check(all[0] == CompressionStage.NORMAL, "the ladder starts at 1x");
        check(all[0].scale() == ScaleBounds.FULL, "NORMAL must be exactly FULL_SCALE");
        check(ScaleBounds.SAFE.contains(all[all.length - 1].scale()),
                "the deepest stage must be inside the safe range");

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
                "a scale under the band is nearest the deepest stage");
        check(CompressionStage.nearest(99.0D) == CompressionStage.NORMAL,
                "a scale over the band is nearest 1x");

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
