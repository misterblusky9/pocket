package com.misterblusky9.pym.internal.collision;

import com.misterblusky9.pym.api.SubLevelShape;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class SubLevelIndexedCollision {
    private static final Map<SubLevel, Mesh> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SubLevel, LongOpenHashSet> DIRTY = new WeakHashMap<>();
    private static final ThreadLocal<Selection> CURRENT = ThreadLocal.withInitial(Selection::new);
    private static final ThreadLocal<Entity> PROBER = new ThreadLocal<>();
    private static final ThreadLocal<String> REASON = ThreadLocal.withInitial(() -> "");
    private static final ThreadLocal<Boolean> PENDING_EXACT_FALLBACK = ThreadLocal.withInitial(() -> false);
    private static final int FINE = Integer.MAX_VALUE;

    public static void reset() {
        CURRENT.get().reset();
        PENDING_EXACT_FALLBACK.set(false);
    }

    public static boolean pendingExactFallback() {
        return PENDING_EXACT_FALLBACK.get();
    }

    public static boolean active() {
        return CURRENT.get().mesh != null;
    }

    public static void changed(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) return;
        synchronized (CACHE) {
            if (CACHE.containsKey(subLevel)) DIRTY.computeIfAbsent(subLevel, ignored -> new LongOpenHashSet())
                    .add(BlockPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        }
    }

    public static void changed(Level level, BlockPos pos, BlockState old, BlockState now) {
        if (!sameCollision(level, pos, old, now)) changed(level, pos);
    }

    private static boolean sameCollision(Level level, BlockPos pos, BlockState old, BlockState now) {
        if (old.getBlock() != now.getBlock()) return plain(level, pos, old) && plain(level, pos, now);
        VoxelShape a = old.getCollisionShape(level, pos), b = now.getCollisionShape(level, pos);
        return a == b || !Shapes.joinIsNotEmpty(a, b, BooleanOp.NOT_SAME);
    }

    private static boolean plain(Level level, BlockPos pos, BlockState state) {
        return !state.hasBlockEntity() && !(state.getBlock() instanceof ScaffoldingBlock)
                && !(state.getBlock() instanceof BlockSubLevelCollisionShape)
                && !state.is(dev.ryanhcode.sable.index.SableTags.BOUNCY)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    public static void reloaded(Level level, ChunkPos chunk) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, chunk.getWorldPosition());
        if (subLevel == null) return;
        synchronized (CACHE) {
            Mesh mesh = CACHE.get(subLevel);
            if (mesh == null || mesh.bounds == null) return;
            LongOpenHashSet dirty = DIRTY.computeIfAbsent(subLevel, ignored -> new LongOpenHashSet());
            for (int sy = mesh.bounds[1] >> 4; sy <= mesh.bounds[4] >> 4; sy++) dirty.add(BlockPos.asLong(chunk.x, sy, chunk.z));
        }
    }

    public static void forget(UUID id) {
        synchronized (CACHE) {
            CACHE.keySet().removeIf(subLevel -> id.equals(subLevel.getUniqueId()));
            DIRTY.keySet().removeIf(subLevel -> id.equals(subLevel.getUniqueId()));
        }
    }

    public static <T> T probing(Entity entity, Supplier<T> action) {
        Entity previous = PROBER.get();
        PROBER.set(entity);
        try {
            return action.get();
        } finally {
            PROBER.set(previous);
        }
    }

    private static Mesh meshFor(SubLevel subLevel, double scale, double width, double lowest, double standing, double step,
                                boolean wait) {
        PENDING_EXACT_FALLBACK.set(false);
        if (subLevel == null || subLevel.isRemoved() || subLevel.getLevel() == null
                || !Double.isFinite(scale) || scale <= 0.0 || !Double.isFinite(width)
                || !Double.isFinite(lowest) || !Double.isFinite(standing)) {
            REASON.set("Original: no usable sublevel or player size");
            return null;
        }

        BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null) {
            REASON.set("Original: the plot has no bounds");
            return null;
        }
        Mesh mesh;
        synchronized (CACHE) {
            mesh = CACHE.get(subLevel);
            LongOpenHashSet dirty = DIRTY.remove(subLevel);
            if (mesh == null || !mesh.covers(bounds) || dirty != null) {
                long begin = System.nanoTime();
                Mesh previous = mesh != null && mesh.covers(bounds) ? mesh : null;
                int occupied = sections(subLevel.getLevel(), bounds);
                Mesh old = mesh;
                mesh = build(subLevel.getLevel(), bounds, previous, dirty);
                if (previous != null) mesh.inherit(old);
                mesh.label = CollisionLog.id(subLevel);
                mesh.sectionCount = occupied;
                CACHE.put(subLevel, mesh);
                CollisionLog.built(subLevel, previous == null ? -1 : dirty.size(), occupied, mesh.summary(),
                        System.nanoTime() - begin);
            }
        }
        if (!mesh.occupied) {
            REASON.set("LOD0: empty craft");
            return mesh;
        }

        double w = width / scale;
        double s = Double.isFinite(step) && step > 0.0 ? step / scale : 0.0;
        Mesh result;
        if (mesh.ox1 - mesh.ox0 + 1 <= w * 0.5 && mesh.oz1 - mesh.oz0 + 1 <= w * 0.5
                && mesh.oy1 - mesh.oy0 + 1 <= s) {
            REASON.set("LOD4: the whole craft fits under the player");
            result = mesh.box();
        } else {
            double reach = Math.min(w, lowest / scale);
            if (reach < 16.0) {
                REASON.set(String.format(Locale.ROOT, "LOD0: reach %.1f craft blocks is under 16", reach));
                result = mesh;
            } else {
                boolean promote = s >= 1.0;
                Lod requested = new Lod(floorPow2(reach * (reach >= 64.0 ? 0.5 : 0.25)), promote,
                        promote ? Mth.smallestEncompassingPowerOfTwo(Mth.ceil(standing / scale) + 1) : 0, floorPow2(s), floorPow2(w));
                result = mesh.detail(requested, wait);
                PENDING_EXACT_FALLBACK.set(!wait && requested.grow >= 2 && result == mesh);
            }
        }
        REASON.set(REASON.get() + " | selected " + result.colliders() + " colliders | craft " + mesh.summary());
        return result;
    }

    private static int sections(Level level, BoundingBox3ic bounds) {
        int count = 0;
        for (int sx = bounds.minX() >> 4; sx <= bounds.maxX() >> 4; sx++) {
            for (int sz = bounds.minZ() >> 4; sz <= bounds.maxZ() >> 4; sz++) {
                LevelChunkSection[] chunk = level.getChunk(sx, sz).getSections();
                for (int sy = bounds.minY() >> 4; sy <= bounds.maxY() >> 4; sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index >= 0 && index < chunk.length && !chunk[index].hasOnlyAir()) count++;
                }
            }
        }
        return count;
    }

    private static int floorPow2(double value) {
        return value < 1.0 ? 0 : Integer.highestOneBit((int) Math.min(value, 1 << 20));
    }

    private static double standing(Entity entity) {
        return entity.getDimensions(Pose.STANDING).height();
    }

    private static double lowest(Entity entity) {
        return Math.min(entity.getBbHeight(), Math.min(entity.getDimensions(Pose.CROUCHING).height(),
                entity.getDimensions(Pose.SWIMMING).height()));
    }

    private static Mesh meshFor(SubLevel subLevel, double scale, Entity entity) {
        return meshFor(subLevel, scale, entity.getBbWidth(), lowest(entity), standing(entity), entity.maxUpStep(), false);
    }

    public static List<SubLevelShape.Box> outlines(SubLevel subLevel, double scale, double width,
                                                   double lowest, double standing, double step) {
        Mesh mesh = meshFor(subLevel, scale, width, lowest, standing, step, true);
        return mesh == null ? null : mesh.outlines(subLevel.getLevel());
    }

    public static Iterable<BlockPos> candidates(
            SubLevel subLevel, double scale, Entity entity,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        Mesh mesh = meshFor(subLevel, scale, entity);
        if (CollisionLog.watching(entity)) CollisionLog.choice(subLevel, entity, scale, REASON.get());
        if (mesh == null) return null;

        Selection selected = CURRENT.get();
        selected.mesh = mesh;
        if (minX > maxX || minY > maxY || minZ > maxZ) return List.of();
        long[] keys = collect(mesh, selected.shapes, selected.materials, minX, minY, minZ, maxX, maxY, maxZ);
        CollisionLog.candidates(keys.length);
        return positions(keys);
    }

    public static Probe probe(
            SubLevel subLevel, double scale, AABB bounds,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        Entity entity = PROBER.get();
        Mesh mesh = entity != null ? meshFor(subLevel, scale, entity)
                : meshFor(subLevel, scale, Math.min(bounds.getXsize(), bounds.getZsize()), 0.0, bounds.getYsize(), 0.0, false);
        if (mesh == null) return null;
        Map<Long, VoxelShape> shapes = new HashMap<>();
        Map<Long, Long> materials = new HashMap<>();
        if (minX > maxX || minY > maxY || minZ > maxZ) return new Probe(new long[0], shapes, materials);
        return new Probe(collect(mesh, shapes, materials, minX, minY, minZ, maxX, maxY, maxZ), shapes, materials);
    }

    private static long[] collect(Mesh mesh, Map<Long, VoxelShape> shapes, Map<Long, Long> materials,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        LongArrayList positions = new LongArrayList();
        LongArrayList blocks = new LongArrayList();
        LongOpenHashSet seenPositions = new LongOpenHashSet();
        Set<Region> seenRegions = new ReferenceOpenHashSet<>();
        List<Region> hits = new ArrayList<>();
        mesh.coarse(minX, minY, minZ, maxX, maxY, maxZ, seenRegions, hits);
        for (int sx = Math.floorDiv(minX, 16); sx <= Math.floorDiv(maxX, 16); sx++) {
            for (int sy = Math.floorDiv(minY, 16); sy <= Math.floorDiv(maxY, 16); sy++) {
                for (int sz = Math.floorDiv(minZ, 16); sz <= Math.floorDiv(maxZ, 16); sz++) {
                    Section section = mesh.sections.get(BlockPos.asLong(sx, sy, sz));
                    if (section == null) continue;
                    for (Region region : section.regions) {
                        if (seenRegions.add(region)) hits.add(region);
                    }
                    for (long key : section.blocks) {
                        int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                        if (x >= minX && y >= minY && z >= minZ && x <= maxX && y <= maxY && z <= maxZ
                                && seenPositions.add(key)) blocks.add(key);
                    }
                }
            }
        }
        for (Region region : hits) {
            if (!region.intersects(minX, minY, minZ, maxX, maxY, maxZ)) continue;
            int x = BlockPos.getX(region.anchor);
            int y = BlockPos.getY(region.anchor);
            int z = BlockPos.getZ(region.anchor);
            long key = BlockPos.asLong(x, y, z);
            VoxelShape shape = region.shape();
            VoxelShape previous = shapes.put(key, shape);
            if (previous != null) shapes.put(key, Shapes.or(previous, shape).optimize());
            if (region.material != key) materials.put(key, region.material);
            if (seenPositions.add(key)) positions.add(key);
        }
        positions.addAll(blocks);
        return positions.toLongArray();
    }

    public static int lod() {
        Mesh mesh = CURRENT.get().mesh;
        return mesh == null ? -1 : mesh.lod;
    }

    public static VoxelShape shape(BlockPos pos) {
        return CURRENT.get().shapes.get(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static BlockState state(LevelAccelerator accel, BlockPos pos) {
        Long material = CURRENT.get().materials.get(pos.asLong());
        return accel.getBlockState(material == null ? pos : BlockPos.of(material));
    }

    public static boolean proxy(BlockPos pos) {
        return CURRENT.get().shapes.containsKey(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static boolean support(LevelAccelerator accel, double[] xs, double[] zs,
                                  double lowY, double highY) {
        Selection selection = CURRENT.get();
        if (selection.mesh == null) {
            return supportBlocks(accel, xs, zs, lowY, highY);
        }
        Mesh mesh = selection.mesh;
        double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        double maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        Set<Region> seen = new ReferenceOpenHashSet<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<Region> large = new ArrayList<>();
        mesh.coarse(Mth.floor(minX), Mth.floor(lowY - 1.0), Mth.floor(minZ),
                Mth.floor(maxX), Mth.floor(highY + 0.02), Mth.floor(maxZ), seen, large);
        for (Region region : large) {
            if (region.maxY >= lowY - 0.02 && region.maxY <= highY + 0.02
                    && overlaps(xs, zs, region.minX, region.minZ, region.maxX, region.maxZ)) return true;
        }
        for (int sx = Math.floorDiv(Mth.floor(minX), 16); sx <= Math.floorDiv(Mth.floor(maxX), 16); sx++) {
            for (int sy = Math.floorDiv(Mth.floor(lowY - 1.0), 16); sy <= Math.floorDiv(Mth.floor(highY + 0.02), 16); sy++) {
                for (int sz = Math.floorDiv(Mth.floor(minZ), 16); sz <= Math.floorDiv(Mth.floor(maxZ), 16); sz++) {
                    Section section = mesh.sections.get(BlockPos.asLong(sx, sy, sz));
                    if (section == null) continue;
                    for (Region region : section.regions) {
                        if (seen.add(region) && region.maxY >= lowY - 0.02 && region.maxY <= highY + 0.02
                                && overlaps(xs, zs, region.minX, region.minZ, region.maxX, region.maxZ)) return true;
                    }
                    for (long key : section.blocks) {
                        if (!visited.add(key)) continue;
                        int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                        if (y > highY + 0.02 || y + 1 < lowY - 0.02
                                || x > maxX || x + 1 < minX || z > maxZ || z + 1 < minZ) continue;
                        if (supportBlock(accel, x, y, z, xs, zs, lowY, highY)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean supportBlocks(LevelAccelerator accel, double[] xs, double[] zs,
                                         double lowY, double highY) {
        double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        double maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        for (int x = Mth.floor(minX); x <= Mth.floor(maxX); x++) {
            for (int z = Mth.floor(minZ); z <= Mth.floor(maxZ); z++) {
                for (int y = Mth.floor(lowY - 1.0); y <= Mth.floor(highY + 0.02); y++) {
                    if (supportBlock(accel, x, y, z, xs, zs, lowY, highY)) return true;
                }
            }
        }
        return false;
    }

    private static boolean supportBlock(LevelAccelerator accel, int x, int y, int z,
                                        double[] xs, double[] zs, double lowY, double highY) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = accel.getBlockState(pos);
        if (state.isAir()) return false;
        for (AABB box : state.getCollisionShape(accel, pos).toAabbs()) {
            double top = y + box.maxY;
            if (top >= lowY - 0.02 && top <= highY + 0.02
                    && overlaps(xs, zs, x + box.minX, z + box.minZ, x + box.maxX, z + box.maxZ)) return true;
        }
        return false;
    }

    private static boolean overlaps(double[] xs, double[] zs,
                                    double minX, double minZ, double maxX, double maxZ) {
        double left = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double right = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double front = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        double back = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        if (left <= minX + 1.0E-6 || right >= maxX - 1.0E-6
                || front <= minZ + 1.0E-6 || back >= maxZ - 1.0E-6) return false;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            double axisX = -(zs[next] - zs[i]);
            double axisZ = xs[next] - xs[i];
            double smallest = Double.POSITIVE_INFINITY, largest = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < 4; j++) {
                double dot = xs[j] * axisX + zs[j] * axisZ;
                smallest = Math.min(smallest, dot);
                largest = Math.max(largest, dot);
            }
            double a = minX * axisX + minZ * axisZ, b = minX * axisX + maxZ * axisZ;
            double c = maxX * axisX + minZ * axisZ, d = maxX * axisX + maxZ * axisZ;
            double lower = Math.min(Math.min(a, b), Math.min(c, d));
            double upper = Math.max(Math.max(a, b), Math.max(c, d));
            if (largest <= lower + 1.0E-6 || smallest >= upper - 1.0E-6) return false;
        }
        return true;
    }

    private static Mesh build(BlockGetter level, BoundingBox3ic bounds, Mesh previous, LongOpenHashSet dirty) {
        Mesh mesh = new Mesh();
        mesh.parts = new HashMap<>();
        mesh.bounds = new int[]{bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()};
        for (int sx = bounds.minX() >> 4; sx <= bounds.maxX() >> 4; sx++) {
            for (int sy = bounds.minY() >> 4; sy <= bounds.maxY() >> 4; sy++) {
                for (int sz = bounds.minZ() >> 4; sz <= bounds.maxZ() >> 4; sz++) {
                    long key = BlockPos.asLong(sx, sy, sz);
                    Mesh part = previous != null && !dirty.contains(key) ? previous.parts.get(key)
                            : part(level, bounds, sx, sy, sz);
                    if (part == null || part.sections.isEmpty()) continue;
                    mesh.parts.put(key, part);
                    mesh.sections.putAll(part.sections);
                    mesh.regions.addAll(part.regions);
                    mesh.fullBlocks.addAll(part.fullBlocks);
                    mesh.otherBlocks.addAll(part.otherBlocks);
                    mesh.partialBlocks.addAll(part.partialBlocks);
                    mesh.floorBlocks.addAll(part.floorBlocks);
                    mesh.tallBlocks.addAll(part.tallBlocks);
                    mesh.specialBlocks.addAll(part.specialBlocks);
                    if (part.occupied) mesh.occupy(part.ox0, part.oy0, part.oz0, part.ox1, part.oy1, part.oz1, part.anchor);
                }
            }
        }
        return mesh;
    }

    private static Mesh part(BlockGetter level, BoundingBox3ic bounds, int sx, int sy, int sz) {
        Mesh mesh = new Mesh();
        int x0 = Math.max(sx << 4, bounds.minX()), y0 = Math.max(sy << 4, bounds.minY()), z0 = Math.max(sz << 4, bounds.minZ());
        int x1 = Math.min((sx << 4) + 15, bounds.maxX()), y1 = Math.min((sy << 4) + 15, bounds.maxY());
        int z1 = Math.min((sz << 4) + 15, bounds.maxZ());
        if (level instanceof Level world) {
            LevelChunkSection[] chunk = world.getChunk(sx, sz).getSections();
            int index = world.getSectionIndexFromSectionY(sy);
            if (index >= 0 && index < chunk.length && chunk[index].hasOnlyAir()) return mesh;
        }
        int nx = x1 - x0 + 1, ny = y1 - y0 + 1, nz = z1 - z0 + 1;
        boolean[] plain = new boolean[nx * ny * nz];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    boolean scaffolding = state.getBlock() instanceof ScaffoldingBlock;
                    boolean special = state.hasBlockEntity() || scaffolding
                            || state.getBlock() instanceof BlockSubLevelCollisionShape
                            || state.is(dev.ryanhcode.sable.index.SableTags.BOUNCY);
                    if (!special && state.isCollisionShapeFullBlock(level, pos)) {
                        plain[((y - y0) * nz + z - z0) * nx + x - x0] = true;
                        continue;
                    }
                    long key = BlockPos.asLong(x, y, z);
                    mesh.add(key, false);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty() && !scaffolding) continue;
                    if (special) {
                        mesh.specialBlocks.add(key);
                    } else {
                        AABB box = shape.bounds();
                        boolean inside = box.minX >= 0.0 && box.minY >= 0.0 && box.minZ >= 0.0
                                && box.maxX <= 1.0 && box.maxY <= 1.0 && box.maxZ <= 1.0;
                        (!inside ? mesh.tallBlocks : Block.isFaceFull(shape, Direction.DOWN)
                                ? mesh.floorBlocks : mesh.partialBlocks).add(key);
                    }
                    mesh.occupy(x, y, z, x, y, z, special ? Long.MAX_VALUE : key);
                }
            }
        }
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    if (!plain[(y * nz + z) * nx + x]) continue;
                    int ex = x + 1;
                    while (ex < nx && plain[(y * nz + z) * nx + ex]) ex++;
                    int ez = z + 1;
                    while (ez < nz && plain(plain, nx, nz, x, ex, y, y + 1, ez, ez + 1)) ez++;
                    int ey = y + 1;
                    while (ey < ny && plain(plain, nx, nz, x, ex, ey, ey + 1, z, ez)) ey++;
                    for (int yy = y; yy < ey; yy++) {
                        for (int zz = z; zz < ez; zz++) {
                            for (int xx = x; xx < ex; xx++) plain[(yy * nz + zz) * nx + xx] = false;
                        }
                    }
                    long key = BlockPos.asLong(x0 + x, y0 + y, z0 + z);
                    if ((ex - x) * (ey - y) * (ez - z) == 1) mesh.add(key, true);
                    else mesh.add(new Region(x0 + x, y0 + y, z0 + z, x0 + ex, y0 + ey, z0 + ez));
                    mesh.occupy(x0 + x, y0 + y, z0 + z, x0 + ex - 1, y0 + ey - 1, z0 + ez - 1, key);
                }
            }
        }
        return mesh;
    }

    private static boolean plain(boolean[] plain, int nx, int nz, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    if (!plain[(y * nz + z) * nx + x]) return false;
                }
            }
        }
        return true;
    }

    private static Iterable<BlockPos> positions(long[] keys) {
        return () -> new Iterator<>() {
            private int next;
            private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            @Override
            public boolean hasNext() {
                return next < keys.length;
            }
            @Override
            public BlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();
                long key = keys[next++];
                return pos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
            }
        };
    }

    public record Probe(long[] keys, Map<Long, VoxelShape> shapes, Map<Long, Long> materials) implements Iterable<BlockPos> {
        public VoxelShape shape(BlockPos pos) {
            return shapes.get(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }

        public BlockPos material(BlockPos pos) {
            Long material = materials.get(pos.asLong());
            return material == null ? pos : BlockPos.of(material);
        }

        @Override
        public Iterator<BlockPos> iterator() {
            return positions(keys).iterator();
        }
    }

    private static final class Region {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final long anchor, material;
        private volatile VoxelShape shape;

        Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, BlockPos.asLong(minX, minY, minZ));
        }

        Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, long material) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.anchor = BlockPos.asLong(minX, minY, minZ);
            this.material = material;
        }

        VoxelShape shape() {
            VoxelShape cached = shape;
            if (cached == null) {
                int x = BlockPos.getX(anchor), y = BlockPos.getY(anchor), z = BlockPos.getZ(anchor);
                cached = Shapes.box(minX - x, minY - y, minZ - z,
                        maxX - x, maxY - y, maxZ - z);
                shape = cached;
            }
            return cached;
        }

        boolean intersects(int x0, int y0, int z0, int x1, int y1, int z1) {
            return maxX > x0 && maxY > y0 && maxZ > z0
                    && minX <= x1 && minY <= y1 && minZ <= z1;
        }
    }

    private static final class Section {
        final List<Region> regions = new ArrayList<>();
        final LongArrayList blocks = new LongArrayList();
    }

    private static final class Mesh {
        final Map<Long, Section> sections = new HashMap<>();
        final List<Map<Long, List<Region>>> levels = new ArrayList<>();
        final List<Region> regions = new ArrayList<>();
        final LongArrayList fullBlocks = new LongArrayList();
        final LongArrayList otherBlocks = new LongArrayList();
        final Map<Lod, CompletableFuture<Mesh>> details = new HashMap<>();
        final Map<Lod, Mesh> stale = new HashMap<>();
        final Map<Lod, CompletableFuture<Mesh>> inflight = new HashMap<>();
        Map<Long, Mesh> parts;
        int[] bounds;
        String label = "?";
        int sectionCount;
        private List<SubLevelShape.Box> outlines;

        int colliders() {
            return regions.size() + fullBlocks.size() + otherBlocks.size();
        }

        void coarse(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<Region> seen, List<Region> out) {
            for (int level = 1; level <= levels.size(); level++) {
                Map<Long, List<Region>> cells = levels.get(level - 1);
                if (cells.isEmpty()) continue;
                int shift = 4 + level;
                for (int cx = minX >> shift; cx <= maxX >> shift; cx++) {
                    for (int cy = minY >> shift; cy <= maxY >> shift; cy++) {
                        for (int cz = minZ >> shift; cz <= maxZ >> shift; cz++) {
                            List<Region> list = cells.get(BlockPos.asLong(cx, cy, cz));
                            if (list == null) continue;
                            for (Region region : list) {
                                if (seen.add(region)) out.add(region);
                            }
                        }
                    }
                }
            }
        }

        String summary() {
            if (!occupied) return "empty";
            return (ox1 - ox0 + 1) + "x" + (oy1 - oy0 + 1) + "x" + (oz1 - oz0 + 1) + " occupied, " + sectionCount
                    + " sections, LOD0 " + colliders() + " colliders (" + regions.size() + " merged, "
                    + (partialBlocks.size() + floorBlocks.size() + tallBlocks.size()) + " partial, " + specialBlocks.size() + " special)";
        }

        boolean covers(BoundingBox3ic plot) {
            return bounds != null && bounds[0] == plot.minX() && bounds[1] == plot.minY() && bounds[2] == plot.minZ()
                    && bounds[3] == plot.maxX() && bounds[4] == plot.maxY() && bounds[5] == plot.maxZ();
        }

        synchronized List<SubLevelShape.Box> outlines(BlockGetter level) {
            if (outlines != null) return outlines;
            List<SubLevelShape.Box> boxes = new ArrayList<>(regions.size() + fullBlocks.size() + otherBlocks.size());
            for (Region region : regions) {
                boxes.add(new SubLevelShape.Box(region.minX, region.minY, region.minZ,
                        region.maxX, region.maxY, region.maxZ));
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (long key : fullBlocks) {
                int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                boxes.add(new SubLevelShape.Box(x, y, z, x + 1, y + 1, z + 1));
            }
            for (long key : otherBlocks) {
                int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir()) continue;
                for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
                    boxes.add(new SubLevelShape.Box(x + box.minX, y + box.minY, z + box.minZ,
                            x + box.maxX, y + box.maxY, z + box.maxZ));
                }
            }
            outlines = List.copyOf(boxes);
            return outlines;
        }
        final LongArrayList partialBlocks = new LongArrayList();
        final LongArrayList floorBlocks = new LongArrayList();
        final LongArrayList tallBlocks = new LongArrayList();
        final LongArrayList specialBlocks = new LongArrayList();
        int ox0 = Integer.MAX_VALUE, oy0 = Integer.MAX_VALUE, oz0 = Integer.MAX_VALUE;
        int ox1 = Integer.MIN_VALUE, oy1 = Integer.MIN_VALUE, oz1 = Integer.MIN_VALUE;
        long anchor = Long.MAX_VALUE;
        int lod;
        boolean occupied;
        private Mesh box;

        void occupy(int x0, int y0, int z0, int x1, int y1, int z1, long key) {
            occupied = true;
            ox0 = Math.min(ox0, x0);
            oy0 = Math.min(oy0, y0);
            oz0 = Math.min(oz0, z0);
            ox1 = Math.max(ox1, x1);
            oy1 = Math.max(oy1, y1);
            oz1 = Math.max(oz1, z1);
            if (anchor == Long.MAX_VALUE) anchor = key;
        }

        Section section(int sx, int sy, int sz) {
            return sections.computeIfAbsent(BlockPos.asLong(sx, sy, sz), ignored -> new Section());
        }

        void add(Region region) {
            regions.add(region);
            int extent = Math.max(region.maxX - region.minX, Math.max(region.maxY - region.minY, region.maxZ - region.minZ));
            if (extent > 16) {
                int level = 32 - Integer.numberOfLeadingZeros((extent - 1) >> 4);
                while (levels.size() < level) levels.add(new HashMap<>());
                Map<Long, List<Region>> cells = levels.get(level - 1);
                int shift = 4 + level;
                for (int cx = region.minX >> shift; cx <= (region.maxX - 1) >> shift; cx++) {
                    for (int cy = region.minY >> shift; cy <= (region.maxY - 1) >> shift; cy++) {
                        for (int cz = region.minZ >> shift; cz <= (region.maxZ - 1) >> shift; cz++) {
                            cells.computeIfAbsent(BlockPos.asLong(cx, cy, cz), ignored -> new ArrayList<>()).add(region);
                        }
                    }
                }
                return;
            }
            for (int sx = Math.floorDiv(region.minX, 16); sx <= Math.floorDiv(region.maxX - 1, 16); sx++) {
                for (int sy = Math.floorDiv(region.minY, 16); sy <= Math.floorDiv(region.maxY - 1, 16); sy++) {
                    for (int sz = Math.floorDiv(region.minZ, 16); sz <= Math.floorDiv(region.maxZ - 1, 16); sz++) {
                        section(sx, sy, sz).regions.add(region);
                    }
                }
            }
        }

        void add(long key, boolean full) {
            (full ? fullBlocks : otherBlocks).add(key);
            section(Math.floorDiv(BlockPos.getX(key), 16), Math.floorDiv(BlockPos.getY(key), 16),
                    Math.floorDiv(BlockPos.getZ(key), 16)).blocks.add(key);
        }

        synchronized Mesh box() {
            if (box == null) {
                box = new Mesh();
                box.lod = 4;
                box.add(new Region(ox0, oy0, oz0, ox1 + 1, oy1 + 1, oz1 + 1,
                        anchor != Long.MAX_VALUE ? anchor : specialBlocks.getLong(0)));
            }
            return box;
        }

        private long cells(Lod lod) {
            int fine = Math.min(lod.grow, FINE);
            int sx = ox1 - ox0 + 1, sy = oy1 - oy0 + 1, sz = oz1 - oz0 + 1;
            return (long) (sx + 2 * pad(fine, sx))
                    * (sy + 2 * pad(fine, sy))
                    * (sz + 2 * pad(fine, sz));
        }

        private static int pad(int grow, int size) {
            return (Math.min(grow, size) + 1) / 2;
        }

        synchronized Mesh detail(Lod lod, boolean wait) {
            if (floorBlocks.isEmpty()) lod = new Lod(lod.grow, false, 0, lod.riser, lod.footprint);
            if (lod.grow < 2 && !lod.promote) {
                REASON.set("LOD0: closing grow " + lod.grow + " is too small to simplify");
                return this;
            }
            Lod key = lod;
            CompletableFuture<Mesh> built = details.get(key);
            if (built == null) {
                CompletableFuture<Mesh> older = inflight.remove(key);
                if (older != null && !older.isDone() && !wait) {
                    inflight.put(key, older);
                    Mesh interim = interim(key);
                    REASON.set("LOD" + interim.lod + " while an older grow " + lod.grow + " build finishes");
                    return interim;
                }
                if (older != null) stale.put(key, older.join());
                built = CompletableFuture.supplyAsync(() -> timedDetail(key), Util.backgroundExecutor());
                details.put(key, built);
            }
            Mesh result = wait ? built.join() : built.isDone() ? built.getNow(null) : built.getNow(interim(key));
            REASON.set((built.isDone() ? "LOD" + result.lod : "LOD" + result.lod + " while grow " + lod.grow + " builds")
                    + " (grow " + lod.grow + ")");
            return result;
        }

        private Mesh interim(Lod requested) {
            Mesh exact = stale.get(requested);
            if (exact != null) return exact;
            Mesh best = null;
            int bestCount = colliders();
            int bestGrow = -1;
            for (Map.Entry<Lod, CompletableFuture<Mesh>> entry : details.entrySet()) {
                Lod available = entry.getKey();
                if (available.grow > requested.grow || available.grow < Math.max(2, requested.grow / 8)
                        || available.riser > requested.riser || available.footprint > requested.footprint
                        || (available.promote && !requested.promote)
                        || (available.promote && available.headroom > requested.headroom)) continue;
                CompletableFuture<Mesh> future = entry.getValue();
                if (!future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) continue;
                Mesh ready = future.getNow(null);
                if (ready != null && ready.lod > 0 && ready.colliders() < colliders()
                        && (best == null || ready.colliders() < bestCount
                        || (ready.colliders() == bestCount && available.grow > bestGrow))) {
                    best = ready;
                    bestCount = ready.colliders();
                    bestGrow = available.grow;
                }
            }
            return best == null ? this : best;
        }

        private Mesh timedDetail(Lod lod) {
            long begin = System.nanoTime();
            Mesh detail = buildDetail(lod);
            CollisionLog.detail(label, lod.grow, detail.lod, cells(lod), detail.colliders(), System.nanoTime() - begin);
            return detail;
        }

        synchronized void inherit(Mesh old) {
            synchronized (old) {
                stale.putAll(old.stale);
                for (Map<Lod, CompletableFuture<Mesh>> futures : List.of(old.inflight, old.details)) {
                    for (Map.Entry<Lod, CompletableFuture<Mesh>> entry : futures.entrySet()) {
                        Mesh ready = entry.getValue().getNow(null);
                        if (ready != null) stale.put(entry.getKey(), ready);
                        else inflight.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        private Mesh buildDetail(Lod lod) {
            int sx = ox1 - ox0 + 1, sy = oy1 - oy0 + 1, sz = oz1 - oz0 + 1;
            int fine = Math.min(lod.grow, FINE);
            int gx = Math.min(fine, sx), gy = Math.min(fine, sy), gz = Math.min(fine, sz);
            int px = pad(fine, sx), py = pad(fine, sy), pz = pad(fine, sz);
            Grid grid = new Grid(ox0 - px, oy0 - py, oz0 - pz, sx + 2 * px, sy + 2 * py, sz + 2 * pz);
            for (Region region : regions) {
                for (int y = region.minY; y < region.maxY; y++) {
                    for (int z = region.minZ; z < region.maxZ; z++) {
                        for (int x = region.minX; x < region.maxX; x++) grid.set(x, y, z, Grid.REAL);
                    }
                }
            }
            for (long key : fullBlocks) grid.set(key, Grid.REAL);
            if (lod.grow > fine) grid.coarse(Math.min(lod.grow, sx), Math.min(lod.grow, sy), Math.min(lod.grow, sz));
            if (fine > 1) grid.close(gx, gy, gz);
            for (long key : specialBlocks) grid.set(key, Grid.EXACT);
            for (long key : tallBlocks) grid.set(key, Grid.EXACT);
            for (long key : partialBlocks) {
                if (grid.at(key) != Grid.FILLED) grid.set(key, Grid.EXACT);
            }
            for (long key : floorBlocks) {
                if (grid.at(key) != Grid.FILLED) grid.set(key, Grid.EXACT);
            }
            if (lod.promote) {
                for (long key : floorBlocks) {
                    if (grid.at(key) == Grid.EXACT && grid.clear(key, lod.headroom)) grid.set(key, Grid.REAL);
                }
            }
            int shift = lod.grow >= 128 ? 1 : lod.grow >= 32 ? 2 : 3;
            int tolerance = lod.riser < 2 ? 0 : Math.min(Math.max(1, lod.grow >> shift), lod.riser >> 1);
            if (tolerance >= 1) grid.simplifySurface(tolerance, Math.min(8, Math.max(1, lod.footprint >> 3)),
                    Math.min(8, Math.max(2, lod.grow >> 4)), lod.footprint);

            Mesh result = new Mesh();
            result.lod = lod.grow >= 128 ? 3 : lod.grow >= 32 ? 2 : 1;
            grid.meshSlabs(result);
            for (long key : specialBlocks) result.add(key, false);
            for (long key : tallBlocks) result.add(key, false);
            for (long key : partialBlocks) {
                if (grid.at(key) == Grid.EXACT) result.add(key, false);
            }
            for (long key : floorBlocks) {
                if (grid.at(key) == Grid.EXACT) result.add(key, false);
            }
            return result;
        }
    }

    private record Lod(int grow, boolean promote, int headroom, int riser, int footprint) {}

    private static final class Grid {
        static final byte FREE = 0, REAL = 1, EXACT = 2, FILLED = 3;
        static final int[][] ORDERS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        final int bx, by, bz, nx, ny, nz;
        final byte[] cells;

        Grid(int bx, int by, int bz, int nx, int ny, int nz) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.cells = new byte[nx * ny * nz];
        }

        void set(int x, int y, int z, byte type) {
            cells[((y - by) * nz + (z - bz)) * nx + (x - bx)] = type;
        }

        void set(long key, byte type) {
            set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key), type);
        }

        void close(int gx, int gy, int gz) {
            boolean[] solid = new boolean[cells.length];
            boolean[] spare = new boolean[cells.length];
            for (int i = 0; i < cells.length; i++) solid[i] = cells[i] != FREE;
            int[][] passes = {
                    {0, -(gx / 2), gx - 1 - gx / 2, 1},
                    {1, -(gy / 2), gy - 1 - gy / 2, 1},
                    {2, -(gz / 2), gz - 1 - gz / 2, 1},
                    {0, -(gx - 1 - gx / 2), gx / 2, 0},
                    {1, -(gy - 1 - gy / 2), gy / 2, 0},
                    {2, -(gz - 1 - gz / 2), gz / 2, 0}
            };
            for (int[] pass : passes) {
                if (pass[1] == 0 && pass[2] == 0) continue;
                sweep(solid, spare, pass[0], pass[1], pass[2], pass[3] != 0);
                boolean[] swap = solid;
                solid = spare;
                spare = swap;
            }
            for (int i = 0; i < cells.length; i++) {
                if (solid[i] && cells[i] == FREE) cells[i] = FILLED;
            }
        }

        void coarse(int gx, int gy, int gz) {
            int fx = gx > FINE ? gx / 8 : 1, fy = gy > FINE ? gy / 8 : 1, fz = gz > FINE ? gz / 8 : 1;
            int cx = gx / fx, cy = gy / fy, cz = gz / fz;
            int px = 1 + cx / 2, py = 1 + cy / 2, pz = 1 + cz / 2;
            Grid low = new Grid(0, 0, 0, (nx - 1) / fx + 1 + 2 * px, (ny - 1) / fy + 1 + 2 * py, (nz - 1) / fz + 1 + 2 * pz);
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++) {
                        if (cells[(y * nz + z) * nx + x] != FREE) low.set(x / fx + px, y / fy + py, z / fz + pz, REAL);
                    }
                }
            }
            low.close(cx, cy, cz);
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++) {
                        int i = (y * nz + z) * nx + x;
                        if (cells[i] == FREE && low.cells[((y / fy + py) * low.nz + z / fz + pz) * low.nx + x / fx + px] == FILLED) {
                            cells[i] = FILLED;
                        }
                    }
                }
            }
        }

        private void sweep(boolean[] in, boolean[] out, int axis, int lo, int hi, boolean grow) {
            int n = axis == 0 ? nx : axis == 1 ? ny : nz;
            int stride = axis == 0 ? 1 : axis == 1 ? nx * nz : nx;
            int[] sum = new int[n + 1];
            for (int line = 0, lines = in.length / n; line < lines; line++) {
                int start = axis == 0 ? line * nx : axis == 1 ? line : (line / nx) * nx * nz + line % nx;
                for (int i = 0; i < n; i++) sum[i + 1] = sum[i] + (in[start + i * stride] ? 1 : 0);
                for (int i = 0; i < n; i++) {
                    int a = Math.max(0, i + lo), b = Math.min(n - 1, i + hi);
                    int count = a > b ? 0 : sum[b + 1] - sum[a];
                    out[start + i * stride] = grow ? count > 0 : count == hi - lo + 1;
                }
            }
        }

        boolean clear(long key, int headroom) {
            int column = (BlockPos.getZ(key) - bz) * nx + BlockPos.getX(key) - bx;
            int floor = BlockPos.getY(key) - by;
            for (int y = floor + 1; y <= floor + headroom && y < ny; y++) {
                if (cells[y * nx * nz + column] != FREE) return false;
            }
            return true;
        }

        void simplifySurface(int tolerance, int passes, int minDepth, int footprint) {
            int layer = nx * nz;
            int[] original = new int[layer];
            java.util.Arrays.fill(original, -1);
            for (int column = 0; column < layer; column++) {
                int y = ny - 1;
                while (y >= 0 && cells[y * layer + column] == FREE) y--;
                if (y < 0 || !boxable(y * layer + column)) continue;
                int depth = 0;
                while (depth < minDepth && y - depth >= 0 && boxable((y - depth) * layer + column)) depth++;
                if (depth == minDepth) original[column] = y + 1;
            }
            int[] heights = original.clone();
            int[] next = new int[layer];
            int[] neighbors = new int[9];
            int limit = Math.max(1, tolerance / 2);
            for (int pass = 0; pass < passes; pass++) {
                System.arraycopy(heights, 0, next, 0, layer);
                for (int z = 1; z < nz - 1; z++) {
                    for (int x = 1; x < nx - 1; x++) {
                        int index = z * nx + x, height = heights[index];
                        if (height < 0) continue;
                        int count = 0;
                        for (int zz = z - 1; zz <= z + 1; zz++) {
                            for (int xx = x - 1; xx <= x + 1; xx++) {
                                int h = heights[zz * nx + xx];
                                if (h < 0 || Math.abs(h - height) > tolerance) continue;
                                int i = count++;
                                while (i > 0 && neighbors[i - 1] > h) {
                                    neighbors[i] = neighbors[i - 1];
                                    i--;
                                }
                                neighbors[i] = h;
                            }
                        }
                        if (count >= 5) next[index] = Mth.clamp(neighbors[count / 2],
                                original[index] - limit, original[index] + limit);
                    }
                }
                int[] swap = heights;
                heights = next;
                next = swap;
            }
            int[] phases = new int[tolerance];
            for (int height : heights) {
                if (height >= 0) phases[Math.floorMod(height + by, tolerance)]++;
            }
            int phase = 0;
            for (int i = 1; i < phases.length; i++) {
                if (phases[i] > phases[phase]) phase = i;
            }
            for (int column = 0; column < layer; column++) {
                int from = original[column];
                if (from < 0) continue;
                int rounded = Math.floorDiv(heights[column] + by - phase + tolerance / 2, tolerance) * tolerance + phase - by;
                int to = Mth.clamp(rounded, from - limit, from + limit);
                if (to > from) {
                    for (int y = from; y < to && y < ny; y++) {
                        int i = y * layer + column;
                        if (cells[i] != FREE) break;
                        cells[i] = FILLED;
                    }
                } else if (to < from) {
                    for (int y = from - 1; y >= to && y >= 0; y--) {
                        int i = y * layer + column;
                        if (!boxable(i)) break;
                        cells[i] = FREE;
                    }
                }
            }
        }

        byte at(long key) {
            return cells[((BlockPos.getY(key) - by) * nz + (BlockPos.getZ(key) - bz)) * nx + (BlockPos.getX(key) - bx)];
        }

        private boolean boxable(int i) {
            return cells[i] == REAL || cells[i] == FILLED;
        }

        private record Footprint(int x0, int x1, int z0, int z1) {}

        private byte[] exposedFaces() {
            byte[] faces = new byte[cells.length];
            int layer = nx * nz;
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++) {
                        int i = (y * nz + z) * nx + x;
                        if (!boxable(i)) continue;
                        int bits = 0;
                        if (x == 0 || !boxable(i - 1)) bits |= 1;
                        if (x == nx - 1 || !boxable(i + 1)) bits |= 2;
                        if (y == 0 || !boxable(i - layer)) bits |= 4;
                        if (y == ny - 1 || !boxable(i + layer)) bits |= 8;
                        if (z == 0 || !boxable(i - nx)) bits |= 16;
                        if (z == nz - 1 || !boxable(i + nx)) bits |= 32;
                        faces[i] = (byte) bits;
                    }
                }
            }
            return faces;
        }

        private record Plate(int axis, int direction, int layer, int u0, int u1, int v0, int v1) {
            int area() { return (u1 - u0) * (v1 - v0); }
        }

        private List<Plate> surfacePlates(byte[] exposed, boolean transpose) {
            int[] dims = {nx, ny, nz};
            int[] xyz = new int[3];
            List<Plate> result = new ArrayList<>();
            for (int axis = 0; axis < 3; axis++) {
                int ua = (axis + 1) % 3, va = (axis + 2) % 3;
                int width = dims[transpose ? va : ua], height = dims[transpose ? ua : va];
                boolean[] mask = new boolean[width * height];
                for (int layer = 0; layer < dims[axis]; layer++) {
                    xyz[axis] = layer;
                    for (int sign = -1; sign <= 1; sign += 2) {
                        int bit = 1 << (axis * 2 + (sign > 0 ? 1 : 0));
                        for (int v = 0; v < height; v++) {
                            for (int u = 0; u < width; u++) {
                                xyz[ua] = transpose ? v : u;
                                xyz[va] = transpose ? u : v;
                                int i = (xyz[1] * nz + xyz[2]) * nx + xyz[0];
                                mask[v * width + u] = (exposed[i] & bit) != 0;
                            }
                        }
                        for (int v = 0; v < height; v++) {
                            for (int u = 0; u < width;) {
                                if (!mask[v * width + u]) { u++; continue; }
                                int endU = u + 1;
                                while (endU < width && mask[v * width + endU]) endU++;
                                int endV = v + 1;
                                outer: while (endV < height) {
                                    for (int w = u; w < endU; w++) {
                                        if (!mask[endV * width + w]) break outer;
                                    }
                                    endV++;
                                }
                                for (int row = v; row < endV; row++) {
                                    java.util.Arrays.fill(mask, row * width + u, row * width + endU, false);
                                }
                                if ((long) (endU - u) * (endV - v) >= 8) {
                                    result.add(transpose
                                            ? new Plate(axis, sign, layer, v, endV, u, endU)
                                            : new Plate(axis, sign, layer, u, endU, v, endV));
                                }
                                u = endU;
                            }
                        }
                    }
                }
            }
            return result;
        }

        private boolean plateFits(Plate plate, int layer, boolean[] remaining) {
            int[] xyz = new int[3];
            xyz[plate.axis] = layer;
            int ua = (plate.axis + 1) % 3, va = (plate.axis + 2) % 3;
            for (int v = plate.v0; v < plate.v1; v++) {
                xyz[va] = v;
                for (int u = plate.u0; u < plate.u1; u++) {
                    xyz[ua] = u;
                    if (!remaining[(xyz[1] * nz + xyz[2]) * nx + xyz[0]]) return false;
                }
            }
            return true;
        }

        private List<int[]> shellBoxes(List<Plate> plates, int priority) {
            boolean[] remaining = new boolean[cells.length];
            for (int i = 0; i < cells.length; i++) remaining[i] = boxable(i);
            List<Plate> order = new ArrayList<>(plates);
            order.sort(Comparator.comparingLong((Plate p) ->
                    (long) p.area() * (p.axis == priority ? 2 : 1)).reversed());
            List<int[]> boxes = new ArrayList<>();
            int[] dimensions = {nx, ny, nz}, xyz = new int[3];
            for (Plate plate : order) {
                if (!plateFits(plate, plate.layer, remaining)) continue;
                int far = plate.layer;
                int step = -plate.direction;
                while (far + step >= 0 && far + step < dimensions[plate.axis]
                        && plateFits(plate, far + step, remaining)) far += step;
                int[] box = new int[7];
                box[plate.axis] = Math.min(plate.layer, far);
                box[plate.axis + 3] = Math.max(plate.layer, far) + 1;
                int ua = (plate.axis + 1) % 3, va = (plate.axis + 2) % 3;
                box[ua] = plate.u0; box[ua + 3] = plate.u1;
                box[va] = plate.v0; box[va + 3] = plate.v1;
                box[6] = -1;
                for (int y = box[1]; y < box[4]; y++) {
                    xyz[1] = y;
                    for (int z = box[2]; z < box[5]; z++) {
                        xyz[2] = z;
                        for (int x = box[0]; x < box[3]; x++) {
                            xyz[0] = x;
                            int i = (xyz[1] * nz + xyz[2]) * nx + xyz[0];
                            if (box[6] < 0 && cells[i] == REAL) box[6] = i;
                            remaining[i] = false;
                        }
                    }
                }
                boxes.add(box);
            }
            boxes.addAll(slabBoxes(priority, false, remaining));
            return boxes;
        }

        private record Score(long visibleSeams, long exposedBoxFaces, long indexEntries, int colliderCount) {
            long cost() {
                return visibleSeams * 8L + exposedBoxFaces * 4L
                        + colliderCount * 12L + indexEntries * 2L;
            }
        }

        private Score score(List<int[]> boxes, byte[] exposed, int[] owners) {
            java.util.Arrays.fill(owners, -1);
            long indexEntries = 0;
            for (int b = 0; b < boxes.size(); b++) {
                int[] box = boxes.get(b);
                int extent = Math.max(box[3] - box[0],
                        Math.max(box[4] - box[1], box[5] - box[2]));
                int shift = extent > 16
                        ? 4 + 32 - Integer.numberOfLeadingZeros((extent - 1) >> 4) : 4;
                long entries = 1;
                int[] origin = {bx, by, bz};
                for (int axis = 0; axis < 3; axis++) {
                    entries *= ((box[axis + 3] + origin[axis] - 1) >> shift)
                            - ((box[axis] + origin[axis]) >> shift) + 1L;
                }
                indexEntries += entries;
                for (int y = box[1]; y < box[4]; y++) {
                    for (int z = box[2]; z < box[5]; z++) {
                        for (int x = box[0]; x < box[3]; x++) {
                            int i = (y * nz + z) * nx + x;
                            if (owners[i] >= 0 || !boxable(i)) {
                                throw new IllegalStateException("Overlapping or invalid collision box");
                            }
                            owners[i] = b;
                        }
                    }
                }
            }
            byte[] visible = new byte[boxes.size()];
            long seams = 0;
            int[] dimensions = {nx, ny, nz};
            int[] stride = {1, nx * nz, nx};
            int[] position = new int[3];
            for (int y = 0; y < ny; y++) {
                position[1] = y;
                for (int z = 0; z < nz; z++) {
                    position[2] = z;
                    for (int x = 0; x < nx; x++) {
                        position[0] = x;
                        int i = (y * nz + z) * nx + x;
                        int bits = exposed[i] & 0x3f;
                        if (owners[i] < 0) {
                            if (boxable(i)) throw new IllegalStateException("Unmeshed collision voxel");
                            continue;
                        }
                        if (bits == 0) continue;
                        visible[owners[i]] |= (byte) bits;
                        for (int axis = 0; axis < 3; axis++) {
                            int ua = (axis + 1) % 3, va = (axis + 2) % 3;
                            int adjacentU = position[ua] + 1 < dimensions[ua] ? i + stride[ua] : -1;
                            int adjacentV = position[va] + 1 < dimensions[va] ? i + stride[va] : -1;
                            for (int sign = 0; sign < 2; sign++) {
                                int bit = 1 << (axis * 2 + sign);
                                if ((bits & bit) == 0) continue;
                                if (adjacentU >= 0 && (exposed[adjacentU] & bit) != 0
                                        && owners[adjacentU] != owners[i]) seams++;
                                if (adjacentV >= 0 && (exposed[adjacentV] & bit) != 0
                                        && owners[adjacentV] != owners[i]) seams++;
                            }
                        }
                    }
                }
            }
            long facePieces = 0;
            for (byte mask : visible) facePieces += Integer.bitCount(mask & 0x3f);
            return new Score(seams, facePieces, indexEntries, boxes.size());
        }

        void meshSlabs(Mesh result) {
            byte[] exposed = exposedFaces();
            int[] owners = new int[cells.length];
            List<int[]> best = null;
            Score bestScore = null;
            for (int axis = 0; axis < 3; axis++) {
                for (int order = 0; order < 2; order++) {
                    List<int[]> candidate = slabBoxes(axis, order == 1, null);
                    Score candidateScore = score(candidate, exposed, owners);
                    if (bestScore == null || candidateScore.cost() < bestScore.cost()) {
                        best = candidate;
                        bestScore = candidateScore;
                    }
                }
            }
            for (int orientation = 0; orientation < 2; orientation++) {
                List<Plate> plates = surfacePlates(exposed, orientation != 0);
                for (int priority = 0; priority < 3; priority++) {
                    List<int[]> candidate = shellBoxes(plates, priority);
                    Score candidateScore = score(candidate, exposed, owners);
                    if (candidateScore.cost() < bestScore.cost()) {
                        best = candidate;
                        bestScore = candidateScore;
                    }
                }
            }
            long fallback = Long.MAX_VALUE;
            for (int i = 0; i < cells.length && fallback == Long.MAX_VALUE; i++) {
                if (cells[i] == REAL) fallback = key(i);
            }
            for (int[] box : best) {
                long material = box[6] >= 0 ? key(box[6]) : near(box[0], box[1], box[2], fallback);
                if (material != Long.MAX_VALUE) {
                    result.add(new Region(bx + box[0], by + box[1], bz + box[2],
                            bx + box[3], by + box[4], bz + box[5], material));
                }
            }
        }

        private List<int[]> slabBoxes(int axis, boolean swap, boolean[] available) {
            int[] dimensions = {nx, ny, nz};
            int run = axis == 0 ? (swap ? 2 : 1) : axis == 1 ? (swap ? 2 : 0) : (swap ? 1 : 0);
            int depth = 3 - axis - run;
            Map<Footprint, int[]> previous = new LinkedHashMap<>();
            List<int[]> boxes = new ArrayList<>();
            int[] coordinates = new int[3];
            for (int layer = 0; layer < dimensions[axis]; layer++) {
                coordinates[axis] = layer;
                Map<Long, int[]> row = new LinkedHashMap<>();
                List<int[]> rectangles = new ArrayList<>();
                for (int v = 0; v < dimensions[depth]; v++) {
                    coordinates[depth] = v;
                    Map<Long, int[]> nextRow = new LinkedHashMap<>();
                    for (int u = 0; u < dimensions[run];) {
                        coordinates[run] = u;
                        int i = (coordinates[1] * nz + coordinates[2]) * nx + coordinates[0];
                        if (available != null ? !available[i] : !boxable(i)) {
                            u++;
                            continue;
                        }
                        int begin = u, material = -1;
                        do {
                            coordinates[run] = u;
                            i = (coordinates[1] * nz + coordinates[2]) * nx + coordinates[0];
                            if (material < 0 && cells[i] == REAL) material = i;
                            u++;
                            if (u >= dimensions[run]) break;
                            coordinates[run] = u;
                        } while (available != null
                                ? available[(coordinates[1] * nz + coordinates[2]) * nx + coordinates[0]]
                                : boxable((coordinates[1] * nz + coordinates[2]) * nx + coordinates[0]));
                        long key = ((long) begin << 32) | u;
                        int[] box = row.remove(key);
                        if (box == null) {
                            box = new int[7];
                            box[axis] = layer;
                            box[axis + 3] = layer + 1;
                            box[run] = begin;
                            box[run + 3] = u;
                            box[depth] = v;
                            box[depth + 3] = v + 1;
                            box[6] = material;
                        } else {
                            box[depth + 3] = v + 1;
                            if (box[6] < 0) box[6] = material;
                        }
                        nextRow.put(key, box);
                    }
                    rectangles.addAll(row.values());
                    row = nextRow;
                }
                rectangles.addAll(row.values());
                Map<Footprint, int[]> next = new LinkedHashMap<>();
                for (int[] rect : rectangles) {
                    Footprint footprint = new Footprint(rect[run], rect[run + 3], rect[depth], rect[depth + 3]);
                    int[] box = previous.remove(footprint);
                    if (box == null) box = rect;
                    else {
                        box[axis + 3] = layer + 1;
                        if (box[6] < 0) box[6] = rect[6];
                    }
                    next.put(footprint, box);
                }
                boxes.addAll(previous.values());
                previous = next;
            }
            boxes.addAll(previous.values());
            return boxes;
        }

        void mesh(Mesh result, int grow) {
            boolean[] used = new boolean[cells.length];
            List<int[]> boxes = new ArrayList<>();
            long fallback = Long.MAX_VALUE;
            for (int i = 0; i < cells.length && fallback == Long.MAX_VALUE; i++) {
                if (cells[i] == REAL) fallback = key(i);
            }
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++) {
                        int i = (y * nz + z) * nx + x;
                        if (used[i] || !boxable(i)) continue;
                        int[] best = null;
                        double bestScore = -1.0;
                        for (int[] order : ORDERS) {
                            int[] end = {x + 1, y + 1, z + 1};
                            for (int direction : order) {
                                while (extend(used, x, y, z, end, direction)) end[direction]++;
                            }
                            double dx = end[0] - x, dy = end[1] - y, dz = end[2] - z;
                            double reach = Math.max(1, grow);
                            double score = dx * dy * dz / ((dx + reach) * (dy + reach) * (dz + reach));
                            if (score > bestScore) {
                                best = end;
                                bestScore = score;
                            }
                        }
                        int x1 = best[0], y1 = best[1], z1 = best[2];
                        int material = -1;
                        for (int yy = y; yy < y1; yy++) {
                            for (int zz = z; zz < z1; zz++) {
                                for (int xx = x; xx < x1; xx++) {
                                    int cell = (yy * nz + zz) * nx + xx;
                                    used[cell] = true;
                                    if (material < 0 && cells[cell] == REAL) material = cell;
                                }
                            }
                        }
                        boxes.add(new int[]{x, y, z, x1, y1, z1, material});
                    }
                }
            }
            for (int iteration = 0; iteration < 8; iteration++) {
                boolean merged = false;
                for (int axis = 0; axis < 3; axis++) {
                    int a = (axis + 1) % 3, b = (axis + 2) % 3, d = axis;
                    boxes.sort(Comparator.comparingInt((int[] box) -> box[a])
                            .thenComparingInt(box -> box[a + 3])
                            .thenComparingInt(box -> box[b])
                            .thenComparingInt(box -> box[b + 3])
                            .thenComparingInt(box -> box[d]));
                    List<int[]> packed = new ArrayList<>(boxes.size());
                    for (int[] box : boxes) {
                        int[] last = packed.isEmpty() ? null : packed.get(packed.size() - 1);
                        if (last != null && last[a] == box[a] && last[a + 3] == box[a + 3]
                                && last[b] == box[b] && last[b + 3] == box[b + 3]
                                && last[d + 3] == box[d]) {
                            last[d + 3] = box[d + 3];
                            if (last[6] < 0) last[6] = box[6];
                            merged = true;
                        } else packed.add(box);
                    }
                    boxes = packed;
                }
                if (!merged) break;
            }
            for (int[] box : boxes) {
                int x = box[0], y = box[1], z = box[2];
                int x1 = box[3], y1 = box[4], z1 = box[5];
                long material = box[6] >= 0 ? key(box[6]) : near(x, y, z, fallback);
                if (material != Long.MAX_VALUE) {
                    result.add(new Region(bx + x, by + y, bz + z, bx + x1, by + y1, bz + z1, material));
                }
            }
        }

        private boolean extend(boolean[] used, int x, int y, int z, int[] end, int axis) {
            if (axis == 0) return end[0] < nx && open(used, end[0], end[0] + 1, y, end[1], z, end[2]);
            if (axis == 1) return end[1] < ny && open(used, x, end[0], end[1], end[1] + 1, z, end[2]);
            return end[2] < nz && open(used, x, end[0], y, end[1], end[2], end[2] + 1);
        }

        private boolean open(boolean[] used, int x0, int x1, int y0, int y1, int z0, int z1) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    for (int x = x0; x < x1; x++) {
                        int i = (y * nz + z) * nx + x;
                        if (used[i] || !boxable(i)) return false;
                    }
                }
            }
            return true;
        }

        private long near(int x, int y, int z, long fallback) {
            for (int yy = Math.max(0, y - 1); yy < Math.min(ny, y + 2); yy++) {
                for (int zz = Math.max(0, z - 1); zz < Math.min(nz, z + 2); zz++) {
                    for (int xx = Math.max(0, x - 1); xx < Math.min(nx, x + 2); xx++) {
                        if (cells[(yy * nz + zz) * nx + xx] == REAL) return key((yy * nz + zz) * nx + xx);
                    }
                }
            }
            return fallback;
        }

        private long key(int i) {
            int x = i % nx, z = (i / nx) % nz, y = i / (nx * nz);
            return BlockPos.asLong(bx + x, by + y, bz + z);
        }
    }

    private static final class Selection {
        final Map<Long, VoxelShape> shapes = new HashMap<>();
        final Map<Long, Long> materials = new HashMap<>();
        Mesh mesh;
        void reset() {
            shapes.clear();
            materials.clear();
            mesh = null;
        }
    }

    private SubLevelIndexedCollision() {}
}
