package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlotShape {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    public static final int TILE = 16;

    public record TileKey(int x, int y, int z) {}

    private final ColliderDetail.Level level;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<TileKey, List<Box>> tiles;
    private final List<Box> boxes;

    private PlotShape(
            final ColliderDetail.Level level,
            final int minX, final int minY, final int minZ,
            final int maxX, final int maxY, final int maxZ,
            final Map<TileKey, List<Box>> tiles
    ) {
        this.level = level;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.tiles = tiles;

        final List<Box> flattened = new ArrayList<>();
        for (final List<Box> tile : tiles.values()) flattened.addAll(tile);
        this.boxes = List.copyOf(flattened);
    }

    public List<Box> boxes() {
        return this.boxes;
    }

    public ColliderDetail.Level level() {
        return this.level;
    }

    public int minX() { return this.minX; }
    public int minY() { return this.minY; }
    public int minZ() { return this.minZ; }
    public int maxX() { return this.maxX; }
    public int maxY() { return this.maxY; }
    public int maxZ() { return this.maxZ; }

    public boolean covers(final BoundingBox3ic bounds) {
        return bounds != null
                && bounds.minX() == this.minX && bounds.minY() == this.minY && bounds.minZ() == this.minZ
                && bounds.maxX() == this.maxX && bounds.maxY() == this.maxY && bounds.maxZ() == this.maxZ;
    }

    public TileKey tileOf(final int blockX, final int blockY, final int blockZ) {
        final int span = this.level.quantum() * TILE;
        return new TileKey(
                Math.floorDiv(blockX, span),
                Math.floorDiv(blockY, span),
                Math.floorDiv(blockZ, span));
    }

    public static PlotShape mesh(final PlotSource source, final ColliderDetail.Level level) {
        if (source == null || level == null) return null;

        final int span = level.quantum() * TILE;
        final int firstTileX = Math.floorDiv(source.blockMinX(), span);
        final int firstTileY = Math.floorDiv(source.blockMinY(), span);
        final int firstTileZ = Math.floorDiv(source.blockMinZ(), span);
        final int lastTileX = Math.floorDiv(source.blockMaxX(), span);
        final int lastTileY = Math.floorDiv(source.blockMaxY(), span);
        final int lastTileZ = Math.floorDiv(source.blockMaxZ(), span);

        final Map<TileKey, List<Box>> tiles = new HashMap<>();
        for (int x = firstTileX; x <= lastTileX; x++) {
            for (int y = firstTileY; y <= lastTileY; y++) {
                for (int z = firstTileZ; z <= lastTileZ; z++) {
                    final TileKey key = new TileKey(x, y, z);
                    final List<Box> built = meshTile(source, level, key);
                    if (!built.isEmpty()) tiles.put(key, built);
                }
            }
        }

        if (tiles.isEmpty()) return null;
        return new PlotShape(
                level,
                source.blockMinX(), source.blockMinY(), source.blockMinZ(),
                source.blockMaxX(), source.blockMaxY(), source.blockMaxZ(),
                tiles);
    }

    public PlotShape remesh(final PlotSource source, final Collection<TileKey> dirty) {
        if (source == null || dirty == null || dirty.isEmpty()) return this;

        final Map<TileKey, List<Box>> updated = new HashMap<>(this.tiles);
        for (final TileKey key : dirty) {
            final List<Box> built = meshTile(source, this.level, key);
            if (built.isEmpty()) updated.remove(key);
            else updated.put(key, built);
        }
        if (updated.isEmpty()) return null;

        return new PlotShape(
                this.level,
                source.blockMinX(), source.blockMinY(), source.blockMinZ(),
                source.blockMaxX(), source.blockMaxY(), source.blockMaxZ(),
                updated);
    }

    private static List<Box> meshTile(
            final PlotSource source,
            final ColliderDetail.Level level,
            final TileKey tile
    ) {
        final int quantum = level.quantum();
        final int group = Math.max(1, quantum / source.quantum());
        final int baseX = tile.x() * TILE, baseY = tile.y() * TILE, baseZ = tile.z() * TILE;

        final boolean[] solid = new boolean[TILE * TILE * TILE];
        final List<Box> boxes = new ArrayList<>();
        boolean any = false;

        for (int i = 0; i < TILE; i++) {
            for (int j = 0; j < TILE; j++) {
                for (int k = 0; k < TILE; k++) {
                    if (!occupied(source, level, group, baseX + i, baseY + j, baseZ + k)) continue;
                    solid[tileIndex(i, j, k)] = true;
                    any = true;
                }
            }
        }

        if (level.exact()) {
            for (int i = 0; i < TILE; i++) {
                for (int j = 0; j < TILE; j++) {
                    for (int k = 0; k < TILE; k++) {
                        boxes.addAll(source.partialsAt(baseX + i, baseY + j, baseZ + k));
                    }
                }
            }
        }

        if (!any) return boxes.isEmpty() ? List.of() : List.copyOf(boxes);

        final boolean[] used = new boolean[TILE * TILE * TILE];
        for (int j = 0; j < TILE; j++) {
            for (int k = 0; k < TILE; k++) {
                for (int i = 0; i < TILE; i++) {
                    final int start = tileIndex(i, j, k);
                    if (!solid[start] || used[start]) continue;

                    int width = 1;
                    while (i + width < TILE && free(solid, used, i + width, j, k)) width++;

                    int depth = 1;
                    depthLoop:
                    while (k + depth < TILE) {
                        for (int w = 0; w < width; w++) {
                            if (!free(solid, used, i + w, j, k + depth)) break depthLoop;
                        }
                        depth++;
                    }

                    int height = 1;
                    heightLoop:
                    while (j + height < TILE) {
                        for (int d = 0; d < depth; d++) {
                            for (int w = 0; w < width; w++) {
                                if (!free(solid, used, i + w, j + height, k + d)) break heightLoop;
                            }
                        }
                        height++;
                    }

                    for (int h = 0; h < height; h++) {
                        for (int d = 0; d < depth; d++) {
                            for (int w = 0; w < width; w++) {
                                used[tileIndex(i + w, j + h, k + d)] = true;
                            }
                        }
                    }

                    final double lowX = (baseX + i) * (double) quantum;
                    final double lowY = (baseY + j) * (double) quantum;
                    final double lowZ = (baseZ + k) * (double) quantum;
                    boxes.add(new Box(
                            Math.max(lowX, source.blockMinX()),
                            Math.max(lowY, source.blockMinY()),
                            Math.max(lowZ, source.blockMinZ()),
                            Math.min(lowX + width * (double) quantum, source.blockMaxX() + 1.0D),
                            Math.min(lowY + height * (double) quantum, source.blockMaxY() + 1.0D),
                            Math.min(lowZ + depth * (double) quantum, source.blockMaxZ() + 1.0D)));
                }
            }
        }

        return List.copyOf(boxes);
    }

    private static boolean occupied(
            final PlotSource source,
            final ColliderDetail.Level level,
            final int group,
            final int cellX, final int cellY, final int cellZ
    ) {
        if (group == 1) {
            final byte state = source.at(cellX, cellY, cellZ);
            return level.exact() ? state == PlotSource.FULL : state != PlotSource.EMPTY;
        }

        final int firstX = cellX * group, firstY = cellY * group, firstZ = cellZ * group;
        for (int x = 0; x < group; x++) {
            for (int y = 0; y < group; y++) {
                for (int z = 0; z < group; z++) {
                    if (source.at(firstX + x, firstY + y, firstZ + z) != PlotSource.EMPTY) return true;
                }
            }
        }
        return false;
    }

    public Vector3d closestPointToRay(
            final Vector3d from, final Vector3d direction, final double maxAlong
    ) {
        Vector3d best = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        final Vector3d onRay = new Vector3d();
        final Vector3d onBox = new Vector3d();

        for (final Box box : this.boxes) {
            onBox.set(
                    (box.minX() + box.maxX()) * 0.5D,
                    (box.minY() + box.maxY()) * 0.5D,
                    (box.minZ() + box.maxZ()) * 0.5D);

            for (int i = 0; i < 3; i++) {
                final double along = Math.max(0.0D, Math.min(maxAlong,
                        new Vector3d(onBox).sub(from).dot(direction)));
                onRay.set(direction).mul(along).add(from);
                onBox.set(
                        clamp(onRay.x, box.minX(), box.maxX()),
                        clamp(onRay.y, box.minY(), box.maxY()),
                        clamp(onRay.z, box.minZ(), box.maxZ()));
            }

            final double distanceSquared = onBox.distanceSquared(onRay);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = new Vector3d(onBox);
            }
        }

        return best;
    }

    private static double clamp(final double value, final double min, final double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static boolean free(final boolean[] solid, final boolean[] used, final int i, final int j, final int k) {
        final int index = tileIndex(i, j, k);
        return solid[index] && !used[index];
    }

    private static int tileIndex(final int i, final int j, final int k) {
        return i + TILE * (k + TILE * j);
    }
}
