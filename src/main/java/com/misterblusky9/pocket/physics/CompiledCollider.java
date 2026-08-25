package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.physics.ColliderGeometry.SectionKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CompiledCollider {
    public enum Mode {
        DETAILED,
        PRISM_FALLBACK
    }

    public record Bounds(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {}

    public record Cell(
            int x,
            int y,
            int z,
            int localIndex,
            ColliderShapeKey shape,
            boolean approximate
    ) {
        public Cell {
            if (shape == null || shape.faces().isEmpty()) {
                throw new IllegalArgumentException("compiled collider cell requires a non-empty shape");
            }
        }

        public SectionKey sectionKey() {
            return new SectionKey(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
        }
    }

    public static final class Section {
        private final SectionKey key;
        private final List<Cell> cells;
        private final Map<Integer, Cell> byIndex;
        private final Set<ColliderShapeKey> uniqueShapes;
        private final long fingerprint;

        public Section(final SectionKey key, final List<Cell> cells) {
            if (key == null) throw new IllegalArgumentException("section key");
            this.key = key;

            final ArrayList<Cell> ordered = new ArrayList<>(cells == null ? List.of() : cells);
            ordered.sort(java.util.Comparator.comparingInt(Cell::localIndex));

            final LinkedHashMap<Integer, Cell> index = new LinkedHashMap<>();
            final java.util.HashSet<ColliderShapeKey> shapes = new java.util.HashSet<>();
            long hash = 0xcbf29ce484222325L;
            for (final Cell cell : ordered) {
                if (!key.equals(cell.sectionKey())) {
                    throw new IllegalArgumentException("cell " + cell + " does not belong to " + key);
                }
                if (index.put(cell.localIndex(), cell) != null) {
                    throw new IllegalArgumentException("duplicate local cell index " + cell.localIndex());
                }
                shapes.add(cell.shape());
                hash ^= cell.localIndex();
                hash *= 0x100000001b3L;
                hash ^= cell.shape().hashCode();
                hash *= 0x100000001b3L;
            }
            this.cells = List.copyOf(ordered);
            this.byIndex = Collections.unmodifiableMap(index);
            this.uniqueShapes = Set.copyOf(shapes);
            this.fingerprint = hash;
        }

        public SectionKey key() { return this.key; }
        public List<Cell> cells() { return this.cells; }
        public Map<Integer, Cell> byIndex() { return this.byIndex; }
        public Set<ColliderShapeKey> uniqueShapes() { return this.uniqueShapes; }
        public long fingerprint() { return this.fingerprint; }
        public boolean isEmpty() { return this.cells.isEmpty(); }

        public boolean sameImage(final Section other) {
            return other != null
                    && this.fingerprint == other.fingerprint
                    && this.cells.equals(other.cells);
        }
    }

    public record GeometryKey(
            int bodyId,
            int shapeRevision,
            long scaleBits,
            long pivotX,
            long pivotY,
            long pivotZ,
            Bounds bounds,
            Mode mode
    ) {}

    private final UUID craftId;
    private final long generation;
    private final GeometryKey geometryKey;
    private final double scale;
    private final double pivotX;
    private final double pivotY;
    private final double pivotZ;
    private final Map<SectionKey, Section> sections;
    private final List<Cell> cells;
    private final int shapeCount;
    private final int approximateCellCount;

    public CompiledCollider(
            final UUID craftId,
            final long generation,
            final GeometryKey geometryKey,
            final double scale,
            final double pivotX,
            final double pivotY,
            final double pivotZ,
            final Map<SectionKey, Section> sections
    ) {
        if (craftId == null) throw new IllegalArgumentException("craftId");
        if (geometryKey == null) throw new IllegalArgumentException("geometryKey");
        this.craftId = craftId;
        this.generation = generation;
        this.geometryKey = geometryKey;
        this.scale = scale;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;

        final HashMap<SectionKey, Section> copy = new HashMap<>();
        final ArrayList<Cell> flat = new ArrayList<>();
        final java.util.HashSet<ColliderShapeKey> shapes = new java.util.HashSet<>();
        int approximateCells = 0;
        if (sections != null) {
            for (final Map.Entry<SectionKey, Section> entry : sections.entrySet()) {
                final Section section = entry.getValue();
                if (section == null || section.isEmpty()) continue;
                if (!entry.getKey().equals(section.key())) {
                    throw new IllegalArgumentException("section map key mismatch");
                }
                copy.put(entry.getKey(), section);
                flat.addAll(section.cells());
                for (final Cell cell : section.cells()) if (cell.approximate()) approximateCells++;
                shapes.addAll(section.uniqueShapes());
            }
        }
        flat.sort(java.util.Comparator
                .comparingInt(Cell::x)
                .thenComparingInt(Cell::z)
                .thenComparingInt(Cell::y));
        this.sections = Map.copyOf(copy);
        this.cells = List.copyOf(flat);
        this.shapeCount = shapes.size();
        this.approximateCellCount = approximateCells;
    }

    public CompiledCollider withMetadata(
            final GeometryKey geometryKey,
            final double scale,
            final double pivotX,
            final double pivotY,
            final double pivotZ
    ) {
        if (geometryKey == null) throw new IllegalArgumentException("geometryKey");
        if (geometryKey.bodyId() != this.bodyId()) {
            throw new IllegalArgumentException("cannot retag collider onto another body");
        }
        return new CompiledCollider(
                this.craftId, this.generation, geometryKey, scale,
                pivotX, pivotY, pivotZ,
                this.sections, this.cells, this.shapeCount, this.approximateCellCount);
    }

    private CompiledCollider(
            final UUID craftId,
            final long generation,
            final GeometryKey geometryKey,
            final double scale,
            final double pivotX,
            final double pivotY,
            final double pivotZ,
            final Map<SectionKey, Section> sections,
            final List<Cell> cells,
            final int shapeCount,
            final int approximateCellCount
    ) {
        this.craftId = craftId;
        this.generation = generation;
        this.geometryKey = geometryKey;
        this.scale = scale;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.sections = sections;
        this.cells = cells;
        this.shapeCount = shapeCount;
        this.approximateCellCount = approximateCellCount;
    }

    public UUID craftId() { return this.craftId; }
    public long generation() { return this.generation; }
    public GeometryKey geometryKey() { return this.geometryKey; }
    public int bodyId() { return this.geometryKey.bodyId(); }
    public int shapeRevision() { return this.geometryKey.shapeRevision(); }
    public Bounds bounds() { return this.geometryKey.bounds(); }
    public Mode mode() { return this.geometryKey.mode(); }
    public double scale() { return this.scale; }
    public double pivotX() { return this.pivotX; }
    public double pivotY() { return this.pivotY; }
    public double pivotZ() { return this.pivotZ; }
    public Map<SectionKey, Section> sections() { return this.sections; }
    public List<Cell> cells() { return this.cells; }
    public int occupiedCellCount() { return this.cells.size(); }
    public int shapeCount() { return this.shapeCount; }
    public int approximateCellCount() { return this.approximateCellCount; }
    public boolean exact() { return this.approximateCellCount == 0; }

    public String modeName() {
        return this.mode() == Mode.DETAILED ? "detailed" : "prism_fallback";
    }
}
