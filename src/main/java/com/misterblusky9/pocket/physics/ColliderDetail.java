package com.misterblusky9.pocket.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ColliderDetail {
    public enum Fidelity {
        EXACT,
        BLOCKS
    }

    public record Level(Fidelity fidelity, int quantum) {
        public Level {
            if (fidelity == null) throw new IllegalArgumentException("fidelity");
            if (quantum < 1) throw new IllegalArgumentException("quantum must be >= 1");
        }

        public boolean exact() {
            return this.fidelity == Fidelity.EXACT;
        }

        public double worldError(final double scale) {
            return this.quantum * scale;
        }

        @Override
        public String toString() {
            return this.fidelity + "/" + this.quantum;
        }
    }

    public static final Level FINEST = new Level(Fidelity.EXACT, 1);

    private static final int MAX_QUANTUM = 1;
    private static final Level[] LADDER = buildLadder();

    public static final int MAX_BOXES = 24_576;

    public static final int MAX_FRAGMENTS = 65_536;

    public static final int MAX_CELL_BOXES = 512;

    public static final long SLOW_REBUILD_MS = 6L;
    public static final int SLOW_REBUILDS_TO_DEGRADE = 3;
    public static final int MIN_BOXES_TO_DEGRADE = 1_024;

    private record LegacyFloor(int rung) {}
    private static final Map<UUID, LegacyFloor> LEGACY_FLOORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LEGACY_SLOW_STREAKS = new ConcurrentHashMap<>();

    private static Level[] buildLadder() {
        final List<Level> rungs = new ArrayList<>();
        rungs.add(FINEST);
        for (int quantum = 1; quantum <= Math.max(1, MAX_QUANTUM); quantum <<= 1) {
            rungs.add(new Level(Fidelity.BLOCKS, quantum));
            if (quantum > Integer.MAX_VALUE / 2) break;
        }
        return rungs.toArray(new Level[0]);
    }

    public static Level floor(final PlotSource source) {
        if (source == null || source.quantum() <= 1) return FINEST;
        return new Level(Fidelity.BLOCKS, source.quantum());
    }

    public static Level start(final UUID id, final PlotSource source) {
        final Level natural = floor(source);
        final LegacyFloor earned = id == null ? null : LEGACY_FLOORS.get(id);
        if (earned == null) return natural;
        return coarsest(natural, LADDER[Math.min(earned.rung(), LADDER.length - 1)]);
    }

    public static Level coarsen(final Level level) {
        final int index = indexOf(level);
        if (index < 0 || index >= LADDER.length - 1) return level;
        return LADDER[index + 1];
    }

    public static boolean canCoarsen(final Level level) {
        final int index = indexOf(level);
        return index >= 0 && index < LADDER.length - 1;
    }

    public static boolean atLeastAsCoarse(final Level candidate, final Level floor) {
        return rank(candidate) >= rank(floor);
    }

    public static boolean withinBudget(final PlotShape shape, final double scale) {
        if (shape == null) return true;
        return shape.boxes().size() <= MAX_BOXES && estimateFragments(shape, scale) <= MAX_FRAGMENTS;
    }

    public static long estimateFragments(final PlotShape shape, final double scale) {
        return shape == null ? 0L : estimateFragments(shape.boxes(), scale);
    }

    public static long estimateFragments(final List<PlotShape.Box> boxes, final double scale) {
        if (boxes == null) return 0L;
        final double bounded = scale > 0.0D && scale <= 1.0D ? scale : 1.0D;
        long total = 0L;
        for (final PlotShape.Box box : boxes) {
            final long x = spanCells(box.maxX() - box.minX(), bounded);
            final long y = spanCells(box.maxY() - box.minY(), bounded);
            final long z = spanCells(box.maxZ() - box.minZ(), bounded);
            final long xyz;
            try {
                xyz = Math.multiplyExact(Math.multiplyExact(x, y), z);
                total = Math.addExact(total, xyz);
            } catch (final ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
            if (total > MAX_FRAGMENTS) return total;
        }
        return total;
    }

    public static boolean recordRebuild(
            final UUID id,
            final Level level,
            final int boxes,
            final long millis
    ) {
        if (id == null || level == null || !canCoarsen(level)) return false;
        if (millis < SLOW_REBUILD_MS) {
            LEGACY_SLOW_STREAKS.remove(id);
            return false;
        }
        if (boxes < MIN_BOXES_TO_DEGRADE) {
            LEGACY_SLOW_STREAKS.remove(id);
            return false;
        }
        final int streak = LEGACY_SLOW_STREAKS.merge(id, 1, Integer::sum);
        if (streak < SLOW_REBUILDS_TO_DEGRADE) return false;
        LEGACY_SLOW_STREAKS.remove(id);

        final int raised = Math.min(indexOf(level) + 1, LADDER.length - 1);
        final LegacyFloor previous = LEGACY_FLOORS.get(id);
        if (previous != null && previous.rung() >= raised) return false;
        LEGACY_FLOORS.put(id, new LegacyFloor(raised));
        return true;
    }

    public static void forget(final UUID id) {
        if (id == null) return;
        LEGACY_FLOORS.remove(id);
        LEGACY_SLOW_STREAKS.remove(id);
    }

    private static long spanCells(final double sourceLength, final double scale) {
        if (!(sourceLength > 0.0D)) return 1L;
        return (long) Math.floor(sourceLength * scale) + 1L;
    }

    private static Level coarsest(final Level left, final Level right) {
        return rank(left) >= rank(right) ? left : right;
    }

    private static int indexOf(final Level level) {
        if (level == null) return 0;
        for (int i = 0; i < LADDER.length; i++) {
            if (LADDER[i].equals(level)) return i;
        }
        return -1;
    }

    private static long rank(final Level level) {
        if (level == null) return 0L;
        final long fidelity = level.exact() ? 0L : 1L;
        return (((long) level.quantum()) << 1) | fidelity;
    }

    private ColliderDetail() {}
}
