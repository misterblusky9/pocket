package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersRivetCompat;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.persistence.ScalePersistence;
import com.misterblusky9.pocket.physics.ScalePhysicsTransitions;
import com.misterblusky9.pocket.physics.ExpansionClearance;
import com.misterblusky9.pocket.physics.KinematicCollisionSuppression;
import com.misterblusky9.pocket.physics.SubLevelLoadGuard;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleController {
    private static final long EXTERNAL_COMMAND_TTL = 8L;

    private static final double STEP_TICKS = 9.0D;

    private static final double REFERENCE_MASS = 5_000.0D;
    private static final double MIN_STEP_FACTOR = 0.20D;
    private static final double MAX_STEP_FACTOR = 1.50D;
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
        if (subLevel == null || stage == null) return;

        final ScalePhysicsMode effectiveMode =
                physicsMode == null ? ScalePhysicsMode.TRACKING : physicsMode;
        final CompressionStage effectiveStage = stage.isCompressed()
                && CompressionBlacklist.find(subLevel, gameTime).blocked()
                ? CompressionStage.NORMAL
                : stage;

        PocketTrace.scale(
                "forceStage {} -> {} by {}", subLevel.getUniqueId(), effectiveStage, PocketTrace.caller());

        ScalePhysicsTransitions.setMode(subLevel, effectiveMode);
        if (propagateJoints) JointScalePropagation.onCommanded(subLevel, effectiveStage, effectiveMode);

        final long expires = gameTime + 20L * 20L;
        registerExternalCommandUntil(
                subLevel, new ForcedStageSource(subLevel, effectiveStage, expires, anchorLocalPoint), expires);
        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        state.requestedStage(effectiveStage);
    }

    public static void adoptRestoredScale(final ServerSubLevel subLevel, final double scale) {
        final CompressionStage stage = CompressionStage.nearest(scale);
        final double canonical = stage.scale();
        PocketTrace.scale(
                "adoptRestoredScale {} requested={} canonical={} stage={}",
                PocketTrace.context(subLevel), scale, canonical, stage);
        final ScaleState.ServerState state = ScaleState.restoreServerState(
                subLevel, canonical, stage, stage, null
        );
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
        final CompressionStage stage = CompressionStage.nearest(inheritedScale);
        PocketTrace.scale(
                "adoptSplitScale {} inherited={} stage={}",
                PocketTrace.context(subLevel), inheritedScale, stage);
        final ScaleState.ServerState state = ScaleState.restoreServerState(
                subLevel, stage.scale(), stage, stage, null
        );
        forcePoseScale(subLevel, stage.scale());
        subLevel.updateLastPose();
        ScalePersistence.persist(subLevel, state);

        ScaleState.clearServerBounds(subLevel.getUniqueId());
        PocketTrace.scale(
                "deferred split collider refresh until normal physics tick {} bodyId={}",
                PocketTrace.context(subLevel),
                com.misterblusky9.pocket.physics.RapierBridge.bodyId(subLevel));
        ScaleNetwork.sendScale(subLevel, stage.scale(), stage.scale(), true);
    }

    public static void tickServer(final ServerSubLevelContainer container) {
        SubLevelLoadGuard.clearIfStaleOn(Thread.currentThread());

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            // The moon carries its own scale; it is not a compression target.
            if (com.misterblusky9.pocket.moon.MoonSubLevels.isMoon(subLevel)) continue;

            CommandChoice choice = commandSource(subLevel, subLevel.getLevel().getGameTime());
            final boolean alreadyManaged = ScaleState.hasServerState(subLevel.getUniqueId());
            final boolean physicallyCompressed = ScaleState.isScaled(subLevel);
            if (choice == null && !alreadyManaged && !physicallyCompressed) continue;

            final ScaleState.ServerState state = ScaleState.serverState(subLevel);

            final boolean couldBeCompressed = state.currentScale() < 1.0D - PocketSized.EPSILON
                    || state.stableStage().isCompressed()
                    || state.requestedStage().isCompressed()
                    || (state.transitionStage() != null && state.transitionStage().isCompressed())
                    || (choice != null && choice.stage().isCompressed());
            if (couldBeCompressed) {
                final CompressionBlacklist.Result noShrink = CompressionBlacklist.find(
                        subLevel, subLevel.getLevel().getGameTime());
                if (noShrink.blocked()) {
                    if (choice != null) choice.source().setJamMessage(noShrink.message());
                    choice = new CommandChoice(new NoShrinkSource(subLevel), CompressionStage.NORMAL);
                }
            }

            if (choice != null) state.requestedStage(choice.stage());

            if (choice != null && !choice.source().stepwiseTransitions()
                    && state.transitionStage() != null
                    && state.transitionStage() != state.requestedStage()) {
                state.beginTransition(
                        state.requestedStage(),
                        state.currentScale(),
                        choice.source().transitionSpeedFactor());
            }

            if (state.transitionStage() == null && choice != null
                    && state.stableStage() != state.requestedStage()) {
                beginNextStage(container, subLevel, state, choice);
            }

            final CompressionStage activeTarget = state.transitionStage();
            final double target = activeTarget == null
                    ? state.stableStage().scale()
                    : activeTarget.scale();
            final double previous = state.currentScale();
            final double computed = squeezeStep(subLevel, state, activeTarget, target);

            final double next;
            if (PocketSized.isValidScale(computed)) {
                next = computed;
            } else {
                PocketTrace.warn(
                        "rejected invalid computed scale {} (previous={} target={} stage={}) {}",
                        computed, previous, target, activeTarget, PocketTrace.context(subLevel));
                next = previous;
            }
            final boolean scaleChanged = Math.abs(next - previous) > PocketSized.EPSILON;

            final PhysicsPipeline tracePipeline = container.physicsSystem().getPipeline();
            if (scaleChanged) {
                logScaleTransition(tracePipeline, subLevel, previous, next, activeTarget, choice);
            }

            state.currentScale(next);
            if (scaleChanged) {
                applyScaleAroundAnchor(container, subLevel, previous, next, choice == null ? null : choice.source());
            } else {
                forcePoseScale(subLevel, next);
            }

            final boolean reachedTarget = activeTarget != null
                    && Math.abs(next - activeTarget.scale()) <= PocketSized.EPSILON;
            if (reachedTarget) {
                PocketTrace.scale(
                        "stageSettled {} stage={} scale={}",
                        PocketTrace.context(subLevel), activeTarget, activeTarget.scale());
                state.currentScale(activeTarget.scale());
                state.stableStage(activeTarget);
                state.transitionStage(null);
                forcePoseScale(subLevel, activeTarget.scale());
                if (choice != null) {
                    choice.source().clearJamMessage();
                    choice.source().onTransitionCompleted(subLevel, activeTarget);
                } else {
                    state.requestedStage(activeTarget);
                }
            }

            final boolean boundsChanged = ScaleState.serverBoundsChanged(subLevel);
            final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

            if (next < 1.0D - PocketSized.EPSILON) {
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

        ScaleLifecycle.reapDeparted(container);
    }

    private static void logScaleTransition(
            final PhysicsPipeline pipeline,
            final ServerSubLevel subLevel,
            final double oldScale,
            final double newScale,
            final CompressionStage activeTarget,
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
                activeTarget,
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
        final CompressionStage from = state.stableStage();
        final CompressionStage to = choice.source().stepwiseTransitions()
                ? from.stepToward(state.requestedStage())
                : state.requestedStage();
        if (to == from) return;

        if (!choice.source().tryConsumeTransition(subLevel, from, to)) {
            choice.source().setJamMessage("Needs Ender Dust");
            return;
        }

        choice.source().clearJamMessage();
        PocketTrace.scale(
                "beginStage {} from={} to={} requested={} fromScale={}",
                PocketTrace.context(subLevel), from, to, state.requestedStage(), state.currentScale());
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
        CompressionStage deepest = CompressionStage.NORMAL;

        final boolean suspended = ManualScaleOverride.isSuspended(subLevel.getUniqueId(), gameTime);

        for (final BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof final ScaleCommandSource source) || source.isRemoved()) continue;
            if (suspended && source.yieldsToManualOverride()) continue;

            final CompressionStage command = source.commandedStage();
            if (command == null) continue;

            if (best == null || command.depth() > deepest.depth()) {
                best = source;
                deepest = command;
            }
        }

        final ExternalCommand external = EXTERNAL_COMMANDS.get(subLevel.getUniqueId());
        if (external != null) {
            if (external.source().isRemoved() || gameTime > external.validUntilTick()) {
                EXTERNAL_COMMANDS.remove(subLevel.getUniqueId(), external);
            } else {
                final CompressionStage externalStage =
                        suspended && external.source().yieldsToManualOverride()
                                ? null
                                : external.source().commandedStage();
                if (externalStage != null) {
                    return new CommandChoice(external.source(), externalStage);
                }
            }
        }

        if (best == null) return null;

        final PocketMetrics metrics = PocketMetrics.measureForCompression(subLevel, gameTime);
        if (metrics.blocks() > PocketSized.MAX_COMPRESSED_BLOCKS) {
            best.setJamMessage("Hard limit exceeded: " + metrics.blocks() + " blocks");
            return new CommandChoice(best, CompressionStage.NORMAL);
        }

        best.clearJamMessage();
        return new CommandChoice(best, deepest);
    }

    private static double squeezeStep(
            final ServerSubLevel subLevel,
            final ScaleState.ServerState state,
            final CompressionStage activeTarget,
            final double target
    ) {
        final double current = state.currentScale();
        if (Math.abs(target - current) <= PocketSized.EPSILON) return target;

        if (activeTarget == null) return target;

        state.tickTransition();

        final double ticks = STEP_TICKS
                / Math.max(0.05D,
                        stepFactorFor(subLevel) * sanitizeSpeedFactor(state.transitionSpeedFactor()));
        final double progress = Math.min(1.0D, state.transitionTicks() / Math.max(1.0D, ticks));
        if (progress >= 1.0D) return target;

        final double eased = 1.0D - (1.0D - progress) * (1.0D - progress);
        final double from = state.transitionFrom();
        return PocketSized.clampScale(from + (target - from) * eased);
    }

    private static double sanitizeSpeedFactor(final double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0D) return 1.0D;
        return Math.min(4.0D, factor);
    }

    private static double stepFactorFor(final ServerSubLevel subLevel) {
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

    private record CommandChoice(ScaleCommandSource source, CompressionStage stage) {}

    private static final class NoShrinkSource implements ScaleCommandSource {
        private final ServerSubLevel subLevel;

        private NoShrinkSource(final ServerSubLevel subLevel) {
            this.subLevel = subLevel;
        }

        @Override public CompressionStage commandedStage() { return CompressionStage.NORMAL; }
        @Override public boolean yieldsToManualOverride() { return false; }
        @Override public boolean isRemoved() { return this.subLevel.isRemoved(); }
    }

    private static final class ForcedStageSource implements ScaleCommandSource {
        private final ServerSubLevel subLevel;
        private final CompressionStage stage;
        private final long expiresAt;
        private final Vector3d anchor;

        private ForcedStageSource(
                final ServerSubLevel subLevel,
                final CompressionStage stage,
                final long expiresAt,
                final Vector3d anchor
        ) {
            this.subLevel = subLevel;
            this.stage = stage;
            this.expiresAt = expiresAt;
            this.anchor = anchor;
        }

        @Override public CompressionStage commandedStage() { return this.stage; }
        @Override public boolean yieldsToManualOverride() { return false; }
        @Override public Vector3d anchorLocalPoint() {
            return this.anchor == null ? null : new Vector3d(this.anchor);
        }
        @Override public boolean tryConsumeTransition(final ServerSubLevel sub, final CompressionStage from, final CompressionStage to) { return true; }
        @Override public boolean isRemoved() { return this.subLevel.isRemoved() || this.subLevel.getLevel().getGameTime() > this.expiresAt; }
    }

    private record ExternalCommand(ScaleCommandSource source, long validUntilTick) {}

    private ScaleController() {}
}
