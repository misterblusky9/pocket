package com.misterblusky9.pocket.moon;

// Every rejection Sable throws out of addConstraint, checked before the call.
// These throw inside a physics tick, so they must never be reached.
public final class MoonConstraintPreflight {
    public enum Result {
        OK,
        NO_BODIES,
        SAME_BODY,
        BODY_REMOVED,
        ANCHOR_OUTSIDE_PLOT,
        ANCHOR_IN_PLOT_GRID
    }

    // plotBacked: typed as a ServerSubLevel, so anchors are validated against its plot.
    // anchorInsideOwnPlot: only meaningful when plotBacked.
    // anchorInsidePlotGrid: only meaningful when not plotBacked - Sable rejects a raw
    // body whose anchor lands anywhere in the plot grid region.
    public record Body(
            int runtimeId,
            boolean removed,
            boolean plotBacked,
            boolean anchorInsideOwnPlot,
            boolean anchorInsidePlotGrid
    ) {
        public static Body subLevel(final int runtimeId, final boolean anchorInsideOwnPlot) {
            return new Body(runtimeId, false, true, anchorInsideOwnPlot, false);
        }

        public static Body raw(final int runtimeId, final boolean anchorInsidePlotGrid) {
            return new Body(runtimeId, false, false, false, anchorInsidePlotGrid);
        }

        public Body asRemoved() {
            return new Body(runtimeId, true, plotBacked, anchorInsideOwnPlot, anchorInsidePlotGrid);
        }
    }

    public static Result check(final Body a, final Body b) {
        if (a == null && b == null) return Result.NO_BODIES;

        // The shim and the box are distinct objects sharing one Rapier body, so
        // reference equality is not enough - identity is the runtime id.
        if (a != null && b != null && a.runtimeId() == b.runtimeId()) return Result.SAME_BODY;

        final Result first = checkBody(a);
        if (first != Result.OK) return first;
        return checkBody(b);
    }

    private static Result checkBody(final Body body) {
        if (body == null) return Result.OK;
        if (body.removed()) return Result.BODY_REMOVED;
        if (body.plotBacked()) {
            return body.anchorInsideOwnPlot() ? Result.OK : Result.ANCHOR_OUTSIDE_PLOT;
        }
        return body.anchorInsidePlotGrid() ? Result.ANCHOR_IN_PLOT_GRID : Result.OK;
    }

    private MoonConstraintPreflight() {}
}
