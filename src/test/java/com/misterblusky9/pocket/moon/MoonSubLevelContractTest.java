package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.moon.MoonConstraintPreflight.Body;
import com.misterblusky9.pocket.moon.MoonConstraintPreflight.Result;
import com.misterblusky9.pocket.moon.MoonPlotFrame.Anchor;
import com.misterblusky9.pocket.moon.MoonPlungerSpring.Impulse;

public final class MoonSubLevelContractTest {
    private static final double TOLERANCE = 1.0E-9D;

    // One 2-chunk plot, and the moon at its documented full size.
    private static final MoonPlotFrame PLOT = MoonPlotFrame.cube(1024, 0, 2048, 32);
    private static final double FULL_HALF_EXTENT = 16.0D;

    public static void main(final String[] args) {
        bodySpaceRoundTripsThroughThePlot();
        everySurfaceAnchorLandsInsideThePlot();
        thePlotCeilingIsTheAxisAlignedHalfExtent();
        aMoonTooLargeForItsPlotIsRefusedNotClamped();
        anchorsAreRejectedRatherThanSilentlyClamped();

        theSpringCarriesEffectiveMass();
        massNormalizationIsNotOptional();
        theSpringMatchesSimulatedByHand();
        stretchClampsAtTwelveBlocks();
        integratedPullIsIndependentOfStepRate();
        thePairPullsEqualAndOpposite();
        degenerateInputsProduceNoImpulse();

        theShimAndTheBoxAreOneBody();
        worldAnchoredConstraintsAreAllowed();
        removedBodiesNeverReachThePipeline();
        plotBackedAnchorsMustBeInTheirOwnPlot();
        rawBodyAnchorsMustStayOutOfThePlotGrid();

        System.out.println("MoonSubLevelContractTest: PASS");
    }

    // --- anchor space ---

    private static void bodySpaceRoundTripsThroughThePlot() {
        final double[][] locals = {
                {0.0D, 0.0D, 0.0D},
                {FULL_HALF_EXTENT, 0.0D, 0.0D},
                {-3.5D, 7.25D, -11.125D},
                {FULL_HALF_EXTENT, -FULL_HALF_EXTENT, FULL_HALF_EXTENT},
        };

        for (final double[] local : locals) {
            final Anchor plot = PLOT.toPlotAnchor(local[0], local[1], local[2]);
            final Anchor back = PLOT.toBodyLocal(plot);
            check(Math.abs(back.x() - local[0]) < TOLERANCE
                            && Math.abs(back.y() - local[1]) < TOLERANCE
                            && Math.abs(back.z() - local[2]) < TOLERANCE,
                    "body space did not survive the trip through plot space: " + back);
        }

        final Anchor origin = PLOT.origin();
        final Anchor mapped = PLOT.toPlotAnchor(0.0D, 0.0D, 0.0D);
        check(origin.equals(mapped), "the body origin must map to the plot centre, got " + mapped);
    }

