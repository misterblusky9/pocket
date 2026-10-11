package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.api.event.ResizeFollowedEvent;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterRivets;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.network.ScaleNetwork;
import com.misterblusky9.pym.internal.physics.ConstraintRefresh;
import com.misterblusky9.pym.internal.physics.PlotShapeCache;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Resizer {
    static final String CONNECTED = "Connected sublevel: ";
    static final String NOT_LOADED = "A connected sublevel is not loaded";

    public static final double DEFAULT_TICKS = 9.0D;
    private static final double REFERENCE_MASS = 5_000.0D;

    public record Request(
            ServerSubLevel subLevel,
            double scale,
            Vector3dc anchor,
            ScaleBounds bounds,
            boolean joints,
            ScalePhysicsMode mode,
            double ticks,
            ScaleDriver driver
    ) {
        public Request {
            bounds = bounds == null ? ScaleBounds.ANY : bounds;
            mode = mode == null ? ScalePhysicsMode.TRACKING : mode;
        }
    }

    public static ResizeResult submit(final Request request) {
        final ResizePlan plan = plan(request);
        if (plan.refusal() != null) return plan.refusal();
        commit(plan, request);
        return ResizeResult.accepted(plan.originGoal());
    }

    public static ResizeResult check(final Request request) {
        final ResizePlan plan = plan(request);
        return plan.refusal() != null ? plan.refusal() : ResizeResult.accepted(plan.originGoal());
    }

    static ResizePlan plan(final Request request) {
        final ServerSubLevel origin = request.subLevel();
        if (origin == null || origin.isRemoved() || origin.getUniqueId() == null) {
            return ResizePlan.refused(origin, ResizeResult.refused(ResizeResult.Status.UNAVAILABLE));
        }
        if (!ScaleBounds.isValid(request.scale())) {
            return ResizePlan.refused(origin, ResizeResult.refused(
                    ResizeResult.Status.INVALID_SCALE,
                    "Scale " + request.scale() + " must be finite and greater than zero."));
        }
        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(origin.getLevel());
        if (container == null) {
            return ResizePlan.refused(origin, ResizeResult.refused(ResizeResult.Status.UNAVAILABLE));
        }

        final double goal = ScaleSnap.snap(request.scale());
        final double ratio = goal / ScaleState.getTargetScale(origin);

        final Set<ServerSubLevel> members = new LinkedHashSet<>();
        final ResizeResult missing = collect(container, origin, request.joints(), members);
        if (missing != null) return ResizePlan.refused(origin, missing);

        final Map<ServerSubLevel, Double> goals = new LinkedHashMap<>();
        double ticks = request.ticks();
        double slowest = Double.POSITIVE_INFINITY;
        for (final ServerSubLevel member : members) {
            final double from = ScaleState.getTargetScale(member);
            final double to = member == origin ? goal : ScaleSnap.snap(from * ratio);
            final ResizeResult refusal = refusal(member, from, to, request);
            if (refusal != null) {
                return ResizePlan.refused(origin, member == origin
                        || refusal.status() == ResizeResult.Status.OUT_OF_BOUNDS
                        ? refusal
                        : ResizeResult.refused(refusal.status(), CONNECTED + refusal.message()));
            }
            if (!ScaleBounds.same(from, to)) goals.put(member, to);
            slowest = Math.min(slowest, massSpeed(member));
        }

        final Map<Entity, Double> entityGoals = new LinkedHashMap<>();
        if (!ScaleBounds.same(ratio, 1.0D)) {
            for (final ServerSubLevel member : members) {
                for (final Map.Entry<Entity, ScaleBounds> follower : PymExtensions.resizeFollowers(member, request.driver()).entrySet()) {
                    final Entity entity = follower.getKey();
                    if (!EntityScaleTracker.supports(entity)) continue;
                    final double from = EntityScaleTracker.target(entity);
                    final double to = clampFollower(ScaleSnap.snap(from * ratio), from, follower.getValue());
                    if (!ScaleBounds.isValid(to)) continue;
                    if (!ScaleBounds.same(from, to)) entityGoals.put(entity, to);
                }
            }
        }

        if (!Double.isFinite(ticks) || ticks < 0.0D) {
            ticks = DEFAULT_TICKS / (Double.isFinite(slowest) ? slowest : 1.0D);
        }

        return new ResizePlan(origin, goals, entityGoals, pivot(container, origin, request.anchor()), ticks, null);
    }

    static void commit(final ResizePlan plan, final Request request) {
        for (final Map.Entry<ServerSubLevel, Double> entry : plan.goals().entrySet()) {
            final ServerSubLevel member = entry.getKey();
            final ScaleRecord record = ScaleState.record(member);
            record.begin(entry.getValue(), plan.ticks(), plan.pivot(),
                    member == plan.origin() ? request.driver() : null, plan.goals().size() == 1);
            ScalePhysicsTransitions.setMode(member, request.mode());
            ScaleNetwork.sendScale(member, record.current(), record.target());
        }

        final int entityTicks = (int) Math.max(0.0D, Math.round(plan.ticks()));
        for (final Map.Entry<Entity, Double> entry : plan.entityGoals().entrySet()) {
            if (EntityScaleTracker.set(entry.getKey(), entry.getValue(), entityTicks)) {
                NeoForge.EVENT_BUS.post(new ResizeFollowedEvent(entry.getKey(), entry.getValue()));
            }
        }
    }

    public static ResizeResult adopt(final ServerSubLevel subLevel, final double scale) {
        if (subLevel == null || subLevel.isRemoved()) return ResizeResult.refused(ResizeResult.Status.UNAVAILABLE);
        if (!ScaleBounds.isValid(scale)) return ResizeResult.refused(ResizeResult.Status.INVALID_SCALE);

        final double canonical = ScaleSnap.snap(scale);
        ScaleState.settle(subLevel, canonical);
        ScaleMotion.setPoseScale(subLevel, canonical);
        if (!SubLevelPhysicsSystem.IN_PHYSICS_STEP
                && ServerSubLevelContainer.getContainer(subLevel.getLevel()) instanceof final ServerSubLevelContainer container) {
            container.physicsSystem().getPipeline().onStatsChanged(subLevel);
            ScaleState.captureServerBounds(subLevel);
        }
        subLevel.updateLastPose();
        ScalePersistence.persist(subLevel);
        ScaleNetwork.sendScale(subLevel, canonical, canonical, true);
        return ResizeResult.accepted(canonical);
    }

    public static void adoptSplit(final ServerSubLevel child, final ServerSubLevel parent, final double scale) {
        final double canonical = ScaleSnap.snap(scale);
        ScaleState.settle(child, canonical);
        ScaleMotion.setPoseScale(child, canonical);
        child.updateLastPose();
        ScalePersistence.persist(child);
        ScaleState.clearServerBounds(child.getUniqueId());
        PlotShapeCache.invalidate(parent);
        PlotShapeCache.invalidate(child);
        SubLevelParentage.record(child, parent);
        ScaleNetwork.sendScale(child, canonical, canonical, true);
    }

    private static ResizeResult collect(
            final ServerSubLevelContainer container,
            final ServerSubLevel origin,
            final boolean joints,
            final Set<ServerSubLevel> members
    ) {
        final ScaleCoupling.Graph coupling = PymExtensions.couplingGraph(container);
        final Deque<ServerSubLevel> queue = new ArrayDeque<>();
        queue.add(origin);
        members.add(origin);

        while (!queue.isEmpty()) {
            final ServerSubLevel current = queue.poll();
            final UUID id = current.getUniqueId();

            final Set<UUID> next = new LinkedHashSet<>();
            if (coupling.contains(id)) {
                if (!coupling.complete(id)) return ResizeResult.refused(ResizeResult.Status.UNAVAILABLE, NOT_LOADED);
                next.addAll(coupling.component(id));
            }
            final UUID parent = SubLevelParentage.parentOf(id);
            if (parent != null && !owns(container, parent, id)) next.add(parent);
            next.addAll(SubLevelParentage.childrenOf(id));
            next.addAll(PymExtensions.ownedBy(current));
            if (joints) {
                for (final UUID neighbour : ConstraintRefresh.conductingNeighbours(container, id)) {
                    if (PymExtensions.holdsScale(neighbour) || owns(container, neighbour, id)) continue;
                    next.add(neighbour);
                }
            }

            for (final UUID candidate : next) {
                if (!(container.getSubLevel(candidate) instanceof final ServerSubLevel member) || member.isRemoved()) {
                    if (coupling.contains(candidate)) {
                        return ResizeResult.refused(ResizeResult.Status.UNAVAILABLE, NOT_LOADED);
                    }
                    continue;
                }
                if (PymExtensions.excludesSubLevel(member)) continue;
                if (members.add(member)) queue.add(member);
            }
        }
        return null;
    }

    private static boolean owns(final ServerSubLevelContainer container, final UUID owner, final UUID owned) {
        return container.getSubLevel(owner) instanceof final ServerSubLevel subLevel
                && PymExtensions.ownedBy(subLevel).contains(owned);
    }

    private static ResizeResult refusal(
            final ServerSubLevel subLevel,
            final double from,
            final double to,
            final Request request
    ) {
        if (!ScaleBounds.isValid(to)) {
            return ResizeResult.refused(ResizeResult.Status.INVALID_SCALE,
                    "Scale " + to + " must be finite and greater than zero.");
        }
        if (ScaleBounds.same(from, to)) return null;
        final ScaleBounds bounds = request.bounds();
        if (!bounds.permits(from, to)) {
            return ResizeResult.refused(ResizeResult.Status.OUT_OF_BOUNDS, to < from
                    ? "Can't scale below " + ScaleFormat.label(bounds.min())
                    : "Can't scale above " + ScaleFormat.label(bounds.max()));
        }
        final String shrink = PlotScan.refuseShrink(subLevel, from, to);
        if (shrink != null) return ResizeResult.refused(ResizeResult.Status.BLOCKED, shrink);
        final String blocked = PymExtensions.refuse(subLevel, from, to);
        return blocked == null ? null : ResizeResult.refused(ResizeResult.Status.BLOCKED, blocked);
    }

    private static double clampFollower(final double to, final double from, final ScaleBounds bounds) {
        if (!ScaleBounds.isValid(to)) return to;
        return Math.max(Math.min(from, bounds.min()), Math.min(Math.max(from, bounds.max()), to));
    }

    private static Pivot pivot(
            final ServerSubLevelContainer container,
            final ServerSubLevel origin,
            final Vector3dc anchor
    ) {
        final Vector3d world = PymExtensions.couplingWorldAnchor(container, origin.getUniqueId());
        if (world != null) return Pivot.fixed(world);
        if (anchor != null) return Pivot.on(origin, anchor);
        if (CoasterRivets.isRivetSubLevel(origin)) return Pivot.on(origin, CoasterRivets.attachmentAnchor(origin));
        return Pivot.on(origin, origin.logicalPose().rotationPoint());
    }

    private static double massSpeed(final ServerSubLevel subLevel) {
        final var tracker = subLevel.getMassTracker();
        final double mass = tracker == null ? Double.NaN : tracker.getMass();
        if (!Double.isFinite(mass) || mass <= 0.0D) return 1.0D;
        return Math.max(0.20D, Math.min(1.50D, Math.cbrt(REFERENCE_MASS / Math.max(mass, 1.0D))));
    }

    private Resizer() {}
}
