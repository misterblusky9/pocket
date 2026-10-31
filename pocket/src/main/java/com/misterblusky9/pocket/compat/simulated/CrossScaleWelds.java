package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CrossScaleWelds {
    private static final ThreadLocal<Integer> ASSEMBLY_MOVE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static volatile List<WeldRecord> clientWelds = List.of();
    private static final Map<Level, CachedGraph> SERVER_GRAPHS = new WeakHashMap<>();

    private static final Map<Level, CachedRivetGraph> RIVET_GRAPHS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record CachedGraph(long tick, long revision, WeldGraph graph) {}

    private record CachedRivetGraph(long tick, Object source, long revision, WeldGraph graph) {}

    public static final String SCALE_LIMIT = "Connected sublevel at scale limit";
    public static final String POCKET_BLOCKED = "Cut welds before pocketing";

    private static final double SEAM_TOLERANCE = 0.35D;

    public enum Refusal {
        NONE(null, false),
        OUT_OF_RANGE("create.package_port.too_far", true),
        OUT_OF_PLOT("create.package_port.too_far", true),
        SCALE_CHANGING("Still scaling", false),
        ATTACHMENT_UNLOADED("Connected craft not loaded", false),
        ALREADY_CONNECTED("Already connected", false),
        SAME_SUBLEVEL("Cannot weld to itself", false),
        WORLD_ALREADY_CONNECTED("Already welded to the world", false),
        CANNOT_WELD("Cannot weld", false);

        private final String message;
        private final boolean translatable;

        Refusal(String message, boolean translatable) {
            this.message = message;
            this.translatable = translatable;
        }

        public Component component() {
            if (this.message == null) return null;
            return this.translatable ? Component.translatable(this.message) : Component.literal(this.message);
        }

        public boolean allowed() {
            return this == NONE;
        }
    }

    public record Weld(
            SubLevel small,
            SubLevel big,
            BlockPos smallPos,
            BlockPos bigPos,
            Direction smallFacing,
            Direction bigFacing,
            double smallScale,
            double bigScale,
            int divisor,
            Vector3d smallAnchor,
            Vector3d bigAnchor
    ) {
        public static Weld resolve(
                Level level,
                BlockPos firstPos,
                Direction firstFacing,
                Vec3 firstHit,
                BlockPos secondPos,
                Direction secondFacing,
                Vec3 secondHit,
                WeldGeometry.SnapMode mode
        ) {
            if (level == null || firstPos == null || secondPos == null
                    || firstFacing == null || secondFacing == null) {
                return null;
            }

            SubLevel first = Sable.HELPER.getContaining(level, firstPos);
            SubLevel second = Sable.HELPER.getContaining(level, secondPos);
            if ((first != null && first.isRemoved()) || (second != null && second.isRemoved())) return null;
            if (first == null && second == null) return null;

            boolean worldWeld = first == null || second == null;
            double firstScale = first == null ? 1.0D : Pym.scale().settled(first);
            double secondScale = second == null ? 1.0D : Pym.scale().settled(second);
            boolean sameScale = ScaleBounds.same(firstScale, secondScale);
            boolean firstIsSmall = worldWeld
                    ? first != null
                    : sameScale || firstScale < secondScale;
            BlockPos smallPos = (firstIsSmall ? firstPos : secondPos).immutable();
            BlockPos bigPos = (firstIsSmall ? secondPos : firstPos).immutable();
            Direction smallFacing = firstIsSmall ? firstFacing : secondFacing;
            Direction bigFacing = firstIsSmall ? secondFacing : firstFacing;
            double smallScale = firstIsSmall ? firstScale : secondScale;
            double bigScale = firstIsSmall ? secondScale : firstScale;
            SubLevel small = firstIsSmall ? first : second;
            SubLevel big = worldWeld ? null : firstIsSmall ? second : first;
            if (small == null) return null;

            boolean unsized = sameScale || smallScale > bigScale;
            int divisor = unsized ? WeldGeometry.UNSTEPPED : WeldGeometry.divisor(smallScale, bigScale);
            double footprint = unsized ? 1.0D : smallScale / bigScale;
            Vec3 rawHit = firstIsSmall ? secondHit : firstHit;
            Vec3 hit = rawHit == null ? new Vec3(0.5D, 0.5D, 0.5D) : rawHit;
            WeldGeometry.SnapMode resolvedMode = !unsized && !firstIsSmall
                    ? WeldGeometry.SnapMode.GRID
                    : mode;
            Vector3d bigAnchor = unsized && mode != WeldGeometry.SnapMode.FREE
                    ? WeldGeometry.faceCentre(bigPos, bigFacing)
                    : WeldGeometry.anchor(bigPos, bigFacing, hit.x, hit.y, hit.z, divisor, footprint, resolvedMode);

            return new Weld(
                    small,
                    big,
                    smallPos,
                    bigPos,
                    smallFacing,
                    bigFacing,
                    smallScale,
                    bigScale,
                    divisor,
                    WeldGeometry.faceCentre(smallPos, smallFacing),
                    bigAnchor);
        }

        public boolean startedSmall(BlockPos firstPos) {
            return this.smallPos.equals(firstPos);
        }

        public double smallSpan() {
            return WeldGeometry.span(this.smallScale, this.bigScale);
        }

        public double bigSpan() {
            return WeldGeometry.span(this.bigScale, this.smallScale);
        }

        public boolean worldAnchored() {
            return this.big == null;
        }

        public Refusal check() {
            Level level = this.small.getLevel();
            if (level == null || this.divisor < 1) return Refusal.CANNOT_WELD;
            if (!worldAnchored() && (this.small == this.big
                    || java.util.Objects.equals(this.small.getUniqueId(), this.big.getUniqueId()))) {
                return Refusal.SAME_SUBLEVEL;
            }
            if (level.getBlockState(this.smallPos).isAir() || level.getBlockState(this.bigPos).isAir()) {
                return Refusal.CANNOT_WELD;
            }

            double range = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF()
                    * Math.max(ScaleBounds.FULL, Math.max(this.smallScale, this.bigScale));
            Vector3d smallWorld = worldPosition(this.small,
                    new Vec3(this.smallAnchor.x, this.smallAnchor.y, this.smallAnchor.z));
            Vector3d bigWorld = worldPosition(this.big,
                    new Vec3(this.bigAnchor.x, this.bigAnchor.y, this.bigAnchor.z));
            double distanceSquared = smallWorld.distanceSquared(bigWorld);
            if (!Double.isFinite(distanceSquared) || distanceSquared > range * range) {
                return Refusal.OUT_OF_RANGE;
            }

            if (!worldAnchored() && !withinPlot(this.small, this.big, this.bigAnchor)) return Refusal.OUT_OF_PLOT;

            if (!Pym.scale().isSettled(this.small)
                    || (!worldAnchored() && !Pym.scale().isSettled(this.big))) {
                return Refusal.SCALE_CHANGING;
            }

            SubLevelContainer container = SubLevelContainer.getContainer(level);
            boolean includeRivets = ModList.get().isLoaded("simulatedcoasters");
            WeldGraph attachments = container == null ? WeldGraph.EMPTY
                    : includeRivets ? rivetSnapshot(container) : snapshot(container);
            if (includeRivets && (!attachments.complete(this.small.getUniqueId())
                    || (!worldAnchored() && !attachments.complete(this.big.getUniqueId())))) {
                return Refusal.ATTACHMENT_UNLOADED;
            }

            if (worldAnchored()) {
                return attachments.worldAnchored(this.small.getUniqueId())
                        ? Refusal.WORLD_ALREADY_CONNECTED
                        : Refusal.NONE;
            }

            return attachments.adjacent(this.small.getUniqueId(), this.big.getUniqueId())
                    ? Refusal.ALREADY_CONNECTED
                    : Refusal.NONE;
        }

        public WeldRecord toRecord(UUID weldId, Quaterniond orientation) {
            return new WeldRecord(
                    weldId,
                    this.small.getUniqueId(),
                    worldAnchored() ? null : this.big.getUniqueId(),
                    this.smallPos,
                    this.bigPos,
                    this.smallFacing,
                    this.bigFacing,
                    new Vector3d(this.smallAnchor),
                    new Vector3d(this.bigAnchor),
                    smallSpan(),
                    bigSpan(),
                    orientation);
        }
    }

    public static void acceptClientWelds(List<WeldRecord> incoming) {
        clientWelds = incoming == null ? List.of() : List.copyOf(incoming);
    }

    public static List<WeldRecord> clientWelds() {
        return clientWelds;
    }

    public static Collection<WeldRecord> records(Level level) {
        if (level instanceof ServerLevel serverLevel) return WeldStore.get(serverLevel).all();
        return clientWelds;
    }

    public static boolean worldWeldAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        for (WeldRecord record : records(level)) {
            if (record.worldAnchored() && record.bigPos().equals(pos)) return true;
        }
        return false;
    }

    public static WeldRecord weldNear(Level level, Vec3 point) {
        if (level == null || point == null) return null;

        SubLevel containing = Sable.HELPER.getContaining(level, point);
        double scale = containing == null ? 1.0D : Pym.scale().of(containing);
        double tolerance = SEAM_TOLERANCE
                / (ScaleBounds.isValid(scale) ? ScaleBounds.clampValid(scale) : 1.0D);

        WeldRecord best = null;
        double bestDistance = tolerance;
        for (WeldRecord record : records(level)) {
            double distance = WeldContact.seamDistance(level, record, point);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = record;
        }
        return best;
    }

    public static boolean connected(
            Level level,
            UUID first,
            UUID second,
            UUID exceptWeld
    ) {
        if (first == null || second == null || first.equals(second)) return false;
        for (WeldRecord record : records(level)) {
            if (exceptWeld != null && exceptWeld.equals(record.weldId())) continue;
            if (record.connects(first, second)) return true;
        }
        return false;
    }

    public static boolean componentConnected(
            Level level,
            UUID first,
            UUID second
    ) {
        if (level == null || first == null || second == null || first.equals(second)) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && snapshot(container).component(first).contains(second);
    }

    public static boolean directlyWelded(
            Level level,
            UUID first,
            UUID second
    ) {
        if (level == null || first == null || second == null || first.equals(second)) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && snapshot(container).adjacent(first, second);
    }

    public static boolean isWelded(
            ServerSubLevelContainer container,
            ServerSubLevel subLevel
    ) {
        return subLevel != null
                && subLevel.getUniqueId() != null
                && snapshot(container).isEndpoint(subLevel.getUniqueId());
    }

    public static Set<UUID> componentIds(SubLevelContainer container, UUID origin) {
        return snapshot(container).component(origin);
    }

    public static boolean componentSettled(
            ServerSubLevelContainer container,
            UUID origin
    ) {
        WeldGraph graph = snapshot(container);
        if (!graph.complete(origin)) return false;
        for (UUID id : graph.component(origin)) {
            if (!Pym.scale().isSettled(id)) return false;
        }
        return true;
    }

    public static void beginAssemblyMove() {
        ASSEMBLY_MOVE_DEPTH.set(ASSEMBLY_MOVE_DEPTH.get() + 1);
    }

    public static void endAssemblyMove() {
        int depth = ASSEMBLY_MOVE_DEPTH.get() - 1;
        if (depth <= 0) ASSEMBLY_MOVE_DEPTH.remove();
        else ASSEMBLY_MOVE_DEPTH.set(depth);
    }

    public static boolean assemblyMoveActive() {
        return ASSEMBLY_MOVE_DEPTH.get() > 0;
    }

    public static void refreshLoaded(Level level) {
        invalidate(level);
        if (level instanceof ServerLevel serverLevel) {
            CrossScaleWeldSync.broadcast(serverLevel);
        }
    }

    public static Vector3d worldAnchorPoint(SubLevelContainer container, UUID origin) {
        if (container == null || origin == null) return null;
        Set<UUID> component = snapshot(container).component(origin);
        for (WeldRecord record : records(container.getLevel())) {
            if (record.worldAnchored() && component.contains(record.smallSubLevel())) return record.anchorFor(false);
        }
        return null;
    }

    public static void restageWelds(ServerSubLevel subLevel, double scale) {
        if (subLevel == null || !ScaleBounds.isValid(scale) || subLevel.getUniqueId() == null
                || !(subLevel.getLevel() instanceof ServerLevel level)) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        WeldStore store = WeldStore.get(level);
        boolean changed = false;
        for (WeldRecord record : store.touching(subLevel.getUniqueId())) {
            double smallScale = record.smallSubLevel().equals(subLevel.getUniqueId())
                    ? scale : endpointScale(container, record.smallSubLevel());
            double bigScale = record.worldAnchored() ? ScaleBounds.FULL
                    : record.bigSubLevel().equals(subLevel.getUniqueId())
                    ? scale : endpointScale(container, record.bigSubLevel());
            if (!ScaleBounds.isValid(smallScale) || !ScaleBounds.isValid(bigScale)) continue;

            double smallSpan = WeldGeometry.span(smallScale, bigScale);
            double bigSpan = WeldGeometry.span(bigScale, smallScale);
            if (ScaleBounds.same(record.smallSpan(), smallSpan)
                    && ScaleBounds.same(record.bigSpan(), bigSpan)) {
                continue;
            }
            store.add(record.withSpans(smallSpan, bigSpan));
            changed = true;
        }
        if (changed) CrossScaleWeldSync.broadcast(level);
    }

    private static double endpointScale(SubLevelContainer container, UUID id) {
        SubLevel endpoint = id == null ? null : container.getSubLevel(id);
        return endpoint == null || endpoint.isRemoved()
                ? Double.NaN : Pym.scale().of(endpoint);
    }

    public static void invalidate(Level level) {
        SERVER_GRAPHS.remove(level);
        RIVET_GRAPHS.remove(level);
    }

    private static WeldGraph rivetSnapshot(SubLevelContainer container) {
        Level level = container.getLevel();
        Object source;
        long revision;
        if (level instanceof ServerLevel serverLevel) {
            WeldStore store = WeldStore.get(serverLevel);
            source = store;
            revision = store.revision();
        } else {
            source = clientWelds;
            revision = 0L;
        }
        long tick = level.getGameTime();
        CachedRivetGraph cached = RIVET_GRAPHS.get(level);
        if (cached != null && cached.tick() == tick && cached.source() == source
                && cached.revision() == revision) return cached.graph();

        WeldGraph graph = buildGraph(container, records(level), true);
        RIVET_GRAPHS.put(level, new CachedRivetGraph(tick, source, revision, graph));
        return graph;
    }

    public static WeldGraph snapshot(SubLevelContainer container) {
        if (container == null) return WeldGraph.EMPTY;
        Level level = container.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return buildGraph(container, records(level));

        WeldStore store = WeldStore.get(serverLevel);
        CachedGraph cached = SERVER_GRAPHS.get(level);
        long tick = level.getGameTime();
        long revision = store.revision();
        if (cached != null && cached.tick() == tick && cached.revision() == revision) return cached.graph();

        WeldGraph graph = buildGraph(container, store.all());
        SERVER_GRAPHS.put(level, new CachedGraph(tick, revision, graph));
        return graph;
    }

    private static WeldGraph buildGraph(SubLevelContainer container, Collection<WeldRecord> records) {
        return buildGraph(container, records, false);
    }

    private static WeldGraph buildGraph(
            SubLevelContainer container, Collection<WeldRecord> records, boolean includeRivets
    ) {
        if (records.isEmpty() && !includeRivets) return WeldGraph.EMPTY;

        Set<UUID> endpointIds = new HashSet<>();
        Set<UUID> unresolved = new HashSet<>();
        Set<UUID> worldAnchors = new HashSet<>();
        Map<UUID, Set<UUID>> links = new HashMap<>();
        List<Edge> edges = new ArrayList<>();

        for (WeldRecord record : records) {
            UUID a = record.smallSubLevel();
            UUID b = record.bigSubLevel();
            if (a == null) continue;

            endpointIds.add(a);
            if (!live(container, a)) unresolved.add(a);

            if (record.worldAnchored()) {
                worldAnchors.add(a);
                edges.add(new Edge(record.weldId(), a, null));
                continue;
            }
            if (b == null || a.equals(b)) continue;

            endpointIds.add(b);
            links.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
            links.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
            edges.add(new Edge(record.weldId(), a, b));
            if (!live(container, b)) unresolved.add(b);
        }

        if (includeRivets) {
            Level level = container.getLevel();
            ResourceLocation rivet = ResourceLocation.fromNamespaceAndPath("simulatedcoasters", "rivet");
            for (SubLevel sub : container.getAllSubLevels()) {
                if (sub.isRemoved()) continue;
                var embedded = sub.getPlot().getEmbeddedLevelAccessor();
                if (!rivet.equals(BuiltInRegistries.BLOCK.getKey(embedded.getBlockState(BlockPos.ZERO).getBlock()))) continue;
                var blockEntity = embedded.getBlockEntity(BlockPos.ZERO);
                if (blockEntity == null) continue;

                UUID id = sub.getUniqueId();
                endpointIds.add(id);
                CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
                if (!tag.contains("HostPos", 4)) {
                    unresolved.add(id);
                    continue;
                }
                BlockPos hostPos = BlockPos.of(tag.getLong("HostPos"));
                if (!level.hasChunkAt(hostPos) || level.getBlockState(hostPos).isAir()) {
                    unresolved.add(id);
                    continue;
                }
                SubLevel host = Sable.HELPER.getContaining(level, hostPos);
                UUID hostId = host != null ? host.getUniqueId()
                        : tag.hasUUID("HostSubLevel") ? tag.getUUID("HostSubLevel") : null;
                if (hostId == null) {
                    worldAnchors.add(id);
                } else if (!id.equals(hostId)) {
                    endpointIds.add(hostId);
                    links.computeIfAbsent(id, ignored -> new HashSet<>()).add(hostId);
                    links.computeIfAbsent(hostId, ignored -> new HashSet<>()).add(id);
                    if (!live(container, hostId)) unresolved.add(hostId);
                } else {
                    unresolved.add(id);
                }
            }
        }

        if (endpointIds.isEmpty()) return WeldGraph.EMPTY;
        return WeldGraph.create(links, endpointIds, unresolved, worldAnchors, edges);
    }

    private static boolean live(SubLevelContainer container, UUID id) {
        SubLevel subLevel = container.getSubLevel(id);
        return subLevel != null && !subLevel.isRemoved();
    }

    public static Quaterniond relativeOrientation(SubLevel small, SubLevel big) {
        Quaterniond relative = new Quaterniond(small.logicalPose().orientation()).invert();
        if (big != null) relative.mul(big.logicalPose().orientation());
        return relative.normalize();
    }

    private static Vector3d worldPosition(SubLevel subLevel, Vec3 position) {
        Vector3d world = new Vector3d(position.x, position.y, position.z);
        if (subLevel != null) subLevel.logicalPose().transformPosition(world);
        return world;
    }

    public static Quaterniond weldOrientation(Weld weld) {
        return weldOrientation(weld, 0);
    }

    public static Quaterniond weldOrientation(Weld weld, int rotationDegrees) {
        Quaterniond aligned = WeldGeometry.alignment(
                relativeOrientation(weld.small(), weld.big()),
                WeldGeometry.normal(weld.smallFacing()),
                WeldGeometry.normal(weld.bigFacing()));
        int degrees = Math.floorMod(rotationDegrees, 360);
        if (degrees == 0) return aligned;

        Vector3d axis = WeldGeometry.normal(weld.smallFacing());
        return new Quaterniond()
                .rotationAxis(Math.toRadians(degrees), axis.x, axis.y, axis.z)
                .mul(aligned)
                .normalize();
    }

    private static boolean withinPlot(SubLevel own, SubLevel peer, Vector3dc peerAnchor) {
        if (own == null || peer == null || peerAnchor == null) return false;
        Vector3d probe = new Vector3d(peerAnchor);
        peer.logicalPose().transformPosition(probe);
        own.logicalPose().transformPositionInverse(probe);
        return Double.isFinite(probe.x) && Double.isFinite(probe.z) && own.getPlot().contains(probe);
    }

    public record WeldGraph(
            Map<UUID, Set<UUID>> links,
            Set<UUID> endpointIds,
            Set<UUID> unresolvedIds,
            Set<UUID> worldAnchorIds,
            List<Edge> edges
    ) implements ScaleCoupling.Graph {
        private static final WeldGraph EMPTY = new WeldGraph(Map.of(), Set.of(), Set.of(), Set.of(), List.of());

        private static WeldGraph create(
                Map<UUID, Set<UUID>> links,
                Set<UUID> endpointIds,
                Set<UUID> unresolvedIds,
                Set<UUID> worldAnchorIds,
                List<Edge> edges
        ) {
            Map<UUID, Set<UUID>> copy = new HashMap<>();
            links.forEach((id, values) -> copy.put(id, Set.copyOf(values)));
            return new WeldGraph(
                    Collections.unmodifiableMap(copy),
                    Set.copyOf(endpointIds),
                    Set.copyOf(unresolvedIds),
                    Set.copyOf(worldAnchorIds),
                    List.copyOf(edges));
        }

        public Set<UUID> members() {
            return this.endpointIds;
        }

        public boolean hasWelds() {
            return !this.endpointIds.isEmpty();
        }

        public boolean isEndpoint(UUID id) {
            return id != null && this.endpointIds.contains(id);
        }

        public boolean adjacent(UUID a, UUID b) {
            return a != null && b != null && this.links.getOrDefault(a, Set.of()).contains(b);
        }

        public Set<UUID> component(UUID origin) {
            if (origin == null) return Set.of();
            if (!this.endpointIds.contains(origin)) return Set.of(origin);

            Set<UUID> seen = new HashSet<>();
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            seen.add(origin);
            queue.add(origin);
            while (!queue.isEmpty()) {
                UUID id = queue.removeFirst();
                for (UUID next : this.links.getOrDefault(id, Set.of())) {
                    if (seen.add(next)) queue.addLast(next);
                }
            }
            return Collections.unmodifiableSet(seen);
        }

        public boolean worldAnchored(UUID origin) {
            for (UUID id : component(origin)) {
                if (this.worldAnchorIds.contains(id)) return true;
            }
            return false;
        }

        public boolean complete(UUID origin) {
            if (!isEndpoint(origin)) return true;
            Set<UUID> component = component(origin);
            if (component.size() <= 1 && !worldAnchored(origin)) return false;
            for (UUID id : component) {
                if (this.unresolvedIds.contains(id)) return false;
            }
            return true;
        }
    }

    public record Edge(UUID weldId, UUID a, UUID b) {
        public boolean connects(UUID first, UUID second) {
            return b != null && (a.equals(first) && b.equals(second) || a.equals(second) && b.equals(first));
        }
    }

    private CrossScaleWelds() {}
}
