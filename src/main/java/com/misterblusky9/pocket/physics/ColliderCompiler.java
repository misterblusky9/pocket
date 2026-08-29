package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ColliderGeometry.SectionKey;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ColliderCompiler {
    private static final java.util.concurrent.atomic.AtomicLong CAP_EVENTS = new java.util.concurrent.atomic.AtomicLong();

    public record NativeImageKey(
            int bodyId,
            CompiledCollider.Bounds bounds,
            CompiledCollider.Mode mode,
            int sourceBoxCount,
            long hashA,
            long hashB
    ) {}

    private static final class Fingerprint {
        private long a = 0xcbf29ce484222325L;
        private long b = 0x9e3779b97f4a7c15L;

        private void add(final long value) {
            this.a ^= value;
            this.a *= 0x100000001b3L;

            long mixed = value + 0x9e3779b97f4a7c15L;
            mixed ^= mixed >>> 30;
            mixed *= 0xbf58476d1ce4e5b9L;
            mixed ^= mixed >>> 27;
            mixed *= 0x94d049bb133111ebL;
            mixed ^= mixed >>> 31;
            this.b ^= mixed + Long.rotateLeft(this.b, 17);
            this.b *= 0x9e3779b185ebca87L;
        }
    }

    private static final class SectionBuilder {
        private final SectionKey key;
        private final Map<Integer, List<ColliderShapeKey.Face>> cells = new HashMap<>();

        private SectionBuilder(final SectionKey key) {
            this.key = key;
        }

        private void add(
                final int x, final int y, final int z,
                final ColliderShapeKey.Face face
        ) {
            final int localIndex = Math.floorMod(x, 16)
                    + (Math.floorMod(z, 16) << 4)
                    + (Math.floorMod(y, 16) << 8);
            this.cells.computeIfAbsent(localIndex, ignored -> new ArrayList<>()).add(face);
        }
    }

    public static CompiledCollider compile(
            final ServerSubLevel subLevel,
            final int bodyId,
            final long generation,
            final PlotShape shape,
            final int shapeRevision,
            final PlotShapeCache.Region changed,
            final CompiledCollider previous
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return null;
        final UUID id = subLevel.getUniqueId();
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        final double scale = ScaleState.getServerScale(subLevel);
        final CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !PocketSized.isValidScale(scale)) return null;

        final CompiledCollider.Mode requestedMode = shape == null
                ? CompiledCollider.Mode.PRISM_FALLBACK
                : CompiledCollider.Mode.DETAILED;
        final CompiledCollider.GeometryKey key = geometryKey(
                bodyId, shapeRevision, scale, pivot, bounds, requestedMode);

        Map<SectionKey, CompiledCollider.Section> sections;
        CompiledCollider.Mode mode = requestedMode;

        if (shape != null && canIncrementallyCompile(previous, key, changed)) {
            sections = compileIncremental(shape, pivot, scale, changed, previous);
        } else if (shape != null) {
            sections = compileDetailed(shape, pivot, scale);
        } else {
            sections = compilePrism(subLevel, scale, bounds);
        }

        if (sections.isEmpty() && shape != null) {
            PocketTrace.warn(
                    "detailed collider compiled empty for {}; using prism fallback",
                    id);
            mode = CompiledCollider.Mode.PRISM_FALLBACK;
            sections = compilePrism(subLevel, scale, bounds);
        }

        final CompiledCollider.GeometryKey finalKey = mode == requestedMode
                ? key
                : geometryKey(bodyId, shapeRevision, scale, pivot, bounds, mode);
        return new CompiledCollider(
                id, generation, finalKey, scale,
                pivot.x(), pivot.y(), pivot.z(), sections);
    }

    public static CompiledCollider.Bounds boundsFor(final ServerSubLevel subLevel) {
        return boundsFor(subLevel, ScaleState.getServerScale(subLevel));
    }

    public static CompiledCollider.Bounds boundsFor(final ServerSubLevel subLevel, final double scale) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        final BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (source == null || pivot == null || !PocketSized.isValidScale(scale)) return null;

        final double minX = ScaleFrame.contract(pivot.x(), source.minX(), scale);
        final double minY = ScaleFrame.contract(pivot.y(), source.minY(), scale);
        final double minZ = ScaleFrame.contract(pivot.z(), source.minZ(), scale);
        final double maxX = ScaleFrame.contract(pivot.x(), source.maxX() + 1.0D, scale);
        final double maxY = ScaleFrame.contract(pivot.y(), source.maxY() + 1.0D, scale);
        final double maxZ = ScaleFrame.contract(pivot.z(), source.maxZ() + 1.0D, scale);
        return new CompiledCollider.Bounds(
                floor(minX), floor(minY), floor(minZ),
                ceilExclusive(maxX) - 1, ceilExclusive(maxY) - 1, ceilExclusive(maxZ) - 1);
    }

    public static CompiledCollider.GeometryKey requestKey(
            final ServerSubLevel subLevel,
            final int bodyId,
            final int shapeRevision,
            final boolean hasShape
    ) {
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        final double scale = ScaleState.getServerScale(subLevel);
        final CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !PocketSized.isValidScale(scale)) return null;
        return geometryKey(bodyId, shapeRevision, scale, pivot, bounds,
                hasShape ? CompiledCollider.Mode.DETAILED : CompiledCollider.Mode.PRISM_FALLBACK);
    }

    public static NativeImageKey nativeImageKey(
            final ServerSubLevel subLevel,
            final int bodyId,
            final PlotShape shape
    ) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        final double scale = ScaleState.getServerScale(subLevel);
        final CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !PocketSized.isValidScale(scale)) return null;

        final Fingerprint fingerprint = new Fingerprint();
        final CompiledCollider.Mode mode;
        final int boxCount;
        if (shape != null) {
            mode = CompiledCollider.Mode.DETAILED;
            boxCount = shape.boxes().size();
            fingerprint.add(boxCount);
            for (final PlotShape.Box box : shape.boxes()) {
                addInterval(fingerprint,
                        ScaleFrame.contract(pivot.x(), box.minX(), scale),
                        ScaleFrame.contract(pivot.x(), box.maxX(), scale));
                addInterval(fingerprint,
                        ScaleFrame.contract(pivot.y(), box.minY(), scale),
                        ScaleFrame.contract(pivot.y(), box.maxY(), scale));
                addInterval(fingerprint,
                        ScaleFrame.contract(pivot.z(), box.minZ(), scale),
                        ScaleFrame.contract(pivot.z(), box.maxZ(), scale));
            }
        } else {
            mode = CompiledCollider.Mode.PRISM_FALLBACK;
            final BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
            if (source == null) return null;
            boxCount = 1;
            fingerprint.add(1L);
            addInterval(fingerprint,
                    ScaleFrame.contract(pivot.x(), source.minX(), scale),
                    ScaleFrame.contract(pivot.x(), source.maxX() + 1.0D, scale));
            addInterval(fingerprint,
                    ScaleFrame.contract(pivot.y(), source.minY(), scale),
                    ScaleFrame.contract(pivot.y(), source.maxY() + 1.0D, scale));
            addInterval(fingerprint,
                    ScaleFrame.contract(pivot.z(), source.minZ(), scale),
                    ScaleFrame.contract(pivot.z(), source.maxZ() + 1.0D, scale));
        }

        return new NativeImageKey(
                bodyId, bounds, mode, boxCount,
                fingerprint.a, fingerprint.b);
    }

    public static CompiledCollider retag(
            final CompiledCollider current,
            final ServerSubLevel subLevel,
            final CompiledCollider.GeometryKey request
    ) {
        if (current == null || subLevel == null || request == null) return current;
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        final double scale = ScaleState.getServerScale(subLevel);
        if (pivot == null || !PocketSized.isValidScale(scale)) return current;
        return current.withMetadata(request, scale, pivot.x(), pivot.y(), pivot.z());
    }

    private static void addInterval(
            final Fingerprint fingerprint,
            final double min,
            final double max
    ) {
        if (!(max > min)) {
            fingerprint.add(0x7ff8000000000000L);
            return;
        }
        final int first = floor(min);
        final int last = ceilExclusive(max) - 1;
        fingerprint.add(first);
        fingerprint.add(last);
        fingerprint.add(canonicalLocalBits(min - first));
        fingerprint.add(canonicalLocalBits(max - last));
    }

    private static long canonicalLocalBits(final double value) {
        final float bounded = (float) Math.max(0.0D, Math.min(1.0D, value));
        final float canonical = bounded == 0.0F ? 0.0F : bounded;
        return Integer.toUnsignedLong(Float.floatToIntBits(canonical));
    }

    private static CompiledCollider.GeometryKey geometryKey(
            final int bodyId,
            final int shapeRevision,
            final double scale,
            final Vector3dc pivot,
            final CompiledCollider.Bounds bounds,
            final CompiledCollider.Mode mode
    ) {
        final double contractionOffset = 1.0D - scale;
        return new CompiledCollider.GeometryKey(
                bodyId,
                shapeRevision,
                Double.doubleToLongBits(scale),
                Double.doubleToLongBits(pivot.x() * contractionOffset),
                Double.doubleToLongBits(pivot.y() * contractionOffset),
                Double.doubleToLongBits(pivot.z() * contractionOffset),
                bounds,
                mode);
    }

    private static boolean canIncrementallyCompile(
            final CompiledCollider previous,
            final CompiledCollider.GeometryKey next,
            final PlotShapeCache.Region changed
    ) {
        if (previous == null || changed == null || changed.full()) return false;
        final CompiledCollider.GeometryKey old = previous.geometryKey();
        return previous.mode() == CompiledCollider.Mode.DETAILED
                && next.mode() == CompiledCollider.Mode.DETAILED
                && old.bodyId() == next.bodyId()
                && old.scaleBits() == next.scaleBits()
                && old.pivotX() == next.pivotX()
                && old.pivotY() == next.pivotY()
                && old.pivotZ() == next.pivotZ()
                && old.bounds().equals(next.bounds());
    }

    private static Map<SectionKey, CompiledCollider.Section> compileIncremental(
            final PlotShape shape,
            final Vector3dc pivot,
            final double scale,
            final PlotShapeCache.Region changed,
            final CompiledCollider previous
    ) {
        final Map<SectionKey, CompiledCollider.Section> sections = new HashMap<>(previous.sections());
        final java.util.Set<SectionKey> affected = affectedSections(changed, pivot, scale);
        if (affected.isEmpty()) return sections;

        final Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitDetailedSections(shape, pivot, scale, affected, (x, y, z, face) -> {
            final SectionKey key = new SectionKey(
                    Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
            grouped.computeIfAbsent(key, SectionBuilder::new).add(x, y, z, face);
        });

        for (final SectionKey key : affected) {
            final SectionBuilder builder = grouped.get(key);
            if (builder == null || builder.cells.isEmpty()) {
                sections.remove(key);
                continue;
            }
            final CompiledCollider.Section compiled = compileGroupedSection(key, builder.cells, scale);
            if (compiled == null || compiled.isEmpty()) sections.remove(key);
            else sections.put(key, compiled);
        }
        return sections;
    }

    private static Map<SectionKey, CompiledCollider.Section> compileDetailed(
            final PlotShape shape,
            final Vector3dc pivot,
            final double scale
    ) {
        final Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitDetailed(shape, pivot, scale, (x, y, z, face) -> {
            final SectionKey section = new SectionKey(
                    Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
            grouped.computeIfAbsent(section, SectionBuilder::new).add(x, y, z, face);
        });
        return compileGroupedSections(grouped, scale);
    }

    private static Map<SectionKey, CompiledCollider.Section> compilePrism(
            final ServerSubLevel subLevel,
            final double scale,
            final CompiledCollider.Bounds bounds
    ) {
        final BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (source == null || pivot == null) return Map.of();
        final double minX = ScaleFrame.contract(pivot.x(), source.minX(), scale);
        final double minY = ScaleFrame.contract(pivot.y(), source.minY(), scale);
        final double minZ = ScaleFrame.contract(pivot.z(), source.minZ(), scale);
        final double maxX = ScaleFrame.contract(pivot.x(), source.maxX() + 1.0D, scale);
        final double maxY = ScaleFrame.contract(pivot.y(), source.maxY() + 1.0D, scale);
        final double maxZ = ScaleFrame.contract(pivot.z(), source.maxZ() + 1.0D, scale);
        final Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitPrismShell(minX, minY, minZ, maxX, maxY, maxZ, scale,
                (x, y, z, face) -> {
                    final SectionKey section = new SectionKey(
                            Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
                    grouped.computeIfAbsent(section, SectionBuilder::new).add(x, y, z, face);
                });
        return compileGroupedSections(grouped, scale);
    }

    private static Map<SectionKey, CompiledCollider.Section> compileGroupedSections(
            final Map<SectionKey, SectionBuilder> grouped,
            final double scale
    ) {
        final Map<SectionKey, CompiledCollider.Section> result = new HashMap<>();
        for (final Map.Entry<SectionKey, SectionBuilder> entry : grouped.entrySet()) {
            final CompiledCollider.Section section = compileGroupedSection(
                    entry.getKey(), entry.getValue().cells, scale);
            if (section != null && !section.isEmpty()) result.put(entry.getKey(), section);
        }
        return Map.copyOf(result);
    }

    private static CompiledCollider.Section compileGroupedSection(
            final SectionKey key,
            final Map<Integer, List<ColliderShapeKey.Face>> grouped,
            final double scale
    ) {
        final List<CompiledCollider.Cell> cells = new ArrayList<>(grouped.size());
        for (final Map.Entry<Integer, List<ColliderShapeKey.Face>> entry : grouped.entrySet()) {
            final int localIndex = entry.getKey();
            final int localX = localIndex & 15;
            final int localZ = (localIndex >>> 4) & 15;
            final int localY = (localIndex >>> 8) & 15;
            final int cellX = (key.x() << 4) + localX;
            final int cellY = (key.y() << 4) + localY;
            final int cellZ = (key.z() << 4) + localZ;

            List<ColliderShapeKey.Face> faces = ColliderGeometry.reduceExact(entry.getValue());
            boolean approximate = false;
            if (faces.size() > ColliderDetail.MAX_CELL_BOXES) {
                approximate = true;
                final long event = CAP_EVENTS.incrementAndGet();
                if (event <= 8L || (event & (event - 1L)) == 0L) {
                    PocketTrace.warn(
                            "lossy collider cell cap event={} cell=[{},{},{}] exactBoxes={} maxBoxes={} scale={}",
                            event, cellX, cellY, cellZ, faces.size(),
                            ColliderDetail.MAX_CELL_BOXES, scale);
                }
                faces = ColliderGeometry.capCell(faces, scale, ColliderDetail.MAX_CELL_BOXES);
            }
            final ColliderShapeKey shape = ColliderShapeKey.of(faces);
            if (shape.faces().isEmpty()) continue;
            cells.add(new CompiledCollider.Cell(
                    cellX, cellY, cellZ, localIndex, shape, approximate));
        }
        return new CompiledCollider.Section(key, cells);
    }

    private static java.util.Set<SectionKey> affectedSections(
            final PlotShapeCache.Region region,
            final Vector3dc pivot,
            final double scale
    ) {
        if (region == null || region.full()) return java.util.Set.of();
        final int firstX = floor(ScaleFrame.contract(pivot.x(), region.minX(), scale)) - 1;
        final int firstY = floor(ScaleFrame.contract(pivot.y(), region.minY(), scale)) - 1;
        final int firstZ = floor(ScaleFrame.contract(pivot.z(), region.minZ(), scale)) - 1;
        final int lastX = ceilExclusive(ScaleFrame.contract(pivot.x(), region.maxX() + 1.0D, scale));
        final int lastY = ceilExclusive(ScaleFrame.contract(pivot.y(), region.maxY() + 1.0D, scale));
        final int lastZ = ceilExclusive(ScaleFrame.contract(pivot.z(), region.maxZ() + 1.0D, scale));

        final java.util.HashSet<SectionKey> sections = new java.util.HashSet<>();
        for (int x = Math.floorDiv(firstX, 16); x <= Math.floorDiv(lastX, 16); x++) {
            for (int y = Math.floorDiv(firstY, 16); y <= Math.floorDiv(lastY, 16); y++) {
                for (int z = Math.floorDiv(firstZ, 16); z <= Math.floorDiv(lastZ, 16); z++) {
                    sections.add(new SectionKey(x, y, z));
                }
            }
        }
        return sections;
    }

    private static int floor(final double value) {
        return (int) Math.floor(value);
    }

    private static int ceilExclusive(final double value) {
        return (int) Math.ceil(value - 1.0E-10D);
    }

    private ColliderCompiler() {}
}
