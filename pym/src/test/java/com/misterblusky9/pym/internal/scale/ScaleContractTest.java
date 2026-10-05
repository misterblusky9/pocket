package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.api.ResizeResult;
import net.minecraft.nbt.CompoundTag;

public final class ScaleContractTest {
    public static void main(final String[] args) {
        finitePositiveScalesAreStructurallyValid();
        clampPreservesValidScales();
        boundsNeverPushACraftFurtherOut();
        snappingCanonicalizesOnlyRoundoff();
        legacyStagesDecodeToTheirScales();
        unrestrictedScalesNeedNoGlobalConsent();
        recordsEaseToTheirTargetAndSettle();
        savedTargetsPreferTheNewestKey();
        System.out.println("ScaleContractTest: PASS");
    }

    private static void finitePositiveScalesAreStructurallyValid() {
        check(!ScaleBounds.isValid(Double.NaN), "NaN must never cross the Sable boundary");
        check(!ScaleBounds.isValid(Double.POSITIVE_INFINITY), "+Inf must be rejected");
        check(!ScaleBounds.isValid(Double.NEGATIVE_INFINITY), "-Inf must be rejected");
        check(!ScaleBounds.isValid(0.0D), "zero scale is degenerate");
        check(!ScaleBounds.isValid(-0.5D), "negative scale mirrors the craft");
        for (final double scale : new double[] {Double.MIN_VALUE, 1.0E-12D, 1.0D / 64.0D, 0.3D, 1.0D, 8.0D, 64.0D, 1000.0D, Double.MAX_VALUE}) {
            check(ScaleBounds.isValid(scale), "finite positive scale was rejected: " + scale);
        }
    }

    private static void clampPreservesValidScales() {
        for (final double scale : new double[] {1.0E-12D, 1.0D / 64.0D, 0.3D, 1.0D, 8.0D, 64.0D, 1.0E9D}) {
            check(ScaleBounds.clampValid(scale) == scale, "valid scale was clamped: " + scale);
        }
        check(Double.isNaN(ScaleBounds.clampValid(Double.NaN)), "NaN remains NaN for the validator to reject");
        check(ScaleBounds.clampValid(0.0D) == ScaleBounds.FULL, "zero folds to 1x");
        check(ScaleBounds.clampValid(-1.0D) == ScaleBounds.FULL, "negative values fold to 1x");
        check(ScaleBounds.clampValid(Double.POSITIVE_INFINITY) == ScaleBounds.FULL, "+Inf folds to 1x");
    }

    private static void boundsNeverPushACraftFurtherOut() {
        check(ScaleBounds.ANY.permits(1.0D, 1.0E200D), "the unrestricted domain accepts very large finite scales");
        check(ScaleBounds.ANY.permits(1.0D, 1.0E-200D), "the unrestricted domain accepts very small positive finite scales");
        check(!ScaleBounds.ANY.permits(1.0D, Double.NaN), "NaN is never a permitted goal");
        final ScaleBounds narrow = new ScaleBounds(0.25D, 2.0D);
        check(narrow.permits(4.0D, 2.0D), "a craft above the bounds may still be brought back down");
        check(narrow.permits(1.0D / 16.0D, 1.0D / 8.0D), "a craft below the bounds may still be brought back up");
        check(!narrow.permits(4.0D, 8.0D), "a craft above the bounds must not be pushed further out");
        check(narrow.permits(4.0D, 4.0D), "holding an out-of-bounds craft still is not a breach");
        check(narrow.contains(0.25D) && narrow.contains(2.0D) && !narrow.contains(4.0D), "contains is inclusive");
    }

    private static void snappingCanonicalizesOnlyRoundoff() {
        for (final double value : new double[] {0.3D, 1.0D / 3, 0.75D, 0.09D, 0.999D, 1.5D, 3.2D, 0.04D, 1000.0D}) {
            check(ScaleSnap.snap(value) == value, "a custom size must survive snapping: " + value);
        }
        for (int exponent = -6; exponent <= 6; exponent++) {
            final double power = Math.scalb(1.0D, exponent);
            check(ScaleSnap.snap(power + 1.0E-7D) == power, "roundoff near " + power + " must canonicalize");
            check(ScaleSnap.snap(power) == power, power + " must be a fixed point");
        }
        check(ScaleSnap.snap(1.0E9D) == 1.0E9D, "snapping must not impose a hidden hard ceiling");
    }

    private static void unrestrictedScalesNeedNoGlobalConsent() {
        check(ScaleBounds.SAFE.min() == 1.0D / 16.0D && ScaleBounds.SAFE.max() == 8.0D,
                "documented safe band remains 1/16x to 8x");
        check(ScaleBounds.ANY.permits(1.0D, 1000.0D), "large finite scales are open");
        check(ScaleBounds.ANY.permits(1.0D, 1.0E-200D), "tiny positive finite scales are open");
        check(!ScaleBounds.same(1.0E-20D, 2.0E-20D), "scale equality must be relative, not absolute");
        check(ScaleFormat.label(1.0D / 64.0D).equals("1/64\u00d7"), "fractions read as 1/N");
        check(ScaleFormat.label(64.0D).equals("64\u00d7"), "whole scales read as N");
        check(ScaleFormat.label(0.3D).equals("0.3\u00d7"), "custom sizes read as decimals");
        check(ScaleFormat.label(1.0D / 3.0D).equals("1/3\u00d7"), "thirds read as fractions");
        check(ScaleFormat.label(2.5D).equals("2.5\u00d7"), "fractional growth reads as a decimal");
    }

