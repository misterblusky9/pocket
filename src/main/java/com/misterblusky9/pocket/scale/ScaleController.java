package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.SimulatedRopeScaleBoundary;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersRivetCompat;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.persistence.ScalePersistence;
import com.misterblusky9.pocket.physics.ScalePhysicsTransitions;
import com.misterblusky9.pocket.physics.ExpansionClearance;
import com.misterblusky9.pocket.physics.KinematicCollisionSuppression;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleController {
    private static final long EXTERNAL_COMMAND_TTL = 8L;

    private static final double REFERENCE_MASS = 5_000.0D;
    private static final double MIN_STEP_FACTOR = 0.20D;
    private static final double MAX_STEP_FACTOR = 1.50D;
    private static final double RATIO_TOLERANCE = 1.0E-6D;
    private static final Map<UUID, ExternalCommand> EXTERNAL_COMMANDS = new ConcurrentHashMap<>();

    public static void registerExternalCommand(
            final ServerSubLevel subLevel,
            final ScaleCommandSource source,
            final long gameTime
    ) {
        if (subLevel == null || source == null || subLevel.getUniqueId() == null) return;
        EXTERNAL_COMMANDS.put(subLevel.getUniqueId(), new ExternalCommand(source, gameTime + EXTERNAL_COMMAND_TTL));
    }

    public static void registerExternalCommandUntil(
            final ServerSubLevel subLevel,
            final ScaleCommandSource source,
            final long validUntilTick
    ) {
        if (subLevel == null || source == null || subLevel.getUniqueId() == null) return;
        EXTERNAL_COMMANDS.put(subLevel.getUniqueId(), new ExternalCommand(source, validUntilTick));
    }

    public static void clearExternalCommand(final UUID subLevelId) {
        if (subLevelId != null) EXTERNAL_COMMANDS.remove(subLevelId);
    }

    public static void forceStage(
            final ServerSubLevel subLevel,
            final CompressionStage stage,
            final long gameTime
    ) {
        forceStage(subLevel, stage, gameTime, null);
    }

    public static void forceStage(
            final ServerSubLevel subLevel,
            final CompressionStage stage,
            final long gameTime,
            final Vector3d anchorLocalPoint
    ) {
        forceStage(subLevel, stage, gameTime, anchorLocalPoint, true);
    }

    public static void forceStage(
            final ServerSubLevel subLevel,
            final CompressionStage stage,
            final long gameTime,
            final Vector3d anchorLocalPoint,
            final boolean propagateJoints
    ) {
        forceStage(subLevel, stage, gameTime, anchorLocalPoint, propagateJoints, ScalePhysicsMode.TRACKING);
    }

    public static void forceStage(
            final ServerSubLevel subLevel,
            final CompressionStage stage,
            final long gameTime,
            final Vector3d anchorLocalPoint,
            final boolean propagateJoints,
            final ScalePhysicsMode physicsMode
    ) {
        if (stage == null) return;
        forceScale(subLevel, stage.scale(), gameTime, anchorLocalPoint, propagateJoints, physicsMode);
    }

    public static void forceScale(
            final ServerSubLevel subLevel,
            final double scale,
            final long gameTime,
            final Vector3d anchorLocalPoint,
            final boolean propagateJoints
    ) {
        forceScale(subLevel, scale, gameTime, anchorLocalPoint, propagateJoints, ScalePhysicsMode.TRACKING);
    }

    public static void forceScale(
            final ServerSubLevel subLevel,
            final double scale,
            final long gameTime,
            final Vector3d anchorLocalPoint,
            final boolean propagateJoints,
            final ScalePhysicsMode physicsMode
    ) {
        forceScale(subLevel, scale, gameTime, anchorLocalPoint, propagateJoints, physicsMode, ScaleLimits.API);
    }

    public static void forceScale(
            final ServerSubLevel subLevel,
            final double scale,
            final long gameTime,
            final Vector3d anchorLocalPoint,
            final boolean propagateJoints,
            final ScalePhysicsMode physicsMode,
            final ScaleLimits limits
    ) {
        if (subLevel == null || !PocketSized.isValidScale(scale)) return;

        final double requested = CompressionStage.snap(scale);
        final ScalePhysicsMode effectiveMode =
                physicsMode == null ? ScalePhysicsMode.TRACKING : physicsMode;
        final ScaleLimits effectiveLimits = limits == null ? ScaleLimits.API : limits;

        if (!CrossScaleWelds.scaleCommandActive()) {
            final CrossScaleWelds.ScalePlan plan =
                    CrossScaleWelds.planScale(subLevel, requested, gameTime, effectiveLimits);
            if (plan.welded()) {
                if (!plan.allowed()) return;
                final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
                if (container == null) return;

                if (propagateJoints
                        && !JointScalePropagation.onCommanded(subLevel, requested, effectiveMode, effectiveLimits)) {
                    return;
                }

                final Map<ServerSubLevel, Double> members = new LinkedHashMap<>();
                for (final Map.Entry<UUID, Double> entry : plan.goals().entrySet()) {
                    if (!(container.getSubLevel(entry.getKey()) instanceof final ServerSubLevel member)
                            || member.isRemoved()) {
                        return;
                    }
                    members.put(member, entry.getValue());
                }

                CrossScaleWelds.beginScaleCommand();
                try {
                    for (final Map.Entry<ServerSubLevel, Double> entry : members.entrySet()) {
                        final ServerSubLevel member = entry.getKey();
                        forceScale(
                                member,
                                entry.getValue(),
                                gameTime,
                                member == subLevel ? anchorLocalPoint : null,
                                false,
                                effectiveMode,
                                effectiveLimits);
                    }
                } finally {
                    CrossScaleWelds.endScaleCommand();
                }
                return;
            }
        }

        final double effectiveScale = compressed(requested)
                && CompressionBlacklist.find(subLevel, gameTime).blocked()
                ? PocketSized.FULL_SCALE
                : requested;

        PocketTrace.scale(
                "forceScale {} -> {} by {}", subLevel.getUniqueId(), effectiveScale, PocketTrace.caller());

        if (propagateJoints && !SimulatedRopeScaleBoundary.blocksTransition(subLevel, effectiveScale)
                && !JointScalePropagation.onCommanded(subLevel, effectiveScale, effectiveMode, effectiveLimits)) {
            return;
        }
        ScalePhysicsTransitions.setMode(subLevel, effectiveMode);

        final long expires = gameTime + 20L * 20L;
        registerExternalCommandUntil(
                subLevel,
                new ForcedScaleSource(subLevel, effectiveScale, expires, anchorLocalPoint, effectiveLimits),
                expires);
        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        state.requestedScale(effectiveScale);
    }

    public static void adoptRestoredScale(final ServerSubLevel subLevel, final double scale) {
        final double canonical = CompressionStage.snap(scale);
        PocketTrace.scale(
                "adoptRestoredScale {} requested={} canonical={}",
                PocketTrace.context(subLevel), scale, canonical);
        final ScaleState.ServerState state = ScaleState.restoreSettledState(subLevel, canonical);
        forcePoseScale(subLevel, canonical);

        if (!SubLevelPhysicsSystem.IN_PHYSICS_STEP) {
            final var raw = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(subLevel.getLevel());
            if (raw instanceof final ServerSubLevelContainer container) {
                PocketTrace.enter("PhysicsPipeline.onStatsChanged(adoptRestored)",
                        "scale=" + canonical, PocketTrace.context(subLevel));
                container.physicsSystem().getPipeline().onStatsChanged(subLevel);
                PocketTrace.exit("PhysicsPipeline.onStatsChanged(adoptRestored)");
                ScaleState.captureServerBounds(subLevel);
            }
        }

        subLevel.updateLastPose();
        ScalePersistence.persist(subLevel, state);
    }

    public static void adoptSplitScale(final ServerSubLevel subLevel, final double inheritedScale) {
        final double canonical = CompressionStage.snap(inheritedScale);
        PocketTrace.scale(
                "adoptSplitScale {} inherited={} canonical={}",
                PocketTrace.context(subLevel), inheritedScale, canonical);
        final ScaleState.ServerState state = ScaleState.restoreSettledState(subLevel, canonical);
        forcePoseScale(subLevel, canonical);
        subLevel.updateLastPose();
        ScalePersistence.persist(subLevel, state);

        ScaleState.clearServerBounds(subLevel.getUniqueId());
        PocketTrace.scale(
                "deferred split collider refresh until normal physics tick {} bodyId={}",
                PocketTrace.context(subLevel),
                com.misterblusky9.pocket.physics.RapierBridge.bodyId(subLevel));
        ScaleNetwork.sendScale(subLevel, canonical, canonical, true);
    }

    public static void tickServer(final ServerSubLevelContainer container) {
        final CrossScaleWelds.WeldGraph weldGraph = CrossScaleWelds.snapshot(container);
        final Map<UUID, CommandChoice> weldChoices = new HashMap<>();
        final Set<UUID> weldedMembers = weldGraph.hasWelds()
                ? prepareWeldChoices(container, weldGraph, weldChoices)
                : Set.of();
        if (weldGraph.hasWelds()) normalizeWeldChoices(container, weldGraph, weldChoices);
        final Map<UUID, Double> weldStepFactors = weldGraph.hasWelds()
                ? weldStepFactors(container, weldGraph)
                : Map.of();

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            // The moon carries its own scale; it is not a compression target.
            if (com.misterblusky9.pocket.moon.MoonSubLevels.isMoon(subLevel)) continue;

            final boolean welded = weldedMembers.contains(subLevel.getUniqueId());
            CommandChoice choice = welded
                    ? weldChoices.get(subLevel.getUniqueId())
                    : commandSource(subLevel, subLevel.getLevel().getGameTime());
            final boolean alreadyManaged = ScaleState.hasServerState(subLevel.getUniqueId());
            final boolean physicallyCompressed = ScaleState.isScaled(subLevel);
            if (choice == null && !alreadyManaged && !physicallyCompressed) continue;

            final ScaleState.ServerState state = ScaleState.serverState(subLevel);

            if (welded && !weldGraph.complete(subLevel.getUniqueId())) {
                forcePoseScale(subLevel, state.currentScale());
                if (state.needsPersistence()) ScalePersistence.persist(subLevel, state);
                continue;
            }

            if (!welded && couldBeCompressed(state, choice)) {
                final CompressionBlacklist.Result noShrink = CompressionBlacklist.find(
                        subLevel, subLevel.getLevel().getGameTime());
                if (noShrink.blocked()) {
                    if (choice != null) choice.source().setJamMessage(noShrink.message());
                    choice = new CommandChoice(new NoShrinkSource(subLevel), PocketSized.FULL_SCALE);
                }
            }

            if (choice != null) state.requestedScale(choice.scale());

            if (choice != null && !choice.source().stepwiseTransitions()
                    && state.transitioning()
                    && !sameScale(state.transitionScale(), state.requestedScale())) {
                state.beginTransition(
                        state.requestedScale(),
                        state.currentScale(),
                        choice.source().transitionSpeedFactor());
            }

            if (!state.transitioning() && choice != null
                    && !sameScale(state.stableScale(), state.requestedScale())) {
                beginNextStage(container, subLevel, state, choice);
            }

            final boolean transitioning = state.transitioning();
            final double target = state.goalScale();
            final double previous = state.currentScale();
            final double stepFactor = weldStepFactors.getOrDefault(
                    subLevel.getUniqueId(), rawStepFactorFor(subLevel));
            final double computed = squeezeStep(subLevel, state, transitioning, target, stepFactor);

            final double next;
            if (PocketSized.isValidScale(computed)) {
                next = computed;
            } else {
                PocketTrace.warn(
                        "rejected invalid computed scale {} (previous={} target={} transitioning={}) {}",
                        computed, previous, target, transitioning, PocketTrace.context(subLevel));
                next = previous;
            }
            final boolean scaleChanged = Math.abs(next - previous) > PocketSized.EPSILON;

            final PhysicsPipeline tracePipeline = container.physicsSystem().getPipeline();
            if (scaleChanged) {
                logScaleTransition(tracePipeline, subLevel, previous, next, target, choice);
            }

            state.currentScale(next);
            if (scaleChanged) {
                applyScaleAroundAnchor(container, subLevel, previous, next, choice == null ? null : choice.source());
            } else {
                forcePoseScale(subLevel, next);
            }

            final boolean reachedTarget = transitioning && Math.abs(next - target) <= PocketSized.EPSILON;
            if (reachedTarget) {
                PocketTrace.scale(
                        "stageSettled {} scale={}", PocketTrace.context(subLevel), target);
                state.currentScale(target);
                state.stableScale(target);
                state.endTransition();
                forcePoseScale(subLevel, target);
                if (welded) CrossScaleWelds.restageWorldWelds(subLevel, target);
                if (choice != null) {
                    choice.source().clearJamMessage();
                    choice.source().onTransitionCompleted(subLevel, target);
                } else {
                    state.requestedScale(target);
                }
            }

            final boolean boundsChanged = ScaleState.serverBoundsChanged(subLevel);
            final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

            if (!sameScale(next, PocketSized.FULL_SCALE)) {
                KinematicCollisionSuppression.ensureSuppressed(subLevel, pipeline);
            } else {
                KinematicCollisionSuppression.ensureRestored(subLevel, pipeline);
            }

            ScalePhysicsTransitions.drive(
                    subLevel,
                    previous,
                    state.currentScale(),
                    target,
                    scaleChanged,
                    reachedTarget,
                    boundsChanged);

            if (state.needsPersistence()) ScalePersistence.persist(subLevel, state);

            if (scaleChanged || reachedTarget) {
                ScaleNetwork.sendScale(subLevel, state.currentScale(), target);
            }
        }

        com.misterblusky9.pocket.tweezers.TweezerSessions.tick(container);

        SubLevelParentage.propagate(container);

        com.misterblusky9.pocket.physics.ConstraintRefresh.refreshStale(container);

        if (container.getLevel() instanceof final net.minecraft.server.level.ServerLevel serverLevel) {
            com.misterblusky9.pocket.compat.simulated.WeldedAssembly.tick(container);
            com.misterblusky9.pocket.compat.simulated.WeldRuntime.tick(serverLevel, container);
        }
    }

    public static boolean sameScale(final double a, final double b) {
        return Math.abs(a - b) <= PocketSized.EPSILON;
    }

    private static boolean compressed(final double scale) {
        return scale < PocketSized.FULL_SCALE - PocketSized.EPSILON;
    }

    private static boolean couldBeCompressed(final ScaleState.ServerState state, final CommandChoice choice) {
        return compressed(state.currentScale())
                || compressed(state.stableScale())
                || compressed(state.requestedScale())
                || state.transitioning() && compressed(state.transitionScale())
                || choice != null && compressed(choice.scale());
    }

    private static Set<UUID> prepareWeldChoices(
            final ServerSubLevelContainer container,
            final CrossScaleWelds.WeldGraph graph,
            final Map<UUID, CommandChoice> choices
    ) {
        final Set<UUID> welded = new HashSet<>();
        final Set<UUID> visited = new HashSet<>();
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            if (candidate == null || candidate.isRemoved() || candidate.getUniqueId() == null) continue;
            final UUID id = candidate.getUniqueId();
            if (visited.contains(id)) continue;
            if (!graph.isEndpoint(id)) continue;
            final Set<UUID> component = graph.component(id);
            visited.addAll(component);
            welded.addAll(component);
            for (final UUID memberId : component) {
                if (!(container.getSubLevel(memberId) instanceof final ServerSubLevel member)
                        || member.isRemoved()) {
                    continue;
                }
                final CommandChoice choice = effectiveCommandSource(member, member.getLevel().getGameTime());
                if (choice != null) choices.put(memberId, choice);
            }
        }
        return welded;
    }

    private static CommandChoice effectiveCommandSource(
            final ServerSubLevel subLevel,
            final long gameTime
    ) {
        final CommandChoice choice = commandSource(subLevel, gameTime);
        final boolean alreadyManaged = ScaleState.hasServerState(subLevel.getUniqueId());
        final boolean physicallyCompressed = ScaleState.isScaled(subLevel);
        if (choice == null && !alreadyManaged && !physicallyCompressed) return null;

        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        if (!couldBeCompressed(state, choice)) return choice;

        final CompressionBlacklist.Result noShrink = CompressionBlacklist.find(subLevel, gameTime);
        if (!noShrink.blocked()) return choice;
        if (choice != null) choice.source().setJamMessage(noShrink.message());
        return new CommandChoice(new NoShrinkSource(subLevel), PocketSized.FULL_SCALE);
    }

    private static void normalizeWeldChoices(
            final ServerSubLevelContainer container,
            final CrossScaleWelds.WeldGraph graph,
            final Map<UUID, CommandChoice> choices
    ) {
        final Set<UUID> visited = new HashSet<>();
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            if (candidate == null || candidate.isRemoved() || candidate.getUniqueId() == null) continue;
            final UUID candidateId = candidate.getUniqueId();
            if (visited.contains(candidateId)) continue;

            if (!graph.isEndpoint(candidateId)) continue;
            final Set<UUID> component = graph.component(candidateId);
            visited.addAll(component);
            if (!graph.complete(candidateId)) {
                jamSources(choices, component, CrossScaleWelds.SCALE_LIMIT);
                component.forEach(choices::remove);
                continue;
            }

            double ratio = Double.NaN;
            ServerSubLevel driver = null;
            CommandChoice driverChoice = null;
            boolean conflict = false;

            for (final UUID id : component) {
                final CommandChoice choice = choices.get(id);
                if (choice == null) continue;
                if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                    conflict = true;
                    break;
                }
                final double memberRatio = choice.scale() / CrossScaleWelds.commandedScale(member);
                if (Double.isNaN(ratio)) {
                    ratio = memberRatio;
                } else if (Math.abs(memberRatio / ratio - 1.0D) > RATIO_TOLERANCE) {
                    conflict = true;
                    break;
                }
                final boolean anchored = choice.source().anchorLocalPoint() != null;
                final boolean driverAnchored = driverChoice != null && driverChoice.source().anchorLocalPoint() != null;
                if (driver == null || anchored && !driverAnchored
                        || anchored == driverAnchored && id.compareTo(driver.getUniqueId()) < 0) {
                    driver = member;
                    driverChoice = choice;
                }
            }

            if (driver == null || driverChoice == null) continue;
            if (conflict) {
                jamSources(choices, component, CrossScaleWelds.SCALE_CONFLICT);
                component.forEach(choices::remove);
                continue;
            }

            final CrossScaleWelds.ScalePlan plan = CrossScaleWelds.planScale(
                    driver, driverChoice.scale(), driver.getLevel().getGameTime(),
                    driverChoice.source().scaleLimits());
            if (!plan.allowed()) {
                jamSources(choices, component, CrossScaleWelds.SCALE_LIMIT);
                component.forEach(choices::remove);
                continue;
            }

            final WeldCommandGate gate = new WeldCommandGate(driver, driverChoice.source(), plan.goals().get(driver.getUniqueId()));
            for (final UUID id : component) {
                if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                    jamSources(choices, component, CrossScaleWelds.SCALE_LIMIT);
                    component.forEach(choices::remove);
                    break;
                }
                final Double goal = plan.goals().get(id);
                if (goal == null) {
                    jamSources(choices, component, CrossScaleWelds.SCALE_LIMIT);
                    component.forEach(choices::remove);
                    break;
                }
                choices.put(id, new CommandChoice(new WeldCommandSource(member, gate, goal), goal));
            }
        }
    }

    private static void jamSources(
            final Map<UUID, CommandChoice> choices,
            final Set<UUID> component,
            final String message
    ) {
        for (final UUID id : component) {
            final CommandChoice choice = choices.get(id);
            if (choice != null) choice.source().setJamMessage(message);
        }
    }

    private static Map<UUID, Double> weldStepFactors(
            final ServerSubLevelContainer container,
            final CrossScaleWelds.WeldGraph graph
    ) {
        final Map<UUID, Double> result = new HashMap<>();
        final Set<UUID> visited = new HashSet<>();
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            if (candidate == null || candidate.isRemoved() || candidate.getUniqueId() == null) continue;
            final UUID candidateId = candidate.getUniqueId();
            if (visited.contains(candidateId)) continue;

            if (!graph.isEndpoint(candidateId)) continue;
            final Set<UUID> component = graph.component(candidateId);
            visited.addAll(component);
            if (!graph.complete(candidateId)) continue;

            double factor = Double.POSITIVE_INFINITY;
            boolean complete = true;
            for (final UUID id : component) {
                if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                    complete = false;
                    break;
                }
                factor = Math.min(factor, rawStepFactorFor(member));
            }
            if (!complete || !Double.isFinite(factor)) continue;
            for (final UUID id : component) result.put(id, factor);
        }
        return result;
    }

    private static void logScaleTransition(
            final PhysicsPipeline pipeline,
            final ServerSubLevel subLevel,
            final double oldScale,
            final double newScale,
            final double target,
            final CommandChoice choice
    ) {
        if (!PocketTrace.SCALE) return;

        final Vector3d linear = pipeline.getLinearVelocity(subLevel, new Vector3d());
        final Vector3d angular = pipeline.getAngularVelocity(subLevel, new Vector3d());

        PocketTrace.scale(
                "transition {} old={} new={} target={} pos={} linVel={} angVel={} source={}",
                PocketTrace.context(subLevel),
                oldScale,
                newScale,
                target,
                subLevel.logicalPose().position(),
                linear,
                angular,
                choice == null ? "none" : choice.source().getClass().getSimpleName());
    }

    private static void beginNextStage(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel,
            final ScaleState.ServerState state,
            final CommandChoice choice
    ) {
        final double from = state.stableScale();
        final double to = choice.source().stepwiseTransitions()
                ? CompressionStage.stepToward(from, state.requestedScale())
                : state.requestedScale();
        if (sameScale(to, from)) return;

        if (SimulatedRopeScaleBoundary.blocksTransition(subLevel, to)) {
            choice.source().setJamMessage("Remove rope before scaling");
            return;
        }

        if (!choice.source().tryConsumeTransition(subLevel, from, to)) {
            choice.source().setJamMessage("Needs Ender Dust");
            return;
        }

        choice.source().clearJamMessage();
        PocketTrace.scale(
                "beginStage {} from={} to={} requested={} fromScale={}",
                PocketTrace.context(subLevel), from, to, state.requestedScale(), state.currentScale());
        state.beginTransition(to, state.currentScale(), choice.source().transitionSpeedFactor());
    }

    public static void enforceClientScale(final ClientSubLevel subLevel) {
        final double scale = ScaleState.getClientScale(subLevel);
        final double existing = subLevel.logicalPose().scale().x();
        if (Math.abs(existing - scale) <= PocketSized.EPSILON) return;
        forcePoseScale(subLevel, scale);
        subLevel.forceUpdateBounds();
    }

    private static CommandChoice commandSource(final ServerSubLevel subLevel, final long gameTime) {
        ScaleCommandSource best = null;
        double deepest = PocketSized.MAX_SCALE;

        final boolean suspended = ManualScaleOverride.isSuspended(subLevel.getUniqueId(), gameTime);

        for (final BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof final ScaleCommandSource source) || source.isRemoved()) continue;
            if (suspended && source.yieldsToManualOverride()) continue;

            final double command = source.commandedScale();
            if (!Double.isFinite(command)) continue;

            if (best == null || command < deepest - PocketSized.EPSILON) {
                best = source;
                deepest = command;
            }
        }

        final ExternalCommand external = EXTERNAL_COMMANDS.get(subLevel.getUniqueId());
        if (external != null) {
            if (external.source().isRemoved() || gameTime > external.validUntilTick()) {
                EXTERNAL_COMMANDS.remove(subLevel.getUniqueId(), external);
            } else {
                final double externalScale =
                        suspended && external.source().yieldsToManualOverride()
                                ? ScaleCommandSource.NO_COMMAND
                                : external.source().commandedScale();
                if (Double.isFinite(externalScale)) {
                    return new CommandChoice(external.source(), externalScale);
                }
            }
        }

        if (best == null) return null;

        final PocketMetrics metrics = PocketMetrics.measureForCompression(subLevel, gameTime);
        if (metrics.blocks() > PocketSized.MAX_COMPRESSED_BLOCKS) {
            best.setJamMessage("Hard limit exceeded: " + metrics.blocks() + " blocks");
            return new CommandChoice(best, PocketSized.FULL_SCALE);
        }

        best.clearJamMessage();
        return new CommandChoice(best, deepest);
    }

    private static double squeezeStep(
            final ServerSubLevel subLevel,
            final ScaleState.ServerState state,
            final boolean transitioning,
            final double target,
            final double stepFactor
    ) {
        final double current = state.currentScale();
        if (Math.abs(target - current) <= PocketSized.EPSILON) return target;

        if (!transitioning) return target;

        state.tickTransition();
        return ScaleTransitionCurve.interpolate(
                state.transitionFrom(),
                target,
                state.transitionTicks(),
                stepFactor * ScaleTransitionCurve.sanitizeSpeedFactor(state.transitionSpeedFactor())
        );
    }

    private static double rawStepFactorFor(final ServerSubLevel subLevel) {
        final var tracker = subLevel.getMassTracker();
        if (tracker == null) return 1.0D;

        final double mass = tracker.getMass();
        if (!Double.isFinite(mass) || mass <= 0.0D) return 1.0D;

        final double factor = Math.cbrt(REFERENCE_MASS / Math.max(mass, 1.0D));
        return Math.max(MIN_STEP_FACTOR, Math.min(MAX_STEP_FACTOR, factor));
    }

    private static void applyScaleAroundAnchor(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel,
            final double previousScale,
            final double nextScale,
            final ScaleCommandSource source
    ) {
        if (source instanceof final WeldCommandSource weld) {
            applyWeldScale(container, subLevel, previousScale, nextScale, weld.gate.worldAnchor);
            return;
        }

        final boolean rivet = SimulatedCoastersRivetCompat.isRivetSubLevel(subLevel);
        final Vector3d localAnchor = rivet
                ? SimulatedCoastersRivetCompat.attachmentAnchor(subLevel)
                : source == null ? null : source.anchorLocalPoint();
        if (localAnchor == null) {
            applyScaleAroundBodyPivot(container, subLevel, previousScale, nextScale);
            return;
        }

        final Vector3d oldWorldAnchor = worldPoint(subLevel, localAnchor, previousScale);
        subLevel.logicalPose().scale().set(nextScale, nextScale, nextScale);
        final Vector3d relativeAtNewScale = localRelativeWorld(subLevel, localAnchor, nextScale);
        Vector3d correctedPosition = new Vector3d(oldWorldAnchor).sub(relativeAtNewScale);

        if (nextScale > previousScale && !rivet) {
            correctedPosition = ExpansionClearance.resolve(
                    subLevel, correctedPosition, previousScale, nextScale);
        }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        PocketTrace.enter("PhysicsPipeline.teleport(anchored)",
                "to=" + correctedPosition,
                "scale=" + previousScale + "->" + nextScale,
                PocketTrace.context(subLevel));
        pipeline.teleport(subLevel, correctedPosition, subLevel.logicalPose().orientation());
        PocketTrace.exit("PhysicsPipeline.teleport(anchored) uuid=" + subLevel.getUniqueId());
        subLevel.updateBoundingBox();
    }

    private static void applyWeldScale(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel,
            final double previousScale,
            final double nextScale,
            final Vector3dc worldAnchor
    ) {
        if (worldAnchor == null || !Double.isFinite(previousScale) || previousScale <= 0.0D) {
            forcePoseScale(subLevel, nextScale);
            return;
        }

        final double factor = nextScale / previousScale;
        if (!Double.isFinite(factor) || factor <= 0.0D) {
            forcePoseScale(subLevel, nextScale);
            return;
        }

        final Vector3d targetPosition = new Vector3d(subLevel.logicalPose().position())
                .sub(worldAnchor)
                .mul(factor)
                .add(worldAnchor);
        subLevel.logicalPose().scale().set(nextScale, nextScale, nextScale);
        container.physicsSystem().getPipeline().teleport(
                subLevel, targetPosition, subLevel.logicalPose().orientation());
        subLevel.logicalPose().position().set(targetPosition);
        subLevel.updateBoundingBox();
    }

    private static Vector3d worldPoint(final ServerSubLevel subLevel, final Vector3dc local, final double scale) {
        final Vector3d relative = localRelativeWorld(subLevel, local, scale);
        return relative.add(subLevel.logicalPose().position());
    }

    private static Vector3d localRelativeWorld(final ServerSubLevel subLevel, final Vector3dc local, final double scale) {
        final Vector3d relative = new Vector3d(local)
                .sub(subLevel.logicalPose().rotationPoint())
                .mul(scale);
        subLevel.logicalPose().orientation().transform(relative);
        return relative;
    }

    private static void applyScaleAroundBodyPivot(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel,
            final double previousScale,
            final double nextScale
    ) {
        subLevel.logicalPose().scale().set(nextScale, nextScale, nextScale);
        subLevel.updateBoundingBox();

        if (nextScale <= previousScale) return;

        final Vector3d desired = new Vector3d(subLevel.logicalPose().position());
        final Vector3d corrected = ExpansionClearance.resolve(
                subLevel, desired, previousScale, nextScale);
        if (corrected.distanceSquared(desired) <= 1.0E-20D) return;

        PocketTrace.enter("PhysicsPipeline.teleport(expansionClearance)",
                "to=" + corrected,
                "scale=" + previousScale + "->" + nextScale,
                PocketTrace.context(subLevel));
        container.physicsSystem().getPipeline().teleport(
                subLevel, corrected, subLevel.logicalPose().orientation());
        PocketTrace.exit("PhysicsPipeline.teleport(expansionClearance) uuid=" + subLevel.getUniqueId());
        subLevel.logicalPose().position().set(corrected);
        subLevel.updateBoundingBox();
    }

    private static void forcePoseScale(final SubLevel subLevel, final double scale) {
        if (!PocketSized.isValidScale(scale)) {
            PocketTrace.warn(
                    "refusing to write invalid pose scale {} uuid={} thread={}",
                    scale, subLevel.getUniqueId(), Thread.currentThread().getName());
            return;
        }
        subLevel.logicalPose().scale().set(scale, scale, scale);
        subLevel.updateBoundingBox();
    }

    private record CommandChoice(ScaleCommandSource source, double scale) {
        private CommandChoice {
            scale = CompressionStage.snap(scale);
        }
    }

    private static final class WeldCommandGate {
        private final ServerSubLevel driver;
        private final ScaleCommandSource source;
        private final Double goal;
        private final Vector3d worldAnchor;
        private Boolean consumed;

        private WeldCommandGate(
                final ServerSubLevel driver,
                final ScaleCommandSource source,
                final Double goal
        ) {
            this.driver = driver;
            this.source = source;
            this.goal = goal;
            final Vector3d grid = CrossScaleWelds.worldAnchorPoint(
                    ServerSubLevelContainer.getContainer(driver.getLevel()), driver.getUniqueId());
            final Vector3d localAnchor = source.anchorLocalPoint();
            this.worldAnchor = grid != null
                    ? grid
                    : localAnchor == null
                    ? new Vector3d(driver.logicalPose().position())
                    : worldPoint(driver, localAnchor, ScaleState.serverState(driver).currentScale());
        }

        private boolean consume() {
            if (this.consumed != null) return this.consumed;
            if (this.driver.isRemoved() || this.goal == null || this.source.isRemoved()) {
                this.consumed = Boolean.FALSE;
                return false;
            }
            final ScaleState.ServerState state = ScaleState.serverState(this.driver);
            final double from = state.stableScale();
            final double to = this.source.stepwiseTransitions()
                    ? CompressionStage.stepToward(from, this.goal)
                    : this.goal;
            this.consumed = this.source.tryConsumeTransition(this.driver, from, to);
            return this.consumed;
        }
    }

    private static final class WeldCommandSource implements ScaleCommandSource {
        private final ServerSubLevel member;
        private final WeldCommandGate gate;
        private final double goal;

        private WeldCommandSource(
                final ServerSubLevel member,
                final WeldCommandGate gate,
                final double goal
        ) {
            this.member = member;
            this.gate = gate;
            this.goal = goal;
        }

        @Override public double commandedScale() { return this.goal; }
        @Override public boolean stepwiseTransitions() { return this.gate.source.stepwiseTransitions(); }
        @Override public boolean yieldsToManualOverride() { return this.gate.source.yieldsToManualOverride(); }
        @Override public double transitionSpeedFactor() { return this.gate.source.transitionSpeedFactor(); }
        @Override public ScaleLimits scaleLimits() { return this.gate.source.scaleLimits(); }
        @Override public Vector3d anchorLocalPoint() {
            return this.member == this.gate.driver ? this.gate.source.anchorLocalPoint() : null;
        }
        @Override public boolean tryConsumeTransition(
                final ServerSubLevel subLevel,
                final double from,
                final double to
        ) { return this.gate.consume(); }
        @Override public void onTransitionCompleted(
                final ServerSubLevel subLevel,
                final double scale
        ) {
            if (this.member == this.gate.driver) this.gate.source.onTransitionCompleted(subLevel, scale);
        }
        @Override public void setJamMessage(final String message) { this.gate.source.setJamMessage(message); }
        @Override public void clearJamMessage() { this.gate.source.clearJamMessage(); }
        @Override public boolean isRemoved() { return this.member.isRemoved() || this.gate.source.isRemoved(); }
    }

    private static final class NoShrinkSource implements ScaleCommandSource {
        private final ServerSubLevel subLevel;

        private NoShrinkSource(final ServerSubLevel subLevel) {
            this.subLevel = subLevel;
        }

        @Override public double commandedScale() { return PocketSized.FULL_SCALE; }
        @Override public boolean yieldsToManualOverride() { return false; }
        @Override public boolean isRemoved() { return this.subLevel.isRemoved(); }
    }

    private static final class ForcedScaleSource implements ScaleCommandSource {
        private final ServerSubLevel subLevel;
        private final double scale;
        private final long expiresAt;
        private final Vector3d anchor;
        private final ScaleLimits limits;

        private ForcedScaleSource(
                final ServerSubLevel subLevel,
                final double scale,
                final long expiresAt,
                final Vector3d anchor,
                final ScaleLimits limits
        ) {
            this.subLevel = subLevel;
            this.scale = scale;
            this.expiresAt = expiresAt;
            this.anchor = anchor;
            this.limits = limits;
        }

        @Override public double commandedScale() { return this.scale; }
        @Override public ScaleLimits scaleLimits() { return this.limits; }
        @Override public boolean yieldsToManualOverride() { return false; }
        @Override public Vector3d anchorLocalPoint() {
            return this.anchor == null ? null : new Vector3d(this.anchor);
        }
        @Override public boolean tryConsumeTransition(final ServerSubLevel sub, final double from, final double to) { return true; }
        @Override public boolean isRemoved() { return this.subLevel.isRemoved() || this.subLevel.getLevel().getGameTime() > this.expiresAt; }
    }

    private record ExternalCommand(ScaleCommandSource source, long validUntilTick) {}

    private ScaleController() {}
}
