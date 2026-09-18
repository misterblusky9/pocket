package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;

public final class ScaleLadder {
    public static final double[] CREATIVE = rungs(PocketSized.CREATIVE_MAX_SCALE, PocketSized.CREATIVE_MIN_SCALE);
    public static final double[] EXPERIMENTAL =
            rungs(PocketSized.EXPERIMENTAL_MAX_SCALE, PocketSized.EXPERIMENTAL_MIN_SCALE);

    public static double cycle(final double[] ladder, final double current, final int steps) {
        return ladder[Math.floorMod(nearestIndex(ladder, current) + steps, ladder.length)];
    }

    public static boolean onRung(final double[] ladder, final double scale) {
        for (final double rung : ladder) {
            if (Math.abs(rung - scale) <= PocketSized.EPSILON) return true;
        }
        return false;
    }

    public static double[] withGhost(final double[] ladder, final double ghost) {
        if (!Double.isFinite(ghost) || ghost <= 0.0D || onRung(ladder, ghost)) return ladder;

        final double[] out = new double[ladder.length + 1];
        int i = 0;
        while (i < ladder.length && ladder[i] > ghost) {
            out[i] = ladder[i];
            i++;
        }
        out[i] = ghost;
        System.arraycopy(ladder, i, out, i + 1, ladder.length - i);
        return out;
    }

    public static int nearestIndex(final double[] ladder, final double scale) {
        final double target = Double.isFinite(scale) && scale > 0.0D ? Math.log(scale) : 0.0D;
        int best = 0;
        for (int i = 1; i < ladder.length; i++) {
            if (Math.abs(Math.log(ladder[i]) - target) < Math.abs(Math.log(ladder[best]) - target)) best = i;
        }
        return best;
    }

    private static double[] rungs(final double largest, final double smallest) {
        final int count = (int) Math.round(Math.log(largest / smallest) / Math.log(2.0D)) + 1;
        final double[] rungs = new double[count];
        for (int i = 0; i < count; i++) rungs[i] = largest / (1L << i);
        return rungs;
    }

    private ScaleLadder() {}
}
