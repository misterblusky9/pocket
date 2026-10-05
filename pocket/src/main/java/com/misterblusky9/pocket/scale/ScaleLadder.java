package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleBounds;

public final class ScaleLadder {
    public static final double[] SAFE = rungs(ScaleBounds.SAFE.max(), ScaleBounds.SAFE.min());

    public static double cycle(final double[] ladder, final double current, final int steps) {
        return ladder[Math.floorMod(nearestIndex(ladder, current) + steps, ladder.length)];
    }

    public static boolean onRung(final double[] ladder, final double scale) {
        for (final double rung : ladder) {
            if (Math.abs(rung - scale) <= ScaleBounds.EPSILON) return true;
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

    public static double stepToward(final double current, final double target) {
        return stepToward(SAFE, current, target);
    }

    public static double stepToward(final double[] ladder, final double current, final double target) {
        if (ladder == null || ladder.length == 0) return target;
        if (!Double.isFinite(current) || current <= 0.0D || !Double.isFinite(target) || target <= 0.0D) return target;
        if (Math.abs(target - current) <= ScaleBounds.EPSILON) return target;

        double next = target;
        if (target > current) {
            for (final double rung : ladder) {
                if (rung <= current + ScaleBounds.EPSILON || rung > target + ScaleBounds.EPSILON) continue;
                if (rung < next) next = rung;
            }
        } else {
            for (final double rung : ladder) {
                if (rung >= current - ScaleBounds.EPSILON || rung < target - ScaleBounds.EPSILON) continue;
                if (rung > next) next = rung;
            }
        }
        return next;
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
