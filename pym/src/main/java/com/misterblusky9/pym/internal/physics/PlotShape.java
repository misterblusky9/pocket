package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.SubLevelShape;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlotShape implements SubLevelShape {
    public static final int TILE = 16;

    public record TileKey(int x, int y, int z) {}

    private record Tile(List<Box> boxes, short[] materials) {
        private static final Tile EMPTY = new Tile(List.of(), new short[0]);

        private boolean isEmpty() {
            return this.boxes.isEmpty();
        }
    }

    private final ColliderDetail.Level level;
    private final PlotSource source;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<TileKey, Tile> tiles;
    private final List<Box> boxes;
    private final short[] materials;

    private PlotShape(
            ColliderDetail.Level level,
            PlotSource source,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            Map<TileKey, Tile> tiles
    ) {
        this.level = level;
        this.source = source;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.tiles = tiles;

        List<Box> flattened = new ArrayList<>();
        int count = 0;
        for (Tile tile : tiles.values()) count += tile.boxes().size();
        short[] flatMaterials = new short[count];
        for (Tile tile : tiles.values()) {
            System.arraycopy(tile.materials(), 0, flatMaterials, flattened.size(), tile.boxes().size());
            flattened.addAll(tile.boxes());
        }
        this.boxes = List.copyOf(flattened);
        this.materials = flatMaterials;
    }

    @Override
    public List<Box> boxes() {
        return this.boxes;
    }

    public short material(int boxIndex) {
        return this.materials[boxIndex];
    }

    public ColliderDetail.Level level() {
        return this.level;
    }

    public PlotSource source() {
        return this.source;
    }

    public int minX() { return this.minX; }
    public int minY() { return this.minY; }
    public int minZ() { return this.minZ; }
    public int maxX() { return this.maxX; }
    public int maxY() { return this.maxY; }
    public int maxZ() { return this.maxZ; }

    public boolean covers(BoundingBox3ic bounds) {
        return bounds != null
                && bounds.minX() == this.minX && bounds.minY() == this.minY && bounds.minZ() == this.minZ
                && bounds.maxX() == this.maxX && bounds.maxY() == this.maxY && bounds.maxZ() == this.maxZ;
    }

    public TileKey tileOf(int blockX, int blockY, int blockZ) {
        int span = this.level.quantum() * TILE;
        return new TileKey(
                Math.floorDiv(blockX, span),
                Math.floorDiv(blockY, span),
                Math.floorDiv(blockZ, span));
    }

    public static PlotShape mesh(PlotSource source, ColliderDetail.Level level) {
        if (source == null || level == null) return null;

        int span = level.quantum() * TILE;
        int firstTileX = Math.floorDiv(source.blockMinX(), span);
        int firstTileY = Math.floorDiv(source.blockMinY(), span);
        int firstTileZ = Math.floorDiv(source.blockMinZ(), span);
        int lastTileX = Math.floorDiv(source.blockMaxX(), span);
        int lastTileY = Math.floorDiv(source.blockMaxY(), span);
        int lastTileZ = Math.floorDiv(source.blockMaxZ(), span);

        Map<TileKey, Tile> tiles = new HashMap<>();
        for (int x = firstTileX; x <= lastTileX; x++) {
            for (int y = firstTileY; y <= lastTileY; y++) {
                for (int z = firstTileZ; z <= lastTileZ; z++) {
                    TileKey key = new TileKey(x, y, z);
                    Tile built = meshTile(source, level, key);
                    if (!built.isEmpty()) tiles.put(key, built);
                }
            }
        }

        if (tiles.isEmpty()) return null;
        return new PlotShape(
                level,
                source,
                source.blockMinX(), source.blockMinY(), source.blockMinZ(),
                source.blockMaxX(), source.blockMaxY(), source.blockMaxZ(),
                tiles);
    }

    public PlotShape remesh(PlotSource source, Collection<TileKey> dirty) {
        if (source == null || dirty == null || dirty.isEmpty()) return this;

        Map<TileKey, Tile> updated = new HashMap<>(this.tiles);
        for (TileKey key : dirty) {
            Tile built = meshTile(source, this.level, key);
            if (built.isEmpty()) updated.remove(key);
            else updated.put(key, built);
        }
        if (updated.isEmpty()) return null;

        return new PlotShape(
                this.level,
                source,
                source.blockMinX(), source.blockMinY(), source.blockMinZ(),
                source.blockMaxX(), source.blockMaxY(), source.blockMaxZ(),
                updated);
    }

    private static Tile meshTile(
            PlotSource source,
            ColliderDetail.Level level,
            TileKey tile
    ) {
        int quantum = level.quantum();
        int group = Math.max(1, quantum / source.quantum());
        int baseX = tile.x() * TILE, baseY = tile.y() * TILE, baseZ = tile.z() * TILE;

        boolean[] solid = new boolean[TILE * TILE * TILE];
        short[] material = new short[TILE * TILE * TILE];
        List<Box> boxes = new ArrayList<>();
        List<Short> boxMaterials = new ArrayList<>();
        boolean any = false;

        for (int i = 0; i < TILE; i++) {
            for (int j = 0; j < TILE; j++) {
                for (int k = 0; k < TILE; k++) {
                    if (!occupied(source, level, group, baseX + i, baseY + j, baseZ + k)) continue;
                    int index = tileIndex(i, j, k);
                    solid[index] = true;
                    material[index] = material(source, group, baseX + i, baseY + j, baseZ + k);
                    any = true;
                }
            }
        }

        if (level.exact()) {
            for (int i = 0; i < TILE; i++) {
                for (int j = 0; j < TILE; j++) {
                    for (int k = 0; k < TILE; k++) {
                        List<Box> partials = source.partialsAt(baseX + i, baseY + j, baseZ + k);
                        if (partials.isEmpty()) continue;
                        short partialMaterial = source.materialAt(baseX + i, baseY + j, baseZ + k);
                        boxes.addAll(partials);
                        for (int p = 0; p < partials.size(); p++) boxMaterials.add(partialMaterial);
                    }
                }
            }
        }

        if (!any) return tile(boxes, boxMaterials);

        boolean[] used = new boolean[TILE * TILE * TILE];
        for (int j = 0; j < TILE; j++) {
            for (int k = 0; k < TILE; k++) {
                for (int i = 0; i < TILE; i++) {
                    int start = tileIndex(i, j, k);
                    if (!solid[start] || used[start]) continue;
                    short want = material[start];

                    int width = 1;
                    while (i + width < TILE && free(solid, used, material, want, i + width, j, k)) width++;

                    int depth = 1;
                    depthLoop:
                    while (k + depth < TILE) {
                        for (int w = 0; w < width; w++) {
                            if (!free(solid, used, material, want, i + w, j, k + depth)) break depthLoop;
                        }
                        depth++;
                    }

                    int height = 1;
                    heightLoop:
                    while (j + height < TILE) {
                        for (int d = 0; d < depth; d++) {
                            for (int w = 0; w < width; w++) {
                                if (!free(solid, used, material, want, i + w, j + height, k + d)) break heightLoop;
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

                    double lowX = (baseX + i) * (double) quantum;
                    double lowY = (baseY + j) * (double) quantum;
                    double lowZ = (baseZ + k) * (double) quantum;
                    boxes.add(new Box(
                            Math.max(lowX, source.blockMinX()),
                            Math.max(lowY, source.blockMinY()),
                            Math.max(lowZ, source.blockMinZ()),
                            Math.min(lowX + width * (double) quantum, source.blockMaxX() + 1.0D),
                            Math.min(lowY + height * (double) quantum, source.blockMaxY() + 1.0D),
                            Math.min(lowZ + depth * (double) quantum, source.blockMaxZ() + 1.0D)));
                    boxMaterials.add(want);
                }
            }
        }

        return tile(boxes, boxMaterials);
    }

    private static Tile tile(List<Box> boxes, List<Short> boxMaterials) {
        if (boxes.isEmpty()) return Tile.EMPTY;
        short[] materials = new short[boxMaterials.size()];
        for (int i = 0; i < materials.length; i++) materials[i] = boxMaterials.get(i);
        return new Tile(List.copyOf(boxes), materials);
    }

    private static short material(
            PlotSource source,
            int group,
            int cellX, int cellY, int cellZ
    ) {
        if (group == 1) return source.materialAt(cellX, cellY, cellZ);

        ColliderMaterial.Tally tally = new ColliderMaterial.Tally();
        int firstX = cellX * group, firstY = cellY * group, firstZ = cellZ * group;
        for (int x = 0; x < group; x++) {
            for (int y = 0; y < group; y++) {
                for (int z = 0; z < group; z++) {
                    if (source.at(firstX + x, firstY + y, firstZ + z) == PlotSource.EMPTY) continue;
                    tally.add(source.materialAt(firstX + x, firstY + y, firstZ + z), 1.0D);
                }
            }
        }
        return tally.winner();
    }

    private static boolean occupied(
            PlotSource source,
            ColliderDetail.Level level,
            int group,
            int cellX, int cellY, int cellZ
    ) {
        if (group == 1) {
            byte state = source.at(cellX, cellY, cellZ);
            return level.exact() ? state == PlotSource.FULL : state != PlotSource.EMPTY;
        }

        int firstX = cellX * group, firstY = cellY * group, firstZ = cellZ * group;
        for (int x = 0; x < group; x++) {
            for (int y = 0; y < group; y++) {
                for (int z = 0; z < group; z++) {
                    if (source.at(firstX + x, firstY + y, firstZ + z) != PlotSource.EMPTY) return true;
                }
            }
        }
        return false;
    }

    @Override
    public Vector3d closestPointToRay(
            Vector3dc from, Vector3dc direction, double maxAlong
    ) {
        Vector3d best = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        Vector3d onRay = new Vector3d();
        Vector3d onBox = new Vector3d();

        for (Box box : this.boxes) {
            onBox.set(
                    (box.minX() + box.maxX()) * 0.5D,
                    (box.minY() + box.maxY()) * 0.5D,
                    (box.minZ() + box.maxZ()) * 0.5D);

            for (int i = 0; i < 3; i++) {
                double along = Math.max(0.0D, Math.min(maxAlong,
                        new Vector3d(onBox).sub(from).dot(direction)));
                onRay.set(direction).mul(along).add(from);
                onBox.set(
                        clamp(onRay.x, box.minX(), box.maxX()),
                        clamp(onRay.y, box.minY(), box.maxY()),
                        clamp(onRay.z, box.minZ(), box.maxZ()));
            }

            double distanceSquared = onBox.distanceSquared(onRay);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = new Vector3d(onBox);
            }
        }

        return best;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static boolean free(
            boolean[] solid,
            boolean[] used,
            short[] material,
            short want,
            int i, int j, int k
    ) {
        int index = tileIndex(i, j, k);
        return solid[index] && !used[index] && material[index] == want;
    }

    private static int tileIndex(int i, int j, int k) {
        return i + TILE * (k + TILE * j);
    }
}
