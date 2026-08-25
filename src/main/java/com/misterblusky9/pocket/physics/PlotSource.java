package com.misterblusky9.pocket.physics;

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
import java.util.List;
import java.util.Map;

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

    private final Map<Integer, List<PlotShape.Box>> partials;

    private PlotSource(
            final int quantum,
            final int blockMinX, final int blockMinY, final int blockMinZ,
            final int blockMaxX, final int blockMaxY, final int blockMaxZ,
            final int cellMinX, final int cellMinY, final int cellMinZ,
            final int cellsX, final int cellsY, final int cellsZ
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

    public boolean covers(final BoundingBox3ic bounds) {
        return bounds != null
                && bounds.minX() == this.blockMinX && bounds.minY() == this.blockMinY
                && bounds.minZ() == this.blockMinZ && bounds.maxX() == this.blockMaxX
                && bounds.maxY() == this.blockMaxY && bounds.maxZ() == this.blockMaxZ;
    }

    public byte at(final int cellX, final int cellY, final int cellZ) {
        final int x = cellX - this.cellMinX;
        final int y = cellY - this.cellMinY;
        final int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return EMPTY;
        }
        return this.cells[index(x, y, z)];
    }

    public List<PlotShape.Box> partialsAt(final int cellX, final int cellY, final int cellZ) {
        if (this.partials.isEmpty()) return List.of();
        final int x = cellX - this.cellMinX;
        final int y = cellY - this.cellMinY;
        final int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return List.of();
        }
        final List<PlotShape.Box> found = this.partials.get(index(x, y, z));
        return found == null ? List.of() : found;
    }

    public boolean isEmpty() {
        for (final byte cell : this.cells) {
            if (cell != EMPTY) return false;
        }
        return true;
    }

    public static PlotSource of(final BlockGetter level, final BoundingBox3ic bounds) {
        if (level == null || bounds == null) return null;

        final int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        final int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        if (maxX < minX || maxY < minY || maxZ < minZ) return null;

        final LevelReader reader = level instanceof final LevelReader candidate ? candidate : null;
        final long volume = saturatedVolume(
                (long) maxX - minX + 1L, (long) maxY - minY + 1L, (long) maxZ - minZ + 1L);

        final long sectionReads = reader == null
                ? 0L
                : saturatedProduct(countNonEmptySections(reader, minX, minY, minZ, maxX, maxY, maxZ), 4096L);

        final boolean sectionsUsable = sectionReads > 0L;
        final long reads = sectionsUsable ? Math.min(volume, sectionReads) : volume;

        int quantum = 1;
        if (reads > MAX_PRECISE_READS) {
            if (!sectionsUsable) return null;
            quantum = SECTION_QUANTUM;
        }
        while (cellCount(minX, minY, minZ, maxX, maxY, maxZ, quantum) > MAX_CELLS) {
            quantum <<= 1;
            if (quantum <= 0) return null;
        }

        final int cellMinX = Math.floorDiv(minX, quantum);
        final int cellMinY = Math.floorDiv(minY, quantum);
        final int cellMinZ = Math.floorDiv(minZ, quantum);
        final PlotSource source = new PlotSource(
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

        return source.isEmpty() ? null : source;
    }

    public boolean refreshBlock(final BlockGetter level, final int blockX, final int blockY, final int blockZ) {
        if (level == null) return false;
        if (blockX < this.blockMinX || blockX > this.blockMaxX
                || blockY < this.blockMinY || blockY > this.blockMaxY
                || blockZ < this.blockMinZ || blockZ > this.blockMaxZ) {
            return false;
        }

        final int cellX = Math.floorDiv(blockX, this.quantum);
        final int cellY = Math.floorDiv(blockY, this.quantum);
        final int cellZ = Math.floorDiv(blockZ, this.quantum);
        final int x = cellX - this.cellMinX;
        final int y = cellY - this.cellMinY;
        final int z = cellZ - this.cellMinZ;
        if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
            return false;
        }

        final int index = index(x, y, z);
        final byte before = this.cells[index];
        final List<PlotShape.Box> partialsBefore = this.partials.get(index);

        if (this.quantum >= SECTION_QUANTUM) {
            final byte after = level instanceof final LevelReader reader
                    && sectionHasBlocks(reader, cellX, cellY, cellZ, this.quantum)
                    ? FULL
                    : EMPTY;
            this.cells[index] = after;
            return after != before;
        }

        this.cells[index] = EMPTY;
        this.partials.remove(index);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int firstX = Math.max(this.blockMinX, cellX * this.quantum);
        final int firstY = Math.max(this.blockMinY, cellY * this.quantum);
        final int firstZ = Math.max(this.blockMinZ, cellZ * this.quantum);
        final int lastX = Math.min(this.blockMaxX, firstX + this.quantum - 1);
        final int lastY = Math.min(this.blockMaxY, firstY + this.quantum - 1);
        final int lastZ = Math.min(this.blockMaxZ, firstZ + this.quantum - 1);
        for (int bx = firstX; bx <= lastX; bx++) {
            for (int by = firstY; by <= lastY; by++) {
                for (int bz = firstZ; bz <= lastZ; bz++) {
                    pos.set(bx, by, bz);
                    accept(level, pos, index);
                }
            }
        }

        return this.cells[index] != before
                || !equalBoxes(partialsBefore, this.partials.get(index));
    }

    public int cellOf(final int block) {
        return Math.floorDiv(block, this.quantum);
    }

    private void scanBlocks(final BlockGetter level, final LevelReader reader) {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

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
                    final int baseX = sectionX << 4, baseY = sectionY << 4, baseZ = sectionZ << 4;
                    final int firstX = Math.max(this.blockMinX, baseX);
                    final int firstY = Math.max(this.blockMinY, baseY);
                    final int firstZ = Math.max(this.blockMinZ, baseZ);
                    final int lastX = Math.min(this.blockMaxX, baseX + 15);
                    final int lastY = Math.min(this.blockMaxY, baseY + 15);
                    final int lastZ = Math.min(this.blockMaxZ, baseZ + 15);

                    for (int x = firstX; x <= lastX; x++) {
                        for (int y = firstY; y <= lastY; y++) {
                            for (int z = firstZ; z <= lastZ; z++) {
                                final BlockState state = section.getBlockState(x - baseX, y - baseY, z - baseZ);
                                if (state.isAir()) continue;
                                pos.set(x, y, z);
                                accept(level, pos, state, indexOfBlock(x, y, z));
                            }
                        }
                    }
                });
    }

    private void scanSections(final LevelReader reader) {
        if (reader == null) return;
        forEachSection(reader, this.blockMinX, this.blockMinY, this.blockMinZ,
                this.blockMaxX, this.blockMaxY, this.blockMaxZ,
                (sectionX, sectionY, sectionZ, section) -> {
                    final int x = Math.floorDiv(sectionX << 4, this.quantum) - this.cellMinX;
                    final int y = Math.floorDiv(sectionY << 4, this.quantum) - this.cellMinY;
                    final int z = Math.floorDiv(sectionZ << 4, this.quantum) - this.cellMinZ;
                    if (x < 0 || y < 0 || z < 0 || x >= this.cellsX || y >= this.cellsY || z >= this.cellsZ) {
                        return;
                    }
                    this.cells[index(x, y, z)] = FULL;
                });
    }

    private void accept(final BlockGetter level, final BlockPos.MutableBlockPos pos, final int index) {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        accept(level, pos, state, index);
    }

    private void accept(
            final BlockGetter level,
            final BlockPos.MutableBlockPos pos,
            final BlockState state,
            final int index
    ) {
        final VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return;

        if (Block.isShapeFullBlock(collision)) {
            this.cells[index] = FULL;
            return;
        }
        if (this.quantum > 1) {
            this.cells[index] = FULL;
            return;
        }

        if (this.cells[index] != FULL) this.cells[index] = PARTIAL;
        final List<PlotShape.Box> boxes = this.partials.computeIfAbsent(index, ignored -> new ArrayList<>());
        for (final AABB part : collision.toAabbs()) {
            boxes.add(new PlotShape.Box(
                    pos.getX() + part.minX, pos.getY() + part.minY, pos.getZ() + part.minZ,
                    pos.getX() + part.maxX, pos.getY() + part.maxY, pos.getZ() + part.maxZ));
        }
    }

    private int indexOfBlock(final int blockX, final int blockY, final int blockZ) {
        return index(
                Math.floorDiv(blockX, this.quantum) - this.cellMinX,
                Math.floorDiv(blockY, this.quantum) - this.cellMinY,
                Math.floorDiv(blockZ, this.quantum) - this.cellMinZ);
    }

    private int index(final int x, final int y, final int z) {
        return x + this.cellsX * (z + this.cellsZ * y);
    }

    private static boolean equalBoxes(final List<PlotShape.Box> left, final List<PlotShape.Box> right) {
        if (left == null || left.isEmpty()) return right == null || right.isEmpty();
        return left.equals(right);
    }

    private interface SectionVisitor {
        void visit(int sectionX, int sectionY, int sectionZ, LevelChunkSection section);
    }

    private static void forEachSection(
            final LevelReader reader,
            final int minX, final int minY, final int minZ,
            final int maxX, final int maxY, final int maxZ,
            final SectionVisitor visitor
    ) {
        final int firstChunkX = minX >> 4, lastChunkX = maxX >> 4;
        final int firstChunkZ = minZ >> 4, lastChunkZ = maxZ >> 4;
        final int firstSectionY = minY >> 4, lastSectionY = maxY >> 4;

        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                final ChunkAccess chunk = reader.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                final LevelChunkSection[] sections = chunk.getSections();

                for (int sectionY = firstSectionY; sectionY <= lastSectionY; sectionY++) {
                    final int slot = chunk.getSectionIndexFromSectionY(sectionY);
                    if (slot < 0 || slot >= sections.length) continue;
                    final LevelChunkSection section = sections[slot];
                    if (section == null || section.hasOnlyAir()) continue;
                    visitor.visit(chunkX, sectionY, chunkZ, section);
                }
            }
        }
    }

    private static boolean sectionHasBlocks(
            final LevelReader reader,
            final int cellX, final int cellY, final int cellZ,
            final int quantum
    ) {
        final int firstX = cellX * quantum, lastX = firstX + quantum - 1;
        final int firstY = cellY * quantum, lastY = firstY + quantum - 1;
        final int firstZ = cellZ * quantum, lastZ = firstZ + quantum - 1;
        final boolean[] found = { false };
        forEachSection(reader, firstX, firstY, firstZ, lastX, lastY, lastZ,
                (x, y, z, section) -> found[0] = true);
        return found[0];
    }

    private static long countNonEmptySections(
            final LevelReader reader,
            final int minX, final int minY, final int minZ,
            final int maxX, final int maxY, final int maxZ
    ) {
        final long[] count = { 0L };
        forEachSection(reader, minX, minY, minZ, maxX, maxY, maxZ,
                (x, y, z, section) -> count[0]++);
        return count[0];
    }

    private static long cellCount(
            final int minX, final int minY, final int minZ,
            final int maxX, final int maxY, final int maxZ,
            final int quantum
    ) {
        return saturatedVolume(
                (long) Math.floorDiv(maxX, quantum) - Math.floorDiv(minX, quantum) + 1L,
                (long) Math.floorDiv(maxY, quantum) - Math.floorDiv(minY, quantum) + 1L,
                (long) Math.floorDiv(maxZ, quantum) - Math.floorDiv(minZ, quantum) + 1L);
    }

    private static long saturatedVolume(final long x, final long y, final long z) {
        return saturatedProduct(saturatedProduct(x, y), z);
    }

    private static long saturatedProduct(final long left, final long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }
}