    private static void recordsEaseToTheirTargetAndSettle() {
        final ScaleRecord record = new ScaleRecord(1.0D);
        check(!record.moving() && record.settled() == 1.0D, "a new record is at rest");

        record.begin(0.5D, 4.0D, null, null, true);
        check(record.moving() && record.target() == 0.5D, "begin sets a target");
        double previous = record.current();
        for (int tick = 0; tick < 4; tick++) {
            final double next = record.step();
            check(next <= previous && next >= 0.5D, "a shrink never overshoots or reverses: " + next);
            record.show(next);
            previous = next;
        }
        check(record.current() == 0.5D && !record.moving(), "the last step lands exactly on the target");
        check(record.settled() == 0.5D, "settled reads the resting scale");

        record.begin(2.0D, 10.0D, null, null, true);
        record.show(record.step());
        check(record.settled() == 0.5D, "mid-transition, settled is where it started");
        record.settle(1.0D);
        check(!record.moving() && record.current() == 1.0D, "settle stops a transition in place");
    }

    private static void savedTargetsPreferTheNewestKey() {
        final CompoundTag current = new CompoundTag();
        current.putDouble("current", 0.5D);
        current.putDouble("target", 0.25D);
        current.putDouble("requested", 2.0D);
        check(ScalePersistence.savedTarget(current, 0.5D) == 0.25D, "pym 0.3 target wins");

        final CompoundTag pym02 = new CompoundTag();
        pym02.putDouble("current", 0.5D);
        pym02.putDouble("stable", 0.5D);
        pym02.putDouble("requested", 0.125D);
        check(ScalePersistence.savedTarget(pym02, 0.5D) == 0.125D, "pym 0.2 requested is the target");

        final CompoundTag pocket019 = new CompoundTag();
        pocket019.putDouble("current", 0.25D);
        pocket019.putInt("stable_stage", 2);
        pocket019.putInt("requested_stage", 3);
        check(ScalePersistence.savedTarget(pocket019, 0.25D) == 0.125D, "pocket 0.19 requested stage is the target");

        final CompoundTag bare = new CompoundTag();
        bare.putDouble("current", 0.3D);
        check(ScalePersistence.savedTarget(bare, 0.3D) == 0.3D, "with nothing else saved it stays put");
    }

    private static void legacyStagesDecodeToTheirScales() {
        final double[] stages = {1.0D, 0.5D, 0.25D, 0.125D, 0.0625D};
        for (int depth = 0; depth < stages.length; depth++) {
            check(ScalePersistence.legacyStageScale(depth) == stages[depth],
                    "Pocket 0.19 stage depth " + depth + " must decode to " + stages[depth]);
            check(ScalePersistence.nearestLegacyStage(stages[depth]) == stages[depth],
                    "legacy stage " + stages[depth] + " must round-trip");
        }
        check(ScalePersistence.legacyStageScale(-3) == 1.0D, "an underflowed legacy depth clamps to 1x");
        check(ScalePersistence.legacyStageScale(99) == 0.0625D, "an overflowed legacy depth clamps to 1/16x");
        check(ScalePersistence.nearestLegacyStage(0.4D) == 0.5D, "a legacy manual target snaps to its nearest stage");
        check(ScalePersistence.nearestLegacyStage(0.001D) == 0.0625D, "a legacy target below the ladder takes the deepest stage");
        check(ScalePersistence.ROOT_KEY.equals("pym_scale"), "live scale is written under Pym's key");
        check(ScalePersistence.LEGACY_ROOT_KEY.equals("pocket_scale"), "Pocket 0.19 worlds are still read");

        final CompoundTag legacyOnly = new CompoundTag();
        final CompoundTag legacyScale = new CompoundTag();
        legacyScale.putDouble("current", 0.25D);
        legacyOnly.put(ScalePersistence.LEGACY_ROOT_KEY, legacyScale);
        check(ScalePersistence.storedTag(legacyOnly) == legacyScale, "a 0.19 sublevel is read from pocket_scale");

        final CompoundTag both = legacyOnly.copy();
        final CompoundTag current = new CompoundTag();
        current.putDouble("current", 0.5D);
        both.put(ScalePersistence.ROOT_KEY, current);
        check(ScalePersistence.storedTag(both).getDouble("current") == 0.5D, "pym_scale wins over a stale legacy key");
        check(ScalePersistence.storedTag(new CompoundTag()) == null, "an unscaled sublevel stores nothing");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ScaleContractTest() {}
}
