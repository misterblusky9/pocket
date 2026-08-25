package com.misterblusky9.pocket.physics;

public final class ExpansionClearanceBudgetTest {
    private static final double FIRST_TICK_OF_A_STAGE = 17.0D / 81.0D;
    private static final double FASTEST_FIRST_TICK = 1.0D - (1.0D - 1.0D / 6.0D) * (1.0D - 1.0D / 6.0D);

    private static final double OLD_FLAT_ALLOWANCE = 0.5D;

    static {
        if (ExpansionBudget.BASE_NUDGE_PER_TICK != OLD_FLAT_ALLOWANCE) {
            throw new AssertionError("the flat allowance changed; revisit these expectations");
        }
    }

    public static void main(final String[] args) {
        theAllowanceCoversTheCraftsOwnDescent();
        theRegressionCaseTheFlatAllowanceLost();
        aTallCraftGetsAProportionallyLargerAllowance();
        degenerateInputsFallBackToTheFlatAllowance();
        System.out.println("ExpansionClearanceBudgetTest: PASS");
    }

    private static void theAllowanceCoversTheCraftsOwnDescent() {
        final double[] stages = {1.0D, 0.5D, 0.25D, 0.125D, 0.0625D};
        final double[] extents = {0.5D, 3.0D, 6.0D, 16.0D, 40.0D};
        final double[] firstTicks = {FIRST_TICK_OF_A_STAGE, FASTEST_FIRST_TICK, 1.0D};

        for (int rung = 1; rung < stages.length; rung++) {
            final double from = stages[rung];
            final double to = stages[rung - 1];

            for (final double fraction : firstTicks) {
                final double next = from + (to - from) * fraction;

                for (final double extent : extents) {
                    final double descent = descentOf(extent, from, next);
                    final double budget = budgetAt(extent, from, next);

                    check(budget >= descent,
                            "allowance " + budget + " cannot undo a descent of " + descent
                                    + " (extent " + extent + ", " + from + " -> " + next + ")");
                    check(budget >= OLD_FLAT_ALLOWANCE,
                            "the allowance must never be less than the flat one it replaced");
                }
            }
        }
    }

    private static void theRegressionCaseTheFlatAllowanceLost() {
        final double plotExtent = 6.0D;
        final double from = 0.5D;
        final double next = from + (1.0D - from) * FIRST_TICK_OF_A_STAGE;
        final double extent = plotExtent * next;

        final double descent = descentOf(extent, from, next);
        check(descent > OLD_FLAT_ALLOWANCE,
                "this case is only a regression if the flat allowance was actually short; "
                        + "descent was " + descent);
        check(descent > 0.6D && descent < 0.65D,
                "expected roughly two thirds of a block of descent, got " + descent);

        check(budgetAt(extent, from, next) >= descent,
                "the case that clipped into the ground must now be covered");
    }

    private static void aTallCraftGetsAProportionallyLargerAllowance() {
        final double from = 0.5D;
        final double next = from + (1.0D - from) * FIRST_TICK_OF_A_STAGE;

        double previousBudget = 0.0D;
        double previousDescent = 0.0D;
        for (final double extent : new double[] {1.0D, 6.0D, 20.0D, 60.0D}) {
            final double budget = budgetAt(extent, from, next);
            final double descent = descentOf(extent, from, next);

            check(budget > previousBudget, "a taller craft must not get a smaller allowance");
            check(descent > previousDescent, "a taller craft must descend further; check the fixture");
            check(budget >= descent, "allowance fell behind descent at extent " + extent);

            previousBudget = budget;
            previousDescent = descent;
        }
    }

    private static void degenerateInputsFallBackToTheFlatAllowance() {
        final double flat = OLD_FLAT_ALLOWANCE;

        check(budgetAt(6.0D, 0.5D, 0.5D) == flat, "no growth spends no growth allowance");
        check(budgetAt(6.0D, 1.0D, 0.5D) == flat, "shrinking must not earn an allowance");
        check(budgetAt(6.0D, 0.0D, 0.5D) == flat, "a zero previous scale has no meaningful ratio");
        check(budgetAt(6.0D, -1.0D, 0.5D) == flat, "a negative previous scale has no meaningful ratio");

        check(ExpansionBudget.perTick(0.0D, 4.0D, 0.5D, 1.0D) == flat,
                "a box above the pivot cannot be pushed into the floor by growing");

        for (final double budget : new double[] {
                budgetAt(0.0D, 0.5D, 1.0D),
                budgetAt(6.0D, 0.5D, 1.0D),
                budgetAt(1.0E6D, 0.0625D, 0.125D),
        }) {
            check(Double.isFinite(budget) && budget > 0.0D,
                    "the allowance must always be a finite positive displacement, got " + budget);
        }
    }

    private static double budgetAt(final double extentAtNextScale, final double previous, final double next) {
        return ExpansionBudget.perTick(0.0D, -extentAtNextScale, previous, next);
    }

    private static double descentOf(final double extentAtNextScale, final double previous, final double next) {
        return extentAtNextScale - extentAtNextScale * (previous / next);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ExpansionClearanceBudgetTest() {}
}
