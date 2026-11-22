package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleBounds;

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public final class ScaleDetents {
    public static final double BOARD_MIN = 1.0D / 32.0D;
    public static final double BOARD_MAX = 8.0D;
    private static final double[] EXTENDED_STOPS = {1.0D / 64.0D, 16.0D};
    private static final double[] FINE_STOPS = {
            1.0D / 10.0D, 1.0D / 5.0D, 1.0D / 3.0D, 2.0D / 3.0D, 3.0D / 4.0D,
            1.25D, 1.5D, 1.75D, 2.25D, 2.5D, 2.75D, 3.0D, 3.25D, 3.5D, 3.75D};

    private ScaleDetents() {}

    public static double[] stops(ScaleBounds range) {
        return DoubleStream.concat(
                        IntStream.rangeClosed(-10, -1).mapToDouble(exponent -> Math.pow(2.0D, exponent)),
                        IntStream.rangeClosed(1, (int) BOARD_MAX).asDoubleStream())
                .filter(ScaleDetents::onBoard)
                .filter(range::contains)
                .toArray();
    }

    public static double[] of(ScaleBounds range) {
        double[] stops = stops(range);
        DoubleStream fine = DoubleStream.of(FINE_STOPS)
                .filter(ScaleDetents::onBoard)
                .filter(range::contains)
                .filter(stop -> DoubleStream.of(stops).noneMatch(other -> ScaleBounds.same(stop, other)));
        DoubleStream extended = DoubleStream.of(EXTENDED_STOPS).filter(range::contains);
        return DoubleStream.concat(DoubleStream.concat(DoubleStream.of(stops), fine), extended)
                .sorted()
                .toArray();
    }

    public static int nearest(double[] detents, double scale) {
        int best = 0;
        double target = Math.log(scale);
        for (int i = 1; i < detents.length; i++) {
            if (Math.abs(Math.log(detents[i]) - target) < Math.abs(Math.log(detents[best]) - target)) best = i;
        }
        return best;
    }

    private static boolean onBoard(double stop) {
        return stop >= BOARD_MIN - ScaleBounds.EPSILON && stop <= BOARD_MAX + ScaleBounds.EPSILON;
    }
}