    private static void everySurfaceAnchorLandsInsideThePlot() {
        // The corners are the worst case: any anchor Sable validates comes from a grab
        // or plunge on the surface, and a corner is the farthest of those from centre.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    final Anchor corner = PLOT.toPlotAnchor(
                            sx * FULL_HALF_EXTENT,
                            sy * FULL_HALF_EXTENT,
                            sz * FULL_HALF_EXTENT
                    );
                    check(PLOT.contains(corner),
                            "a surface anchor fell outside the plot - Sable throws on this "
                                    + "inside the physics tick: " + corner);
                }
            }
        }

        check(PLOT.contains(PLOT.origin()), "the body origin must be a legal anchor");
    }

    private static void thePlotCeilingIsTheAxisAlignedHalfExtent() {
        check(Math.abs(PLOT.maxHalfExtent() - FULL_HALF_EXTENT) < TOLERANCE,
                "a 32-block plot holds a half extent of 16, got " + PLOT.maxHalfExtent());

        // Anchors are body-space and body space does not rotate; if this ever starts
        // demanding the rotated diagonal, the plot sizing rule changed underneath us.
        final double diagonal = FULL_HALF_EXTENT * Math.sqrt(3.0D);
        check(!PLOT.fits(diagonal),
                "the plot must be sized for the axis-aligned half extent, not the diagonal");

        final MoonPlotFrame oblong = new MoonPlotFrame(0, 0, 0, 63, 31, 63);
        check(Math.abs(oblong.maxHalfExtent() - 16.0D) < TOLERANCE,
                "the tightest axis sets the ceiling, got " + oblong.maxHalfExtent());
    }

    private static void aMoonTooLargeForItsPlotIsRefusedNotClamped() {
        check(PLOT.fits(FULL_HALF_EXTENT), "the documented full-size moon must fit its plot");
        check(PLOT.fits(FULL_HALF_EXTENT / 16.0D), "the deepest compressed moon must fit");
        check(!PLOT.fits(FULL_HALF_EXTENT + 0.001D),
                "MoonScale is continuous and the plot is not - growth past the plot must be refused");
        check(!PLOT.fits(0.0D), "a zero-extent moon is a degenerate body");
        check(!PLOT.fits(-1.0D), "a negative extent mirrors the body");
        check(!PLOT.fits(Double.NaN), "NaN must never reach the Sable boundary");
        check(!PLOT.fits(Double.POSITIVE_INFINITY), "+Inf must be refused");
    }

    private static void anchorsAreRejectedRatherThanSilentlyClamped() {
        final Anchor beyond = PLOT.toPlotAnchor(FULL_HALF_EXTENT + 4.0D, 0.0D, 0.0D);
        check(!PLOT.contains(beyond),
                "an out-of-plot anchor must report false so the caller can bail, not be clamped");

        final Anchor edge = PLOT.toPlotAnchor(FULL_HALF_EXTENT, 0.0D, 0.0D);
        check(PLOT.contains(edge), "the plot face itself is a legal anchor, got " + edge);
    }

    // --- plunger spring ---

    private static void theSpringCarriesEffectiveMass() {
        check(Math.abs(MoonPlungerSpring.effectiveMass(1.0D / 500.0D, 1.0D / 2000.0D) - 500.0D) < 1.0E-6D,
                "the pair yields at whichever body is lighter at the anchor");
        check(Math.abs(MoonPlungerSpring.effectiveMass(1.0D / 500.0D, -1.0D) - 500.0D) < 1.0E-6D,
                "an absent second body must not change the effective mass");
        check(MoonPlungerSpring.effectiveMass(0.0D, 0.0D) == 0.0D,
                "an immovable pair has no effective mass to pull with");

        final Impulse light = MoonPlungerSpring.impulse(2.0D, 0.0D, 0.0D, 1.0D / 100.0D, -1.0D, 0.05D);
        final Impulse heavy = MoonPlungerSpring.impulse(2.0D, 0.0D, 0.0D, 1.0D / 200.0D, -1.0D, 0.05D);
        check(Math.abs(heavy.x() - light.x() * 2.0D) < 1.0E-9D,
                "doubling the effective mass must double the impulse, so both bodies accelerate alike");
    }

    private static void massNormalizationIsNotOptional() {
        // Guards the exact bug the moon fork shipped: a mass-independent impulse.
        final double dt = 0.05D;
        final double mass = 500.0D;
        final Impulse actual = MoonPlungerSpring.impulse(1.0D, 0.0D, 0.0D, 1.0D / mass, -1.0D, dt);
        final double unnormalized = 1.0D * MoonPlungerSpring.FORCE * MoonPlungerSpring.IMPULSE_FACTOR * dt;

        check(Math.abs(actual.x() - unnormalized) > 1.0D,
                "the impulse is mass-independent - the 1/inverseNormalMass factor was dropped");
        check(Math.abs(actual.x() - unnormalized * mass) < 1.0E-6D,
                "the impulse must be the unnormalized pull scaled by effective mass");
    }

    private static void theSpringMatchesSimulatedByHand() {
        // delta 3 -> force 120; effective mass 500; scale 500 * 0.07 * 0.05 = 1.75.
        final Impulse impulse = MoonPlungerSpring.impulse(3.0D, 0.0D, 0.0D, 1.0D / 500.0D, 1.0D / 2000.0D, 0.05D);
        check(Math.abs(impulse.x() - 210.0D) < 1.0E-6D,
                "expected Simulated's 210 on this input, got " + impulse.x());
        check(impulse.y() == 0.0D && impulse.z() == 0.0D, "a pull along one axis must stay on it");
    }

    private static void stretchClampsAtTwelveBlocks() {
        final Impulse far = MoonPlungerSpring.impulse(100.0D, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 0.05D);
        final Impulse atLimit = MoonPlungerSpring.impulse(
                MoonPlungerSpring.MAX_STRETCH, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 0.05D);
        check(Math.abs(far.x() - atLimit.x()) < 1.0E-6D,
                "past 12 blocks the pull must stop growing, got " + far.x() + " vs " + atLimit.x());

        final Impulse diagonal = MoonPlungerSpring.impulse(100.0D, 100.0D, 100.0D, 1.0D / 500.0D, -1.0D, 0.05D);
        final double clampedPull = diagonal.length() / (500.0D * MoonPlungerSpring.IMPULSE_FACTOR * 0.05D);
        check(Math.abs(clampedPull - MoonPlungerSpring.MAX_STRETCH * MoonPlungerSpring.FORCE) < 1.0E-6D,
                "the clamp is on length, not per axis, got " + clampedPull);
    }

    private static void integratedPullIsIndependentOfStepRate() {
        final double perSecondAt20 = 20.0D * MoonPlungerSpring
                .impulse(4.0D, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 1.0D / 20.0D).x();
        final double perSecondAt60 = 60.0D * MoonPlungerSpring
                .impulse(4.0D, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 1.0D / 60.0D).x();
        check(Math.abs(perSecondAt20 - perSecondAt60) < 1.0E-6D,
                "the same pull must integrate the same whether it is applied per tick or per substep");
    }

    private static void thePairPullsEqualAndOpposite() {
        final Impulse onSubLevel = MoonPlungerSpring.impulse(0.0D, 5.0D, 0.0D, 1.0D / 500.0D, 1.0D / 800.0D, 0.05D);
        final Impulse onMoon = onSubLevel.negated();
        check(Math.abs(onSubLevel.y() + onMoon.y()) < TOLERANCE, "the pair must not inject momentum");
        check(onMoon.y() < 0.0D && onSubLevel.y() > 0.0D, "each end is pulled toward the other");
    }

    private static void degenerateInputsProduceNoImpulse() {
        check(sameAsZero(MoonPlungerSpring.impulse(0.0D, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 0.05D)),
                "no separation means no pull");
        check(sameAsZero(MoonPlungerSpring.impulse(1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.05D)),
                "an immovable body must not be pulled, and must not yield Inf");
        check(sameAsZero(MoonPlungerSpring.impulse(Double.NaN, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 0.05D)),
                "NaN separation must never reach the pipeline");
        check(sameAsZero(MoonPlungerSpring.impulse(1.0D, 0.0D, 0.0D, 1.0D / 500.0D, -1.0D, 0.0D)),
                "a zero timestep applies nothing");
    }

    // --- constraint preflight ---

    private static void theShimAndTheBoxAreOneBody() {
        final Body shim = Body.subLevel(77, true);
        final Body box = Body.raw(77, false);
        check(MoonConstraintPreflight.check(shim, box) == Result.SAME_BODY,
                "the shim and the moon box share a Rapier body - identity is the runtime id, "
                        + "not the Java reference");

        final Body otherCraft = Body.subLevel(78, true);
        check(MoonConstraintPreflight.check(shim, otherCraft) == Result.OK,
                "two distinct bodies must still be allowed to constrain");
    }

    private static void worldAnchoredConstraintsAreAllowed() {
        check(MoonConstraintPreflight.check(Body.subLevel(77, true), null) == Result.OK,
                "a moon-to-world constraint is how the staff lock works");
        check(MoonConstraintPreflight.check(null, Body.subLevel(77, true)) == Result.OK,
                "the world may be either side");
        check(MoonConstraintPreflight.check(null, null) == Result.NO_BODIES,
                "world-to-world is not a constraint");
    }

    private static void removedBodiesNeverReachThePipeline() {
        check(MoonConstraintPreflight.check(Body.subLevel(77, true).asRemoved(), Body.subLevel(78, true))
                        == Result.BODY_REMOVED,
                "a removed body throws out of assertBodyValid inside the physics tick");
        check(MoonConstraintPreflight.check(Body.subLevel(77, true), Body.subLevel(78, true).asRemoved())
                        == Result.BODY_REMOVED,
                "either side being removed is fatal");
    }

    private static void plotBackedAnchorsMustBeInTheirOwnPlot() {
        check(MoonConstraintPreflight.check(Body.subLevel(77, false), null) == Result.ANCHOR_OUTSIDE_PLOT,
                "once the moon is typed as a sublevel its anchors are plot-validated");
        check(MoonConstraintPreflight.check(Body.subLevel(77, true), Body.subLevel(78, false))
                        == Result.ANCHOR_OUTSIDE_PLOT,
                "both ends are validated, not just the first");
    }

    private static void rawBodyAnchorsMustStayOutOfThePlotGrid() {
        check(MoonConstraintPreflight.check(Body.raw(77, false), null) == Result.OK,
                "a raw body anchored in world space is fine");
        check(MoonConstraintPreflight.check(Body.raw(77, true), null) == Result.ANCHOR_IN_PLOT_GRID,
                "Sable rejects a non-sublevel body whose anchor lands in the plot grid - "
                        + "this is the trap when converting the moon from raw box to shim");
    }

    private static boolean sameAsZero(final Impulse impulse) {
        return impulse.x() == 0.0D && impulse.y() == 0.0D && impulse.z() == 0.0D;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private MoonSubLevelContractTest() {}
}
