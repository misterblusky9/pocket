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
        arbitraryTargetsRemainExact();
        arbitraryStepsArriveWithoutOvershooting();
        tiersNest();
        creativeLadderCoversTheCreativeTier();
        toolLimitsNeverPushACraftFurtherOut();
        System.out.println("ScaleContractTest: PASS");
    }

    private static void arbitraryTargetsRemainExact() {
        for (final double value : new double[] {0.3D, 1.0D / 3, 0.75D, 0.09D, 0.999D, 1.5D, 3.2D, 0.04D}) {
            check(CompressionStage.exact(value) == null, "custom size must not acquire a preset identity");
            check(CompressionStage.snap(value) == value, "custom size must survive snapping");
        }
        check(CompressionStage.snap(0.5D + 0.0000001D) == 0.5D, "roundoff near a preset should canonicalize");
        check("1/3×".equals(ScaleFormat.label(1.0D / 3)), "thirds should be readable");
        check("1/32×".equals(ScaleFormat.label(1.0D / 32)), "the band floor should read as a fraction");
        check("0.3×".equals(ScaleFormat.label(0.3D)), "custom decimals must not show a nearby preset");
        check("2×".equals(ScaleFormat.label(2.0D)), "whole growth factors must not read as fractions");
        check("2.5×".equals(ScaleFormat.label(2.5D)), "fractional growth reads as a decimal");
    }

    private static void arbitraryStepsArriveWithoutOvershooting() {
        final double[] scales = {4, 2.5, 1, 0.75, 0.5, 1.0 / 3, 0.3, 0.25, 0.125, 0.09, 0.0625, 0.04, 1.0 / 32};
        for (final double from : scales) for (final double target : scales) {
            double current = from;
            for (int step = 0; step < 10 && current != target; step++) {
                final double next = CompressionStage.stepToward(current, target);
                check(next >= Math.min(current, target) && next <= Math.max(current, target), "step overshot its target");
                check(next != current, "step stalled at an intermediate size");
                current = next;
            }
            check(current == target, "ladder steps must arrive at an exact custom endpoint");
        }
    }

    private static void tiersNest() {
        check(PocketSized.MIN_SCALE <= PocketSized.CREATIVE_MIN_SCALE, "creative floor sits inside the API band");
        check(PocketSized.CREATIVE_MIN_SCALE <= PocketSized.SURVIVAL_MIN_SCALE, "survival floor sits inside creative");
        check(PocketSized.STANDARD_MAX_SCALE <= PocketSized.SURVIVAL_MAX_SCALE, "overclocking only raises the ceiling");
        check(PocketSized.SURVIVAL_MAX_SCALE <= PocketSized.CREATIVE_MAX_SCALE, "survival ceiling sits inside creative");
        check(PocketSized.CREATIVE_MAX_SCALE <= PocketSized.MAX_SCALE, "creative ceiling sits inside the API band");
        for (final CompressionStage stage : CompressionStage.values()) {
            check(stage.scale() >= PocketSized.SURVIVAL_MIN_SCALE && stage.scale() <= PocketSized.STANDARD_MAX_SCALE,
                    stage + " is reachable by standard survival tools, so it must sit in that tier");
        }
    }

    private static void toolLimitsNeverPushACraftFurtherOut() {
        check(ScaleLimits.CREATIVE.permits(1.0D, 2.0D), "creative tools reach the creative ceiling");
        check(!ScaleLimits.CREATIVE.permits(1.0D, 4.0D), "a welded partner must not be grown past the creative ceiling");
        check(!ScaleLimits.CREATIVE.permits(1.0D / 16.0D, 1.0D / 32.0D), "a welded partner must not be shrunk past the creative floor");
        check(ScaleLimits.STANDARD.permits(1.0D, 2.0D), "survival equipment reaches 2x");
        check(!ScaleLimits.STANDARD.permits(1.0D / 16.0D, 1.0D / 32.0D), "survival equipment stops at 1/16x");
        check(ScaleLimits.EXPERIMENTAL.permits(2.0D, 4.0D), "experimental sizes reach 4x");
        check(ScaleLimits.EXPERIMENTAL.permits(1.0D / 16.0D, 1.0D / 32.0D), "experimental sizes reach 1/32x");
        check(!ScaleLimits.EXPERIMENTAL.permits(4.0D, 8.0D), "experimental sizes stop at 4x");
        check(!ScaleLimits.CREATIVE.permits(2.0D, 4.0D), "the creative toy stops at 2x without experimental sizes");
        check(!ScaleLimits.STANDARD.permits(2.0D, 4.0D), "survival equipment stops at 2x");
        check(ScaleLimits.STANDARD.permits(4.0D, 2.0D), "a craft above the tier may still be brought back down");
        check(ScaleLimits.STANDARD.permits(1.0D / 64.0D, 1.0D / 32.0D), "a craft below the tier may still be brought back up");
        check(!ScaleLimits.STANDARD.permits(4.0D, 8.0D), "a craft above the tier must not be pushed further out");
        check(ScaleLimits.STANDARD.permits(4.0D, 4.0D), "holding an out-of-tier craft still is not a breach");
        check(ScaleLimits.API.permits(1.0D, PocketSized.MAX_SCALE), "the API band reaches its own ceiling");
        check(!ScaleLimits.API.permits(1.0D, PocketSized.MAX_SCALE * 2.0D), "nothing passes the API band");
        check(!ScaleLimits.CREATIVE.permits(1.0D, Double.NaN), "NaN is never a permitted goal");
    }

    private static void creativeLadderCoversTheCreativeTier() {
        final double[] ladder = ScaleLadder.CREATIVE;
        check(ladder[0] == PocketSized.CREATIVE_MAX_SCALE, "the ladder starts at the creative ceiling");
        check(ladder[ladder.length - 1] == PocketSized.CREATIVE_MIN_SCALE, "the ladder ends at the creative floor");
        for (int i = 1; i < ladder.length; i++) {
            check(ladder[i] * 2.0D == ladder[i - 1], "creative rungs are exact halves");
        }
        for (final CompressionStage stage : CompressionStage.values()) {
            check(ladder[ScaleLadder.nearestIndex(ladder, stage.scale())] == stage.scale(),
                    stage + " must stay selectable on the creative ladder");
        }
        for (final double scale : ladder) {
            check(ScaleLadder.cycle(ladder, ScaleLadder.cycle(ladder, scale, 1), -1) == scale,
                    "cycling forward then back must round-trip at " + scale);
        }
        check(ScaleLadder.cycle(ladder, PocketSized.CREATIVE_MIN_SCALE, 1) == PocketSized.CREATIVE_MAX_SCALE,
                "scrolling past the smallest rung wraps to the largest");
        check(ScaleLadder.cycle(ladder, 0.3D, 1) == 0.125D, "an off-ladder selection steps from its nearest rung");
    }

    private static void nonFiniteScalesAreRejected() {
        check(!PocketSized.isValidScale(Double.NaN), "NaN must never cross the Sable boundary");
        check(!PocketSized.isValidScale(Double.POSITIVE_INFINITY), "+Inf must be rejected");
        check(!PocketSized.isValidScale(Double.NEGATIVE_INFINITY), "-Inf must be rejected");

        check(!PocketSized.isValidScale(0.0D), "zero scale is a degenerate body, not a small one");
        check(!PocketSized.isValidScale(-0.5D), "a negative scale mirrors the craft");
        check(!PocketSized.isValidScale(PocketSized.MAX_SCALE * 2.0D), "above MAX_SCALE is out of band");
        check(!PocketSized.isValidScale(PocketSized.MIN_SCALE / 2.0D), "below MIN_SCALE is out of band");

        check(PocketSized.isValidScale(PocketSized.MIN_SCALE), "MIN_SCALE is in band");
        check(PocketSized.isValidScale(PocketSized.MAX_SCALE), "MAX_SCALE is in band");
        check(PocketSized.isValidScale(PocketSized.FULL_SCALE), "1x is in band");
        check(PocketSized.isValidScale(0.3D), "an interpolated scale between stages is in band");
        check(PocketSized.isValidScale(2.5D), "growth between 1x and MAX_SCALE is in band");

        check(PocketSized.isValidScale(PocketSized.MAX_SCALE + PocketSized.EPSILON / 2.0D),
                "a hair over MAX_SCALE from accumulation must not be treated as corruption");
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
        check(all[0].scale() == PocketSized.FULL_SCALE, "NORMAL must be exactly FULL_SCALE");
        check(all[all.length - 1].scale() >= PocketSized.MIN_SCALE,
                "the deepest stage must be inside the band");

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
