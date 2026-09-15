package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

public final class CrossScaleWelds {
    private static final ThreadLocal<Boolean> SCALE_COMMAND = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Integer> ASSEMBLY_MOVE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static volatile List<WeldRecord> clientWelds = List.of();

    public static final String SCALE_LIMIT = "Connected sublevel at scale limit";
    public static final String SCALE_CONFLICT = "Conflicting scale commands";
    public static final String POCKET_BLOCKED = "Cut welds before pocketing";

    private static final double SEAM_TOLERANCE = 0.35D;

    public enum Refusal {
        NONE(null),
        OUT_OF_RANGE("Too far apart"),
        OUT_OF_PLOT("Too far apart at this scale"),
        SCALE_CHANGING("Still scaling"),
        ALREADY_CONNECTED("Already connected"),
        SAME_SUBLEVEL("Cannot weld to itself"),
        WORLD_ALREADY_CONNECTED("Already welded to the world"),
        CANNOT_WELD("Cannot weld");

        private final String message;

        Refusal(final String message) {
            this.message = message;
        }

        public String message() {
            return this.message;
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
            CompressionStage smallStage,
            CompressionStage bigStage,
            int divisor,
            Vector3d smallAnchor,
            Vector3d bigAnchor
    ) {
        public static Weld resolve(
                final Level level,
                final BlockPos firstPos,
                final Direction firstFacing,
                final Vec3 firstHit,
                final BlockPos secondPos,
                final Direction secondFacing,
                final Vec3 secondHit,
                final WeldGeometry.SnapMode mode
        ) {
            if (level == null || firstPos == null || secondPos == null
                    || firstFacing == null || secondFacing == null) {
                return null;
            }

            final SubLevel first = Sable.HELPER.getContaining(level, firstPos);
            final SubLevel second = Sable.HELPER.getContaining(level, secondPos);
            if ((first != null && first.isRemoved()) || (second != null && second.isRemoved())) return null;
            if (first == null && second == null) return null;

            final boolean worldWeld = first == null || second == null;
            final CompressionStage firstStage = first == null ? CompressionStage.NORMAL : ScaleState.getStage(first);
            final CompressionStage secondStage = second == null ? CompressionStage.NORMAL : ScaleState.getStage(second);
            final boolean sameScale = firstStage == secondStage;
            final boolean firstIsSmall = worldWeld
                    ? first != null
                    : sameScale || firstStage.isDeeperThan(secondStage);
            final BlockPos smallPos = (firstIsSmall ? firstPos : secondPos).immutable();
            final BlockPos bigPos = (firstIsSmall ? secondPos : firstPos).immutable();
            final Direction smallFacing = firstIsSmall ? firstFacing : secondFacing;
            final Direction bigFacing = firstIsSmall ? secondFacing : firstFacing;
            final CompressionStage smallStage = firstIsSmall ? firstStage : secondStage;
            final CompressionStage bigStage = firstIsSmall ? secondStage : firstStage;
            final SubLevel small = firstIsSmall ? first : second;
            final SubLevel big = worldWeld ? null : firstIsSmall ? second : first;
            if (small == null) return null;

            final int divisor = sameScale ? 1 : WeldGeometry.divisor(smallStage, bigStage);
            final Vec3 rawHit = firstIsSmall ? secondHit : firstHit;
            final Vec3 hit = rawHit == null ? new Vec3(0.5D, 0.5D, 0.5D) : rawHit;
            final WeldGeometry.SnapMode resolvedMode = !sameScale && !firstIsSmall
                    ? WeldGeometry.SnapMode.GRID
                    : mode;
            final Vector3d bigAnchor = sameScale && mode != WeldGeometry.SnapMode.FREE
                    ? WeldGeometry.faceCentre(bigPos, bigFacing)
                    : WeldGeometry.anchor(bigPos, bigFacing, hit.x, hit.y, hit.z, divisor, resolvedMode);

            return new Weld(
                    small,
                    big,
                    smallPos,
                    bigPos,
                    smallFacing,
                    bigFacing,
                    smallStage,
                    bigStage,
                    divisor,
                    WeldGeometry.faceCentre(smallPos, smallFacing),
                    bigAnchor);
        }

        public boolean startedSmall(final BlockPos firstPos) {
            return this.smallPos.equals(firstPos);
        }

        public double smallSpan() {
            return WeldGeometry.span(this.smallStage, this.bigStage);
        }

        public double bigSpan() {
            return WeldGeometry.span(this.bigStage, this.smallStage);
        }

        public boolean worldAnchored() {
            return this.big == null;
        }

        public Refusal check() {
            final Level level = this.small.getLevel();
            if (level == null || this.divisor < 1) return Refusal.CANNOT_WELD;
            if (!worldAnchored() && (this.small == this.big
                    || java.util.Objects.equals(this.small.getUniqueId(), this.big.getUniqueId()))) {
                return Refusal.SAME_SUBLEVEL;
            }
            if (level.getBlockState(this.smallPos).isAir() || level.getBlockState(this.bigPos).isAir()) {
                return Refusal.CANNOT_WELD;
            }

            final float range = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF();
            final Vector3d smallWorld = worldPosition(this.small, this.smallPos.getCenter());
            final Vector3d bigWorld = worldAnchored()
                    ? new Vector3d(this.bigPos.getX() + 0.5D, this.bigPos.getY() + 0.5D, this.bigPos.getZ() + 0.5D)
                    : worldPosition(this.big, this.bigPos.getCenter());
            final double distanceSquared = smallWorld.distanceSquared(bigWorld);
            if (!Double.isFinite(distanceSquared) || distanceSquared > range * range) {
                return Refusal.OUT_OF_RANGE;
            }

            if (!worldAnchored() && !withinPlot(this.small, this.big, this.bigAnchor)) return Refusal.OUT_OF_PLOT;

            if (!ScaleState.isSettled(this.small)
                    || (!worldAnchored() && !ScaleState.isSettled(this.big))) {
                return Refusal.SCALE_CHANGING;
            }

            if (worldAnchored()) {
                final SubLevelContainer container = SubLevelContainer.getContainer(level);
                return container != null && snapshot(container).worldAnchored(this.small.getUniqueId())
                        ? Refusal.WORLD_ALREADY_CONNECTED
                        : Refusal.NONE;
            }

            return directlyWelded(level, this.small.getUniqueId(), this.big.getUniqueId())
                    ? Refusal.ALREADY_CONNECTED
                    : Refusal.NONE;
        }

        public WeldRecord toRecord(final UUID weldId, final Quaterniond orientation) {
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

    public static void acceptClientWelds(final List<WeldRecord> incoming) {
        clientWelds = incoming == null ? List.of() : List.copyOf(incoming);
    }

    public static List<WeldRecord> clientWelds() {
        return clientWelds;
    }

    public static Collection<WeldRecord> records(final Level level) {
        if (level instanceof final ServerLevel serverLevel) return WeldStore.get(serverLevel).all();
        return clientWelds;
    }

    public static boolean worldWeldAt(final Level level, final BlockPos pos) {
        if (level == null || pos == null) return false;
        for (final WeldRecord record : records(level)) {
            if (record.worldAnchored() && record.bigPos().equals(pos)) return true;
        }
        return false;
    }

    public static WeldRecord weldNear(final Level level, final Vec3 point) {
        if (level == null || point == null) return null;

        final SubLevel containing = Sable.HELPER.getContaining(level, point);
        final double scale = containing == null ? 1.0D : ScaleState.getScale(containing);
        final double tolerance = SEAM_TOLERANCE
                / (PocketSized.isValidScale(scale) ? PocketSized.clampScale(scale) : 1.0D);

        WeldRecord best = null;
        double bestDistance = tolerance;
        for (final WeldRecord record : records(level)) {
            final double distance = WeldContact.seamDistance(level, record, point);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = record;
        }
        return best;
    }

    public static boolean connected(
            final Level level,
            final UUID first,
            final UUID second,
            final UUID exceptWeld
    ) {
        if (first == null || second == null || first.equals(second)) return false;
        for (final WeldRecord record : records(level)) {
            if (exceptWeld != null && exceptWeld.equals(record.weldId())) continue;
            if (record.connects(first, second)) return true;
        }
        return false;
    }

    public static boolean componentConnected(
            final Level level,
            final UUID first,
            final UUID second
    ) {
        if (level == null || first == null || second == null || first.equals(second)) return false;
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && snapshot(container).component(first).contains(second);
    }

    public static boolean directlyWelded(
            final Level level,
            final UUID first,
            final UUID second
    ) {
        if (level == null || first == null || second == null || first.equals(second)) return false;
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && snapshot(container).adjacent(first, second);
    }

    public static boolean isWelded(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel
    ) {
        return subLevel != null
                && subLevel.getUniqueId() != null
                && snapshot(container).isEndpoint(subLevel.getUniqueId());
    }

    public static Set<UUID> componentIds(final SubLevelContainer container, final UUID origin) {
        return snapshot(container).component(origin);
    }

    public static boolean componentSettled(
            final ServerSubLevelContainer container,
            final UUID origin
    ) {
        final WeldGraph graph = snapshot(container);
        if (!graph.complete(origin)) return false;
        for (final UUID id : graph.component(origin)) {
            if (!ScaleState.isSettled(id)) return false;
        }
        return true;
    }

    public static boolean scaleCommandActive() {
        return SCALE_COMMAND.get();
    }

    public static void beginScaleCommand() {
        SCALE_COMMAND.set(Boolean.TRUE);
    }

    public static void endScaleCommand() {
        SCALE_COMMAND.remove();
    }

    public static void beginAssemblyMove() {
        ASSEMBLY_MOVE_DEPTH.set(ASSEMBLY_MOVE_DEPTH.get() + 1);
    }

    public static void endAssemblyMove() {
        final int depth = ASSEMBLY_MOVE_DEPTH.get() - 1;
        if (depth <= 0) ASSEMBLY_MOVE_DEPTH.remove();
        else ASSEMBLY_MOVE_DEPTH.set(depth);
    }

    public static boolean assemblyMoveActive() {
        return ASSEMBLY_MOVE_DEPTH.get() > 0;
    }

    public static void refreshLoaded(final Level level) {
        if (level instanceof final ServerLevel serverLevel) {
            CrossScaleWeldSync.broadcast(serverLevel);
        }
    }

    public static ScalePlan planScale(
            final ServerSubLevel origin,
            final CompressionStage requested,
            final long gameTime
    ) {
        if (origin == null || requested == null || origin.isRemoved() || origin.getUniqueId() == null) {
            return ScalePlan.blocked(false);
        }

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(origin.getLevel());
        if (container == null) return ScalePlan.blocked(false);

        final WeldGraph graph = snapshot(container);
        if (!graph.isEndpoint(origin.getUniqueId())) return ScalePlan.single(origin, requested);
        if (!graph.complete(origin.getUniqueId())) return ScalePlan.blocked(true);

        final Set<UUID> component = graph.component(origin.getUniqueId());

        final int originDepth = commandedDepth(origin);
        final int delta = requested.depth() - originDepth;
        final Map<UUID, CompressionStage> goals = new LinkedHashMap<>();

        for (final UUID id : component) {
            if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                return ScalePlan.blocked(true);
            }

            final int currentDepth = commandedDepth(member);
            final int targetDepth = currentDepth + delta;
            if (targetDepth < CompressionStage.NORMAL.depth()
                    || targetDepth > CompressionStage.SIXTEENTH.depth()) {
                return ScalePlan.blocked(true);
            }

            final CompressionStage goal = CompressionStage.values()[targetDepth];
            if (goal.depth() > currentDepth
                    && CompressionBlacklist.find(member, gameTime).blocked()) {
                return ScalePlan.blocked(true);
            }
            goals.put(id, goal);
        }

        return new ScalePlan(true, true, Collections.unmodifiableMap(goals));
    }

    public static Vector3d worldAnchorPoint(final SubLevelContainer container, final UUID origin) {
        if (container == null || origin == null) return null;
        final Set<UUID> component = snapshot(container).component(origin);
        for (final WeldRecord record : records(container.getLevel())) {
            if (record.worldAnchored() && component.contains(record.smallSubLevel())) return record.anchorFor(false);
        }
        return null;
    }

    public static void restageWorldWelds(final ServerSubLevel subLevel, final CompressionStage stage) {
        if (subLevel == null || stage == null || subLevel.getUniqueId() == null
                || !(subLevel.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final WeldStore store = WeldStore.get(level);
        final double smallSpan = WeldGeometry.span(stage, CompressionStage.NORMAL);
        final double bigSpan = WeldGeometry.span(CompressionStage.NORMAL, stage);
        boolean changed = false;
        for (final WeldRecord record : store.touching(subLevel.getUniqueId())) {
            if (!record.worldAnchored()) continue;
            if (Math.abs(record.smallSpan() - smallSpan) <= PocketSized.EPSILON
                    && Math.abs(record.bigSpan() - bigSpan) <= PocketSized.EPSILON) {
                continue;
            }
            store.add(record.withSpans(smallSpan, bigSpan));
            changed = true;
        }
        if (changed) CrossScaleWeldSync.broadcast(level);
    }

    public static int commandedDepth(final ServerSubLevel subLevel) {
        if (subLevel == null) return CompressionStage.NORMAL.depth();
        final UUID id = subLevel.getUniqueId();
        if (id == null || !ScaleState.hasServerState(id)) {
            return CompressionStage.nearest(ScaleState.getServerScale(subLevel)).depth();
        }
        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        final CompressionStage stage = state.requestedStage() != null
                ? state.requestedStage()
                : state.transitionStage() != null ? state.transitionStage() : state.stableStage();
        return stage == null ? CompressionStage.NORMAL.depth() : stage.depth();
    }

    public static WeldGraph snapshot(final SubLevelContainer container) {
        if (container == null) return WeldGraph.EMPTY;

        final Collection<WeldRecord> records = records(container.getLevel());
        if (records.isEmpty()) return WeldGraph.EMPTY;

        final Set<UUID> endpointIds = new HashSet<>();
        final Set<UUID> unresolved = new HashSet<>();
        final Set<UUID> worldAnchors = new HashSet<>();
        final Map<UUID, Set<UUID>> links = new HashMap<>();
        final List<Edge> edges = new ArrayList<>();

        for (final WeldRecord record : records) {
            final UUID a = record.smallSubLevel();
            final UUID b = record.bigSubLevel();
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

        if (endpointIds.isEmpty()) return WeldGraph.EMPTY;
        return WeldGraph.create(links, endpointIds, unresolved, worldAnchors, edges);
    }

    private static boolean live(final SubLevelContainer container, final UUID id) {
        final SubLevel subLevel = container.getSubLevel(id);
        return subLevel != null && !subLevel.isRemoved();
    }

    public static Quaterniond relativeOrientation(final SubLevel small, final SubLevel big) {
        final Quaterniond relative = new Quaterniond(small.logicalPose().orientation()).invert();
        if (big != null) relative.mul(big.logicalPose().orientation());
        return relative.normalize();
    }

    private static Vector3d worldPosition(final SubLevel subLevel, final Vec3 position) {
        final Vector3d world = new Vector3d(position.x, position.y, position.z);
        if (subLevel != null) subLevel.logicalPose().transformPosition(world);
        return world;
    }

    public static Quaterniond weldOrientation(final Weld weld) {
        return weldOrientation(weld, 0);
    }

    public static Quaterniond weldOrientation(final Weld weld, final int rotationTurns) {
        final Quaterniond aligned = WeldGeometry.alignment(
                relativeOrientation(weld.small(), weld.big()),
                WeldGeometry.normal(weld.smallFacing()),
                WeldGeometry.normal(weld.bigFacing()));
        final int turns = Math.floorMod(rotationTurns, 4);
        if (turns == 0) return aligned;

        final Vector3d axis = WeldGeometry.normal(weld.smallFacing());
        return new Quaterniond()
                .rotationAxis(turns * Math.PI * 0.5D, axis.x, axis.y, axis.z)
                .mul(aligned)
                .normalize();
    }


    private static boolean withinPlot(final SubLevel own, final SubLevel peer, final Vector3dc peerAnchor) {
        if (own == null || peer == null || peerAnchor == null) return false;
        final Vector3d probe = new Vector3d(peerAnchor);
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
    ) {
        private static final WeldGraph EMPTY = new WeldGraph(Map.of(), Set.of(), Set.of(), Set.of(), List.of());

        private static WeldGraph create(
                final Map<UUID, Set<UUID>> links,
                final Set<UUID> endpointIds,
                final Set<UUID> unresolvedIds,
                final Set<UUID> worldAnchorIds,
                final List<Edge> edges
        ) {
            final Map<UUID, Set<UUID>> copy = new HashMap<>();
            links.forEach((id, values) -> copy.put(id, Set.copyOf(values)));
            return new WeldGraph(
                    Collections.unmodifiableMap(copy),
                    Set.copyOf(endpointIds),
                    Set.copyOf(unresolvedIds),
                    Set.copyOf(worldAnchorIds),
                    List.copyOf(edges));
        }

        public boolean hasWelds() {
            return !this.endpointIds.isEmpty();
        }

        public boolean isEndpoint(final UUID id) {
            return id != null && this.endpointIds.contains(id);
        }

        public boolean adjacent(final UUID a, final UUID b) {
            return a != null && b != null && this.links.getOrDefault(a, Set.of()).contains(b);
        }

        public Set<UUID> component(final UUID origin) {
            if (origin == null) return Set.of();
            if (!this.endpointIds.contains(origin)) return Set.of(origin);

            final Set<UUID> seen = new HashSet<>();
            final ArrayDeque<UUID> queue = new ArrayDeque<>();
            seen.add(origin);
            queue.add(origin);
            while (!queue.isEmpty()) {
                final UUID id = queue.removeFirst();
                for (final UUID next : this.links.getOrDefault(id, Set.of())) {
                    if (seen.add(next)) queue.addLast(next);
                }
            }
            return Collections.unmodifiableSet(seen);
        }

        public boolean worldAnchored(final UUID origin) {
            for (final UUID id : component(origin)) {
                if (this.worldAnchorIds.contains(id)) return true;
            }
            return false;
        }

        public boolean complete(final UUID origin) {
            if (!isEndpoint(origin)) return true;
            final Set<UUID> component = component(origin);
            if (component.size() <= 1 && !worldAnchored(origin)) return false;
            for (final UUID id : component) {
                if (this.unresolvedIds.contains(id)) return false;
            }
            return true;
        }
    }

    public record Edge(UUID weldId, UUID a, UUID b) {
        public boolean connects(final UUID first, final UUID second) {
            return b != null && (a.equals(first) && b.equals(second) || a.equals(second) && b.equals(first));
        }
    }

    public record ScalePlan(boolean welded, boolean allowed, Map<UUID, CompressionStage> goals) {
        private static ScalePlan single(final ServerSubLevel subLevel, final CompressionStage stage) {
            return new ScalePlan(false, true, Map.of(subLevel.getUniqueId(), stage));
        }

        private static ScalePlan blocked(final boolean welded) {
            return new ScalePlan(welded, false, Map.of());
        }
    }

    private CrossScaleWelds() {}
}
