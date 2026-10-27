package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.physics.ColliderGeometry.SectionKey;
import com.misterblusky9.pym.internal.scale.ScaleState;
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

        private void add(long value) {
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

    private static final class CellBuilder {
        private final List<ColliderShapeKey.Face> faces = new ArrayList<>();
        private final ColliderMaterial.Tally materials = new ColliderMaterial.Tally();
    }

    private static final class SectionBuilder {
        private final SectionKey key;
        private final Map<Integer, CellBuilder> cells = new HashMap<>();

        private SectionBuilder(SectionKey key) {
            this.key = key;
        }

        private void add(
                int x, int y, int z,
                ColliderShapeKey.Face face,
                short material
        ) {
            int localIndex = Math.floorMod(x, 16)
                    + (Math.floorMod(z, 16) << 4)
                    + (Math.floorMod(y, 16) << 8);
            CellBuilder cell = this.cells.computeIfAbsent(localIndex, ignored -> new CellBuilder());
            cell.faces.add(face);
            cell.materials.add(material, (face.maxXd() - face.minXd())
                    * (face.maxYd() - face.minYd())
                    * (face.maxZd() - face.minZd()));
        }
    }

    public static CompiledCollider compile(
            ServerSubLevel subLevel,
            int bodyId,
            long generation,
            PlotShape shape,
            int shapeRevision,
            PlotShapeCache.Region changed,
            CompiledCollider previous
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return null;
        UUID id = subLevel.getUniqueId();
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        double scale = ScaleState.getServerScale(subLevel);
        CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !ScaleBounds.isValid(scale)) return null;

        CompiledCollider.Mode requestedMode = shape == null
                ? CompiledCollider.Mode.PRISM_FALLBACK
                : CompiledCollider.Mode.DETAILED;
        CompiledCollider.GeometryKey key = geometryKey(
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
            PymTrace.warn(
                    "detailed collider compiled empty for {}; using prism fallback",
                    id);
            mode = CompiledCollider.Mode.PRISM_FALLBACK;
            sections = compilePrism(subLevel, scale, bounds);
        }

        CompiledCollider.GeometryKey finalKey = mode == requestedMode
                ? key
                : geometryKey(bodyId, shapeRevision, scale, pivot, bounds, mode);
        return new CompiledCollider(
                id, generation, finalKey, scale,
                pivot.x(), pivot.y(), pivot.z(), sections);
    }

    public static CompiledCollider.Bounds boundsFor(ServerSubLevel subLevel) {
        return boundsFor(subLevel, ScaleState.getServerScale(subLevel));
    }

    public static CompiledCollider.Bounds boundsFor(ServerSubLevel subLevel, double scale) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (source == null || pivot == null || !ScaleBounds.isValid(scale)) return null;

        double minX = ScaleFrame.contract(pivot.x(), source.minX(), scale);
        double minY = ScaleFrame.contract(pivot.y(), source.minY(), scale);
        double minZ = ScaleFrame.contract(pivot.z(), source.minZ(), scale);
        double maxX = ScaleFrame.contract(pivot.x(), source.maxX() + 1.0D, scale);
        double maxY = ScaleFrame.contract(pivot.y(), source.maxY() + 1.0D, scale);
        double maxZ = ScaleFrame.contract(pivot.z(), source.maxZ() + 1.0D, scale);
        return new CompiledCollider.Bounds(
                floor(minX), floor(minY), floor(minZ),
                ceilExclusive(maxX) - 1, ceilExclusive(maxY) - 1, ceilExclusive(maxZ) - 1);
    }

    public static CompiledCollider.GeometryKey requestKey(
            ServerSubLevel subLevel,
            int bodyId,
            int shapeRevision,
            boolean hasShape
    ) {
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        double scale = ScaleState.getServerScale(subLevel);
        CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !ScaleBounds.isValid(scale)) return null;
        return geometryKey(bodyId, shapeRevision, scale, pivot, bounds,
                hasShape ? CompiledCollider.Mode.DETAILED : CompiledCollider.Mode.PRISM_FALLBACK);
    }

    public static NativeImageKey nativeImageKey(
            ServerSubLevel subLevel,
            int bodyId,
            PlotShape shape
    ) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        double scale = ScaleState.getServerScale(subLevel);
        CompiledCollider.Bounds bounds = boundsFor(subLevel);
        if (pivot == null || bounds == null || !ScaleBounds.isValid(scale)) return null;

        Fingerprint fingerprint = new Fingerprint();
        CompiledCollider.Mode mode;
        int boxCount;
        if (shape != null) {
            mode = CompiledCollider.Mode.DETAILED;
            boxCount = shape.boxes().size();
            fingerprint.add(boxCount);
            for (int i = 0; i < boxCount; i++) {
                PlotShape.Box box = shape.boxes().get(i);
                fingerprint.add(shape.material(i));
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
            BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
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
            CompiledCollider current,
            ServerSubLevel subLevel,
            CompiledCollider.GeometryKey request
    ) {
        if (current == null || subLevel == null || request == null) return current;
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        double scale = ScaleState.getServerScale(subLevel);
        if (pivot == null || !ScaleBounds.isValid(scale)) return current;
        return current.withMetadata(request, scale, pivot.x(), pivot.y(), pivot.z());
    }

    private static void addInterval(
            Fingerprint fingerprint,
            double min,
            double max
    ) {
        if (!(max > min)) {
            fingerprint.add(0x7ff8000000000000L);
            return;
        }
        int first = floor(min);
        int last = ceilExclusive(max) - 1;
        fingerprint.add(first);
        fingerprint.add(last);
        fingerprint.add(canonicalLocalBits(min - first));
        fingerprint.add(canonicalLocalBits(max - last));
    }

    private static long canonicalLocalBits(double value) {
        float bounded = (float) Math.max(0.0D, Math.min(1.0D, value));
        float canonical = bounded == 0.0F ? 0.0F : bounded;
        return Integer.toUnsignedLong(Float.floatToIntBits(canonical));
    }

    private static CompiledCollider.GeometryKey geometryKey(
            int bodyId,
            int shapeRevision,
            double scale,
            Vector3dc pivot,
            CompiledCollider.Bounds bounds,
            CompiledCollider.Mode mode
    ) {
        double contractionOffset = 1.0D - scale;
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
            CompiledCollider previous,
            CompiledCollider.GeometryKey next,
            PlotShapeCache.Region changed
    ) {
        if (previous == null || changed == null || changed.full()) return false;
        CompiledCollider.GeometryKey old = previous.geometryKey();
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
            PlotShape shape,
            Vector3dc pivot,
            double scale,
            PlotShapeCache.Region changed,
            CompiledCollider previous
    ) {
        Map<SectionKey, CompiledCollider.Section> sections = new HashMap<>(previous.sections());
        java.util.Set<SectionKey> affected = affectedSections(changed, pivot, scale);
        if (affected.isEmpty()) return sections;

        Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitDetailedSections(shape, pivot, scale, affected, (x, y, z, face, material) -> {
            SectionKey key = new SectionKey(
                    Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
            grouped.computeIfAbsent(key, SectionBuilder::new).add(x, y, z, face, material);
        });

        for (SectionKey key : affected) {
            SectionBuilder builder = grouped.get(key);
            if (builder == null || builder.cells.isEmpty()) {
                sections.remove(key);
                continue;
            }
            CompiledCollider.Section compiled = compileGroupedSection(
                    key, builder.cells, scale, pivot, shape.source());
            if (compiled == null || compiled.isEmpty()) sections.remove(key);
            else sections.put(key, compiled);
        }
        return sections;
    }

    private static Map<SectionKey, CompiledCollider.Section> compileDetailed(
            PlotShape shape,
            Vector3dc pivot,
            double scale
    ) {
        Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitDetailed(shape, pivot, scale, (x, y, z, face, material) -> {
            SectionKey section = new SectionKey(
                    Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
            grouped.computeIfAbsent(section, SectionBuilder::new).add(x, y, z, face, material);
        });
        return compileGroupedSections(grouped, scale, pivot, shape.source());
    }

    private static Map<SectionKey, CompiledCollider.Section> compilePrism(
            ServerSubLevel subLevel,
            double scale,
            CompiledCollider.Bounds bounds
    ) {
        BoundingBox3ic source = subLevel.getPlot().getBoundingBox();
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (source == null || pivot == null) return Map.of();
        double minX = ScaleFrame.contract(pivot.x(), source.minX(), scale);
        double minY = ScaleFrame.contract(pivot.y(), source.minY(), scale);
        double minZ = ScaleFrame.contract(pivot.z(), source.minZ(), scale);
        double maxX = ScaleFrame.contract(pivot.x(), source.maxX() + 1.0D, scale);
        double maxY = ScaleFrame.contract(pivot.y(), source.maxY() + 1.0D, scale);
        double maxZ = ScaleFrame.contract(pivot.z(), source.maxZ() + 1.0D, scale);
        Map<SectionKey, SectionBuilder> grouped = new HashMap<>();
        ColliderGeometry.visitPrismShell(minX, minY, minZ, maxX, maxY, maxZ, scale,
                (x, y, z, face) -> {
                    SectionKey section = new SectionKey(
                            Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
                    grouped.computeIfAbsent(section, SectionBuilder::new)
                            .add(x, y, z, face, ColliderMaterial.DEFAULT_ID);
                });
        return compileGroupedSections(grouped, scale, pivot, null);
    }

    private static Map<SectionKey, CompiledCollider.Section> compileGroupedSections(
            Map<SectionKey, SectionBuilder> grouped,
            double scale,
            Vector3dc pivot,
            PlotSource source
    ) {
        Map<SectionKey, CompiledCollider.Section> result = new HashMap<>();
        for (Map.Entry<SectionKey, SectionBuilder> entry : grouped.entrySet()) {
            CompiledCollider.Section section = compileGroupedSection(
                    entry.getKey(), entry.getValue().cells, scale, pivot, source);
            if (section != null && !section.isEmpty()) result.put(entry.getKey(), section);
        }
        return Map.copyOf(result);
    }

    private static CompiledCollider.Section compileGroupedSection(
            SectionKey key,
            Map<Integer, CellBuilder> grouped,
            double scale,
            Vector3dc pivot,
            PlotSource source
    ) {
        List<CompiledCollider.Cell> cells = new ArrayList<>(grouped.size());
        for (Map.Entry<Integer, CellBuilder> entry : grouped.entrySet()) {
            int localIndex = entry.getKey();
            int localX = localIndex & 15;
            int localZ = (localIndex >>> 4) & 15;
            int localY = (localIndex >>> 8) & 15;
            int cellX = (key.x() << 4) + localX;
            int cellY = (key.y() << 4) + localY;
            int cellZ = (key.z() << 4) + localZ;

            List<ColliderShapeKey.Face> faces = ColliderGeometry.reduceExact(entry.getValue().faces);
            boolean approximate = false;
            if (faces.size() > ColliderDetail.MAX_CELL_BOXES) {
                approximate = true;
                long event = CAP_EVENTS.incrementAndGet();
                if (event <= 8L || (event & (event - 1L)) == 0L) {
                    PymTrace.warn(
                            "lossy collider cell cap event={} cell=[{},{},{}] exactBoxes={} maxBoxes={} scale={}",
                            event, cellX, cellY, cellZ, faces.size(),
                            ColliderDetail.MAX_CELL_BOXES, scale);
                }
                faces = ColliderGeometry.capCell(faces, scale, ColliderDetail.MAX_CELL_BOXES);
            }
            ColliderShapeKey shape = ColliderShapeKey.of(
                    faces, ColliderMaterial.byId(cellMaterial(entry.getValue(), cellX, cellY, cellZ, scale, pivot, source)));
            if (shape.faces().isEmpty()) continue;
            cells.add(new CompiledCollider.Cell(
                    cellX, cellY, cellZ, localIndex, shape, approximate));
        }
        return new CompiledCollider.Section(key, cells);
    }

    private static short cellMaterial(
            CellBuilder cell,
            int cellX, int cellY, int cellZ,
            double scale,
            Vector3dc pivot,
            PlotSource source
    ) {
        if (!cell.materials.mixed() || source == null || pivot == null) return cell.materials.winner();
        return source.surfaceMaterial(
                expand(pivot.x(), cellX, scale), expand(pivot.y(), cellY, scale), expand(pivot.z(), cellZ, scale),
                expand(pivot.x(), cellX + 1.0D, scale), expand(pivot.y(), cellY + 1.0D, scale), expand(pivot.z(), cellZ + 1.0D, scale));
    }

    private static double expand(double pivot, double value, double scale) {
        return pivot + (value - pivot) / scale;
    }

    private static java.util.Set<SectionKey> affectedSections(
            PlotShapeCache.Region region,
            Vector3dc pivot,
            double scale
    ) {
        if (region == null || region.full()) return java.util.Set.of();
        int firstX = floor(ScaleFrame.contract(pivot.x(), region.minX(), scale)) - 1;
        int firstY = floor(ScaleFrame.contract(pivot.y(), region.minY(), scale)) - 1;
        int firstZ = floor(ScaleFrame.contract(pivot.z(), region.minZ(), scale)) - 1;
        int lastX = ceilExclusive(ScaleFrame.contract(pivot.x(), region.maxX() + 1.0D, scale));
        int lastY = ceilExclusive(ScaleFrame.contract(pivot.y(), region.maxY() + 1.0D, scale));
        int lastZ = ceilExclusive(ScaleFrame.contract(pivot.z(), region.maxZ() + 1.0D, scale));

        java.util.HashSet<SectionKey> sections = new java.util.HashSet<>();
        for (int x = Math.floorDiv(firstX, 16); x <= Math.floorDiv(lastX, 16); x++) {
            for (int y = Math.floorDiv(firstY, 16); y <= Math.floorDiv(lastY, 16); y++) {
                for (int z = Math.floorDiv(firstZ, 16); z <= Math.floorDiv(lastZ, 16); z++) {
                    sections.add(new SectionKey(x, y, z));
                }
            }
        }
        return sections;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceilExclusive(double value) {
        return (int) Math.ceil(value - 1.0E-10D);
    }

    private ColliderCompiler() {}
}
