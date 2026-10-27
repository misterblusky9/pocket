package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlotSource {
    public static final byte EMPTY = 0;

    public static final byte FULL = 1;

    public static final byte PARTIAL = 2;

    public static final int MAX_CELLS = 4_194_304;

    public static final int MAX_PRECISE_READS = 2_097_152;

    public static final int SECTION_QUANTUM = 16;

    private final int quantum;
    private final int blockMinX, blockMinY, blockMinZ;
    private final int blockMaxX, blockMaxY, blockMaxZ;
    private final int cellMinX, cellMinY, cellMinZ;
    private final int cellsX, cellsY, cellsZ;
    private final byte[] cells;
    private short[] materials;

    private final Map<Integer, List<PlotShape.Box>> partials;

    private PlotSource(
            int quantum,
            int blockMinX, int blockMinY, int blockMinZ,
            int blockMaxX, int blockMaxY, int blockMaxZ,
            int cellMinX, int cellMinY, int cellMinZ,
            int cellsX, int cellsY, int cellsZ
    ) {
        this.quantum = quantum;
        this.blockMinX = blockMinX;
        this.blockMinY = blockMinY;
        this.blockMinZ = blockMinZ;
        this.blockMaxX = blockMaxX;
        this.blockMaxY = blockMaxY;
        this.blockMaxZ = blockMaxZ;
        this.cellMinX = cellMinX;
        this.cellMinY = cellMinY;
        this.cellMinZ = cellMinZ;
        this.cellsX = cellsX;
        this.cellsY = cellsY;
        this.cellsZ = cellsZ;
        this.cells = new byte[cellsX * cellsY * cellsZ];

        this.partials = new HashMap<>();
    }

    public int quantum() {
        return this.quantum;
    }

    public int cellMinX() { return this.cellMinX; }
    public int cellMinY() { return this.cellMinY; }
    public int cellMinZ() { return this.cellMinZ; }
    public int cellsX() { return this.cellsX; }
    public int cellsY() { return this.cellsY; }
    public int cellsZ() { return this.cellsZ; }

    public int blockMinX() { return this.blockMinX; }
    public int blockMinY() { return this.blockMinY; }
    public int blockMinZ() { return this.blockMinZ; }
    public int blockMaxX() { return this.blockMaxX; }
    public int blockMaxY() { return this.blockMaxY; }
    public int blockMaxZ() { return this.blockMaxZ; }

    public boolean covers(BoundingBox3ic bounds) {
        return bounds != null
                && bounds.minX() == this.blockMinX && bounds.minY() == this.blockMinY
                && bounds.minZ() == this.blockMinZ && bounds.maxX() == this.blockMaxX
                && bounds.maxY() == this.blockMaxY && bounds.maxZ() == this.blockMaxZ;
    }

    public byte at(int cellX, int cellY, int cellZ) {
        int x = cellX - this.cellMinX;
        int y = cellY - this.cellMinY;
        int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return EMPTY;
        }
        return this.cells[index(x, y, z)];
    }

    public short materialAt(int cellX, int cellY, int cellZ) {
        if (this.materials == null) return ColliderMaterial.DEFAULT_ID;
        int x = cellX - this.cellMinX;
        int y = cellY - this.cellMinY;
        int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return ColliderMaterial.DEFAULT_ID;
        }
        return this.materials[index(x, y, z)];
    }

    public short surfaceMaterial(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        ColliderMaterial.Tally exposed = new ColliderMaterial.Tally();
        ColliderMaterial.Tally volume = new ColliderMaterial.Tally();
        int firstX = cellOf((int) Math.floor(minX)), lastX = cellOf((int) Math.ceil(maxX) - 1);
        int firstY = cellOf((int) Math.floor(minY)), lastY = cellOf((int) Math.ceil(maxY) - 1);
        int firstZ = cellOf((int) Math.floor(minZ)), lastZ = cellOf((int) Math.ceil(maxZ) - 1);
        for (int x = firstX; x <= lastX; x++) {
            double spanX = overlap(x, minX, maxX, this.blockMinX, this.blockMaxX);
            if (spanX <= 0.0D) continue;
            for (int y = firstY; y <= lastY; y++) {
                double spanY = overlap(y, minY, maxY, this.blockMinY, this.blockMaxY);
                if (spanY <= 0.0D) continue;
                for (int z = firstZ; z <= lastZ; z++) {
                    if (at(x, y, z) == EMPTY) continue;
                    double spanZ = overlap(z, minZ, maxZ, this.blockMinZ, this.blockMaxZ);
                    if (spanZ <= 0.0D) continue;
                    double weight = spanX * spanY * spanZ;
                    short material = materialAt(x, y, z);
                    volume.add(material, weight);
                    int open = openFaces(x, y, z);
                    if (open > 0) exposed.add(material, weight * open);
                }
            }
        }
        return exposed.isEmpty() ? volume.winner() : exposed.winner();
    }

    private double overlap(int cell, double min, double max, int blockMin, int blockMax) {
        double low = Math.max(min, Math.max((double) cell * this.quantum, blockMin));
        double high = Math.min(max, Math.min((double) (cell + 1) * this.quantum, blockMax + 1.0D));
        return high - low;
    }

    private int openFaces(int cellX, int cellY, int cellZ) {
        int open = 0;
        if (at(cellX - 1, cellY, cellZ) != FULL) open++;
        if (at(cellX + 1, cellY, cellZ) != FULL) open++;
        if (at(cellX, cellY - 1, cellZ) != FULL) open++;
        if (at(cellX, cellY + 1, cellZ) != FULL) open++;
        if (at(cellX, cellY, cellZ - 1) != FULL) open++;
        if (at(cellX, cellY, cellZ + 1) != FULL) open++;
        return open;
    }

    public List<PlotShape.Box> partialsAt(int cellX, int cellY, int cellZ) {
        if (this.partials.isEmpty()) return List.of();
        int x = cellX - this.cellMinX;
        int y = cellY - this.cellMinY;
        int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return List.of();
        }
        List<PlotShape.Box> found = this.partials.get(index(x, y, z));
        return found == null ? List.of() : found;
    }

    public boolean isEmpty() {
        for (byte cell : this.cells) {
            if (cell != EMPTY) return false;
        }
        return true;
    }

    public static PlotSource of(BlockGetter level, BoundingBox3ic bounds) {
        if (level == null || bounds == null) return null;

        int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        if (maxX < minX || maxY < minY || maxZ < minZ) return null;

        LevelReader reader = level instanceof LevelReader candidate ? candidate : null;
        long volume = saturatedVolume(
                (long) maxX - minX + 1L, (long) maxY - minY + 1L, (long) maxZ - minZ + 1L);

        long sectionReads = reader == null
                ? 0L
                : saturatedProduct(countNonEmptySections(reader, minX, minY, minZ, maxX, maxY, maxZ), 4096L);

        boolean sectionsUsable = sectionReads > 0L;
        long reads = sectionsUsable ? Math.min(volume, sectionReads) : volume;

        int quantum = 1;
        if (reads > MAX_PRECISE_READS) {
            if (!sectionsUsable) return null;
            quantum = SECTION_QUANTUM;
        }
        while (cellCount(minX, minY, minZ, maxX, maxY, maxZ, quantum) > MAX_CELLS) {
            quantum <<= 1;
            if (quantum <= 0) return null;
        }

        int cellMinX = Math.floorDiv(minX, quantum);
        int cellMinY = Math.floorDiv(minY, quantum);
        int cellMinZ = Math.floorDiv(minZ, quantum);
        PlotSource source = new PlotSource(
                quantum, minX, minY, minZ, maxX, maxY, maxZ,
                cellMinX, cellMinY, cellMinZ,
                Math.floorDiv(maxX, quantum) - cellMinX + 1,
                Math.floorDiv(maxY, quantum) - cellMinY + 1,
                Math.floorDiv(maxZ, quantum) - cellMinZ + 1);

        if (quantum >= SECTION_QUANTUM) {
            source.scanSections(reader);
        } else {
            source.scanBlocks(level, sectionsUsable ? reader : null);
        }
        if (quantum > 1) source.resolveCoarseMaterials(level, reader);

        return source.isEmpty() ? null : source;
    }

    public boolean refreshBlock(BlockGetter level, int blockX, int blockY, int blockZ) {
        if (level == null) return false;
        if (blockX < this.blockMinX || blockX > this.blockMaxX
                || blockY < this.blockMinY || blockY > this.blockMaxY
                || blockZ < this.blockMinZ || blockZ > this.blockMaxZ) {
            return false;
        }

        int cellX = Math.floorDiv(blockX, this.quantum);
        int cellY = Math.floorDiv(blockY, this.quantum);
        int cellZ = Math.floorDiv(blockZ, this.quantum);
        int x = cellX - this.cellMinX;
        int y = cellY - this.cellMinY;
        int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return false;
        }

        int index = index(x, y, z);
        byte before = this.cells[index];
        short materialBefore = materialAtIndex(index);
        List<PlotShape.Box> partialsBefore = this.partials.get(index);
        LevelReader reader = level instanceof LevelReader candidate ? candidate : null;

        if (this.quantum >= SECTION_QUANTUM) {
            byte after = reader != null
                    && sectionHasBlocks(reader, cellX, cellY, cellZ, this.quantum)
                    ? FULL
                    : EMPTY;
            this.cells[index] = after;
            resolveCellMaterial(level, reader, cellX, cellY, cellZ);
            return after != before || materialAtIndex(index) != materialBefore;
        }

        this.cells[index] = EMPTY;
        setMaterial(index, ColliderMaterial.DEFAULT_ID);
        this.partials.remove(index);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int firstX = Math.max(this.blockMinX, cellX * this.quantum);
        int firstY = Math.max(this.blockMinY, cellY * this.quantum);
        int firstZ = Math.max(this.blockMinZ, cellZ * this.quantum);
        int lastX = Math.min(this.blockMaxX, firstX + this.quantum - 1);
        int lastY = Math.min(this.blockMaxY, firstY + this.quantum - 1);
        int lastZ = Math.min(this.blockMaxZ, firstZ + this.quantum - 1);
        for (int bx = firstX; bx <= lastX; bx++) {
            for (int by = firstY; by <= lastY; by++) {
                for (int bz = firstZ; bz <= lastZ; bz++) {
                    pos.set(bx, by, bz);
                    accept(level, pos, index);
                }
            }
        }
        if (this.quantum > 1) resolveCellMaterial(level, reader, cellX, cellY, cellZ);

        return this.cells[index] != before
                || materialAtIndex(index) != materialBefore
                || !equalBoxes(partialsBefore, this.partials.get(index));
    }

    public int cellOf(int block) {
        return Math.floorDiv(block, this.quantum);
    }

    private void scanBlocks(BlockGetter level, LevelReader reader) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if (reader == null) {
            for (int x = this.blockMinX; x <= this.blockMaxX; x++) {
                for (int y = this.blockMinY; y <= this.blockMaxY; y++) {
                    for (int z = this.blockMinZ; z <= this.blockMaxZ; z++) {
                        pos.set(x, y, z);
                        accept(level, pos, indexOfBlock(x, y, z));
                    }
                }
            }
            return;
        }

        forEachSection(reader, this.blockMinX, this.blockMinY, this.blockMinZ,
                this.blockMaxX, this.blockMaxY, this.blockMaxZ,
                (sectionX, sectionY, sectionZ, section) -> {
                    int baseX = sectionX << 4, baseY = sectionY << 4, baseZ = sectionZ << 4;
                    int firstX = Math.max(this.blockMinX, baseX);
                    int firstY = Math.max(this.blockMinY, baseY);
                    int firstZ = Math.max(this.blockMinZ, baseZ);
                    int lastX = Math.min(this.blockMaxX, baseX + 15);
                    int lastY = Math.min(this.blockMaxY, baseY + 15);
                    int lastZ = Math.min(this.blockMaxZ, baseZ + 15);

                    for (int x = firstX; x <= lastX; x++) {
                        for (int y = firstY; y <= lastY; y++) {
                            for (int z = firstZ; z <= lastZ; z++) {
                                BlockState state = section.getBlockState(x - baseX, y - baseY, z - baseZ);
                                if (state.isAir()) continue;
                                pos.set(x, y, z);
                                accept(level, pos, state, indexOfBlock(x, y, z));
                            }
                        }
                    }
                });
    }

    private void scanSections(LevelReader reader) {
        if (reader == null) return;
        forEachSection(reader, this.blockMinX, this.blockMinY, this.blockMinZ,
                this.blockMaxX, this.blockMaxY, this.blockMaxZ,
                (sectionX, sectionY, sectionZ, section) -> {
                    int x = Math.floorDiv(sectionX << 4, this.quantum) - this.cellMinX;
                    int y = Math.floorDiv(sectionY << 4, this.quantum) - this.cellMinY;
                    int z = Math.floorDiv(sectionZ << 4, this.quantum) - this.cellMinZ;
                    if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
                        return;
                    }
                    this.cells[index(x, y, z)] = FULL;
                });
    }

    private void accept(BlockGetter level, BlockPos.MutableBlockPos pos, int index) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        accept(level, pos, state, index);
    }

    private void accept(
            BlockGetter level,
            BlockPos.MutableBlockPos pos,
            BlockState state,
            int index
    ) {
        VoxelShape collision = SableColliderShapes.of(level, state);
        if (collision.isEmpty()) return;
        if (this.quantum == 1) setMaterial(index, ColliderMaterial.id(state));

        if (Block.isShapeFullBlock(collision)) {
            this.cells[index] = FULL;
            return;
        }
        if (this.quantum > 1) {
            this.cells[index] = FULL;
            return;
        }

        if (this.cells[index] != FULL) this.cells[index] = PARTIAL;
        List<PlotShape.Box> boxes = this.partials.computeIfAbsent(index, ignored -> new ArrayList<>());
        for (AABB part : collision.toAabbs()) {
            boxes.add(new PlotShape.Box(
                    pos.getX() + part.minX, pos.getY() + part.minY, pos.getZ() + part.minZ,
                    pos.getX() + part.maxX, pos.getY() + part.maxY, pos.getZ() + part.maxZ));
        }
    }

    private int indexOfBlock(int blockX, int blockY, int blockZ) {
        return index(
                Math.floorDiv(blockX, this.quantum) - this.cellMinX,
                Math.floorDiv(blockY, this.quantum) - this.cellMinY,
                Math.floorDiv(blockZ, this.quantum) - this.cellMinZ);
    }

    private int index(int x, int y, int z) {
        return x + this.cellsX * (z + this.cellsZ * y);
    }

    private short materialAtIndex(int index) {
        return this.materials == null ? ColliderMaterial.DEFAULT_ID : this.materials[index];
    }

    private void setMaterial(int index, short material) {
        if (this.materials == null) {
            if (material == ColliderMaterial.DEFAULT_ID) return;
            this.materials = new short[this.cells.length];
        }
        this.materials[index] = material;
    }

    private void resolveCoarseMaterials(BlockGetter level, LevelReader reader) {
        if (reader == null) return;
        Set<Integer> candidates = new HashSet<>();
        forEachSection(reader, this.blockMinX, this.blockMinY, this.blockMinZ,
                this.blockMaxX, this.blockMaxY, this.blockMaxZ,
                (sectionX, sectionY, sectionZ, section) -> {
                    if (!section.maybeHas(ColliderMaterial::special)) return;
                    int firstX = Math.max(this.blockMinX, sectionX << 4);
                    int firstY = Math.max(this.blockMinY, sectionY << 4);
                    int firstZ = Math.max(this.blockMinZ, sectionZ << 4);
                    int lastX = Math.min(this.blockMaxX, (sectionX << 4) + 15);
                    int lastY = Math.min(this.blockMaxY, (sectionY << 4) + 15);
                    int lastZ = Math.min(this.blockMaxZ, (sectionZ << 4) + 15);
                    for (int x = cellOf(firstX); x <= cellOf(lastX); x++) {
                        for (int y = cellOf(firstY); y <= cellOf(lastY); y++) {
                            for (int z = cellOf(firstZ); z <= cellOf(lastZ); z++) {
                                candidates.add(index(x - this.cellMinX, y - this.cellMinY, z - this.cellMinZ));
                            }
                        }
                    }
                });

        for (int index : candidates) {
            int x = index % this.cellsX;
            int rest = index / this.cellsX;
            int z = rest % this.cellsZ;
            int y = rest / this.cellsZ;
            resolveCellMaterial(level, reader, x + this.cellMinX, y + this.cellMinY, z + this.cellMinZ);
        }
    }

    private void resolveCellMaterial(
            BlockGetter level,
            LevelReader reader,
            int cellX, int cellY, int cellZ
    ) {
        int x = cellX - this.cellMinX;
        int y = cellY - this.cellMinY;
        int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) return;
        int index = index(x, y, z);
        if (this.cells[index] == EMPTY) {
            setMaterial(index, ColliderMaterial.DEFAULT_ID);
            return;
        }

        int firstX = Math.max(this.blockMinX, cellX * this.quantum);
        int firstY = Math.max(this.blockMinY, cellY * this.quantum);
        int firstZ = Math.max(this.blockMinZ, cellZ * this.quantum);
        int lastX = Math.min(this.blockMaxX, cellX * this.quantum + this.quantum - 1);
        int lastY = Math.min(this.blockMaxY, cellY * this.quantum + this.quantum - 1);
        int lastZ = Math.min(this.blockMaxZ, cellZ * this.quantum + this.quantum - 1);

        if (reader != null) {
            boolean[] special = { false };
            forEachSection(reader, firstX, firstY, firstZ, lastX, lastY, lastZ,
                    (sectionX, sectionY, sectionZ, section) -> {
                        if (!special[0] && section.maybeHas(ColliderMaterial::special)) special[0] = true;
                    });
            if (!special[0]) {
                setMaterial(index, ColliderMaterial.DEFAULT_ID);
                return;
            }
        }

        ColliderMaterial.Tally tally = new ColliderMaterial.Tally();
        if (this.quantum >= SECTION_QUANTUM && reader != null) {
            forEachSection(reader, firstX, firstY, firstZ, lastX, lastY, lastZ,
                    (sectionX, sectionY, sectionZ, section) -> section.getStates().count((state, count) -> {
                        if (state.isAir() || SableColliderShapes.of(level, state).isEmpty()) return;
                        tally.add(ColliderMaterial.id(state), count);
                    }));
        } else {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int bx = firstX; bx <= lastX; bx++) {
                for (int by = firstY; by <= lastY; by++) {
                    for (int bz = firstZ; bz <= lastZ; bz++) {
                        BlockState state = level.getBlockState(pos.set(bx, by, bz));
                        if (state.isAir() || SableColliderShapes.of(level, state).isEmpty()) continue;
                        tally.add(ColliderMaterial.id(state), 1.0D);
                    }
                }
            }
        }
        setMaterial(index, tally.winner());
    }

    private static boolean equalBoxes(List<PlotShape.Box> left, List<PlotShape.Box> right) {
        if (left == null || left.isEmpty()) return right == null || right.isEmpty();
        return left.equals(right);
    }

    private interface SectionVisitor {
        void visit(int sectionX, int sectionY, int sectionZ, LevelChunkSection section);
    }

    private static void forEachSection(
            LevelReader reader,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            SectionVisitor visitor
    ) {
        int firstChunkX = minX >> 4, lastChunkX = maxX >> 4;
        int firstChunkZ = minZ >> 4, lastChunkZ = maxZ >> 4;
        int firstSectionY = minY >> 4, lastSectionY = maxY >> 4;

        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                ChunkAccess chunk = reader.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();

                for (int sectionY = firstSectionY; sectionY <= lastSectionY; sectionY++) {
                    int slot = chunk.getSectionIndexFromSectionY(sectionY);
                    if (slot < 0 || slot >= sections.length) continue;
                    LevelChunkSection section = sections[slot];
                    if (section == null || section.hasOnlyAir()) continue;
                    visitor.visit(chunkX, sectionY, chunkZ, section);
                }
            }
        }
    }

    private static boolean sectionHasBlocks(
            LevelReader reader,
            int cellX, int cellY, int cellZ,
            int quantum
    ) {
        int firstX = cellX * quantum, lastX = firstX + quantum - 1;
        int firstY = cellY * quantum, lastY = firstY + quantum - 1;
        int firstZ = cellZ * quantum, lastZ = firstZ + quantum - 1;
        boolean[] found = { false };
        forEachSection(reader, firstX, firstY, firstZ, lastX, lastY, lastZ,
                (x, y, z, section) -> found[0] = true);
        return found[0];
    }

    private static long countNonEmptySections(
            LevelReader reader,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        long[] count = { 0L };
        forEachSection(reader, minX, minY, minZ, maxX, maxY, maxZ,
                (x, y, z, section) -> count[0]++);
        return count[0];
    }

    private static long cellCount(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int quantum
    ) {
        return saturatedVolume(
                (long) Math.floorDiv(maxX, quantum) - Math.floorDiv(minX, quantum) + 1L,
                (long) Math.floorDiv(maxY, quantum) - Math.floorDiv(minY, quantum) + 1L,
                (long) Math.floorDiv(maxZ, quantum) - Math.floorDiv(minZ, quantum) + 1L);
    }

    private static long saturatedVolume(long x, long y, long z) {
        return saturatedProduct(saturatedProduct(x, y), z);
    }

    private static long saturatedProduct(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }
}
