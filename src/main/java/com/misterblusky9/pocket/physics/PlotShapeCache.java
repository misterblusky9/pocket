package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pocket.scale.ScaleState;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlotShapeCache {
    private static final Map<UUID, Entry> SERVER_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Entry> CLIENT_ENTRIES = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> REVISIONS = new ConcurrentHashMap<>();

    private static final Map<UUID, Region> PENDING = new ConcurrentHashMap<>();

    public record Region(
            boolean full,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        public static final Region FULL = new Region(true, 0, 0, 0, 0, 0, 0);

        static Region tile(
                final PlotSource source,
                final PlotShape.TileKey tile,
                final int quantum
        ) {
            final int span = quantum * PlotShape.TILE;
            return new Region(false,
                    Math.max(source.blockMinX(), tile.x() * span),
                    Math.max(source.blockMinY(), tile.y() * span),
                    Math.max(source.blockMinZ(), tile.z() * span),
                    Math.min(source.blockMaxX(), tile.x() * span + span - 1),
                    Math.min(source.blockMaxY(), tile.y() * span + span - 1),
                    Math.min(source.blockMaxZ(), tile.z() * span + span - 1));
        }

        Region union(final Region other) {
            if (other == null) return this;
            if (this.full || other.full) return FULL;
            return new Region(false,
                    Math.min(this.minX, other.minX),
                    Math.min(this.minY, other.minY),
                    Math.min(this.minZ, other.minZ),
                    Math.max(this.maxX, other.maxX),
                    Math.max(this.maxY, other.maxY),
                    Math.max(this.maxZ, other.maxZ));
        }
    }

    private static final class Entry {
        private PlotSource source;
        private PlotShape shape;

        private double budgetScale = Double.NaN;
        private boolean budgetOk;

        private final Set<Edit> pending = new LinkedHashSet<>();
    }

    private static Map<UUID, Entry> entriesFor(final SubLevel subLevel) {
        return subLevel.getLevel() != null && subLevel.getLevel().isClientSide()
                ? CLIENT_ENTRIES
                : SERVER_ENTRIES;
    }

    private static boolean clientSide(final SubLevel subLevel) {
        return subLevel.getLevel() != null && subLevel.getLevel().isClientSide();
    }

    private static void bump(final SubLevel subLevel, final UUID id, final Region changed) {
        if (clientSide(subLevel)) return;
        REVISIONS.merge(id, 1, Integer::sum);
        PENDING.merge(id, changed, Region::union);
    }

    public static PlotShape get(final SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return null;

        final UUID id = subLevel.getUniqueId();
        if (id == null) return null;

        final BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;

        final Map<UUID, Entry> entries = entriesFor(subLevel);
        final Entry existing = entries.get(id);
        if (existing != null && existing.source != null && existing.source.covers(bounds)
                && existing.shape != null) {
            if (applyPendingEdits(subLevel, id, existing)) {
                return coarsenIfOverBudget(subLevel, id, entries, existing);
            }
        }

        final PlotSource source = PlotSource.of(subLevel.getLevel(), bounds);
        if (source == null) {
            if (entries.remove(id) != null) bump(subLevel, id, Region.FULL);
            return null;
        }

        final Entry entry = new Entry();
        entry.source = source;
        entry.shape = meshWithinBudget(id, source, scaleOf(subLevel));
        if (entry.shape == null) {
            if (entries.remove(id) != null) bump(subLevel, id, Region.FULL);
            return null;
        }

        entries.put(id, entry);
        bump(subLevel, id, Region.FULL);
        return entry.shape;
    }

    private static PlotShape meshWithinBudget(final UUID id, final PlotSource source, final double scale) {
        ColliderDetail.Level level = ColliderDetail.start(id, source);
        while (true) {
            final PlotShape shape = PlotShape.mesh(source, level);
            if (shape == null) return null;
            if (ColliderDetail.withinBudget(shape, scale) || !ColliderDetail.canCoarsen(level)) return shape;
            level = ColliderDetail.coarsen(level);
        }
    }

    private static PlotShape coarsenIfOverBudget(
            final SubLevel subLevel,
            final UUID id,
            final Map<UUID, Entry> entries,
            final Entry entry
    ) {
        final double scale = scaleOf(subLevel);
        final ColliderDetail.Level earned = ColliderDetail.start(id, entry.source);
        if (entry.budgetScale != scale) {
            entry.budgetScale = scale;
            entry.budgetOk = ColliderDetail.withinBudget(entry.shape, scale);
        }
        final boolean overBudget = !entry.budgetOk;
        final boolean belowFloor = !ColliderDetail.atLeastAsCoarse(entry.shape.level(), earned);
        if (!overBudget && !belowFloor) return entry.shape;
        if (!belowFloor && !ColliderDetail.canCoarsen(entry.shape.level())) return entry.shape;

        final ColliderDetail.Level next = belowFloor ? earned : ColliderDetail.coarsen(entry.shape.level());
        final PlotShape rebuilt = PlotShape.mesh(entry.source, next);
        if (rebuilt == null) return entry.shape;

        entry.shape = rebuilt;
        entry.budgetScale = Double.NaN;
        entries.put(id, entry);
        bump(subLevel, id, Region.FULL);
        return rebuilt;
    }

    private static double scaleOf(final SubLevel subLevel) {
        final double scale = ScaleState.getScale(subLevel);
        return scale > 0.0D && scale <= 1.0D ? scale : 1.0D;
    }

    public static int revision(final UUID id) {
        return id == null ? 0 : REVISIONS.getOrDefault(id, 0);
    }

    public static Region consumePending(final UUID id) {
        if (id == null) return Region.FULL;
        final Region pending = PENDING.remove(id);
        return pending == null ? Region.FULL : pending;
    }

    public static boolean invalidateBlock(final SubLevel subLevel, final int x, final int y, final int z) {
        if (subLevel == null) return false;
        final UUID id = subLevel.getUniqueId();
        if (id == null) return false;

        if (!clientSide(subLevel)) CLIENT_ENTRIES.remove(id);

        final Entry entry = entriesFor(subLevel).get(id);
        if (entry == null || entry.source == null || entry.shape == null) {
            invalidate(id);
            return true;
        }

        final BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null || !entry.source.covers(bounds)) {
            invalidate(id);
            return true;
        }

        if (entry.pending.size() >= MAX_PENDING_EDITS) {
            invalidate(id);
            return true;
        }
        entry.pending.add(new Edit(x, y, z));

        bump(subLevel, id, Region.tile(entry.source, entry.shape.tileOf(x, y, z),
                entry.shape.level().quantum()));
        return true;
    }

    private record Edit(int x, int y, int z) {}

    private static final int MAX_PENDING_EDITS = 512;

    private static boolean applyPendingEdits(final SubLevel subLevel, final UUID id, final Entry entry) {
        if (entry.pending.isEmpty()) return true;

        final List<Edit> edits = List.copyOf(entry.pending);
        entry.pending.clear();

        final Set<PlotShape.TileKey> dirty = new HashSet<>();
        Region region = null;
        for (final Edit edit : edits) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        final int x = edit.x() + dx, y = edit.y() + dy, z = edit.z() + dz;
                        if (!entry.source.refreshBlock(subLevel.getLevel(), x, y, z)) continue;
                        final PlotShape.TileKey tile = entry.shape.tileOf(x, y, z);
                        if (!dirty.add(tile)) continue;
                        final Region touched = Region.tile(
                                entry.source, tile, entry.shape.level().quantum());
                        region = region == null ? touched : region.union(touched);
                    }
                }
            }
        }

        if (dirty.isEmpty()) return true;

        final PlotShape remeshed = entry.shape.remesh(entry.source, dirty);
        if (remeshed == null) {
            invalidate(id);
            return false;
        }

        entry.shape = remeshed;
        entry.budgetScale = Double.NaN;
        bump(subLevel, id, region);
        return true;
    }

    public static void invalidate(final UUID id) {
        if (id == null) return;

        CLIENT_ENTRIES.remove(id);
        if (SERVER_ENTRIES.remove(id) != null) {
            REVISIONS.merge(id, 1, Integer::sum);
            PENDING.put(id, Region.FULL);
        }
    }

    public static void invalidate(final SubLevel subLevel) {
        if (subLevel != null) invalidate(subLevel.getUniqueId());
    }

    public static void forget(final UUID id) {
        if (id == null) return;
        CLIENT_ENTRIES.remove(id);
        SERVER_ENTRIES.remove(id);
        REVISIONS.remove(id);
        PENDING.remove(id);
        ColliderDetail.forget(id);
    }

    private PlotShapeCache() {}
}
