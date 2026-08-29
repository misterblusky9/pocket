package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.moon.MoonPhysicsTarget;
import com.misterblusky9.pocket.scale.ScaleState;
import com.misterblusky9.pocket.scale.SubLevelParentage;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConstraintRefresh {
    private record Tracked(
            PhysicsConstraintHandle handle,
            UUID bodyA,
            UUID bodyB,
            Vector3dc pivotA,
            Vector3dc pivotB,
            double scaleA,
            double scaleB,
            PhysicsConstraintConfiguration<?> configuration
    ) {}

    private static final ThreadLocal<Boolean> REPOINTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final List<Tracked> TRACKED = Collections.synchronizedList(new ArrayList<>());

    private static final List<Tracked> OUTDATED = new ArrayList<>();

    public static void record(
            final PhysicsConstraintHandle handle,
            final PhysicsPipelineBody bodyA,
            final PhysicsPipelineBody bodyB,
            final PhysicsConstraintConfiguration<?> originalConfiguration
    ) {
        if (handle == null) return;
        if (MoonPhysicsTarget.isBody(bodyA) || MoonPhysicsTarget.isBody(bodyB)) return;

        final UUID idA = idOf(bodyA);
        final UUID idB = idOf(bodyB);

        if (idA == null && idB == null) return;

        if (REPOINTING.get()) return;

        TRACKED.add(new Tracked(
                handle, idA, idB,
                pivotOf(bodyA), pivotOf(bodyB),
                scaleOf(bodyA), scaleOf(bodyB),
                originalConfiguration == null ? null : ConstraintConfigurations.copy(originalConfiguration)));
    }

    public static Set<UUID> neighbours(final UUID id) {
        if (id == null) return Set.of();

        final Set<UUID> found = new HashSet<>();
        synchronized (TRACKED) {
            for (final Tracked tracked : TRACKED) {
                if (!isUsable(tracked)) continue;
                if (id.equals(tracked.bodyA()) && tracked.bodyB() != null) found.add(tracked.bodyB());
                else if (id.equals(tracked.bodyB()) && tracked.bodyA() != null) found.add(tracked.bodyA());
            }
        }
        return found;
    }

    public static void refreshStale(final ServerSubLevelContainer container) {
        if (TRACKED.isEmpty()) return;

        OUTDATED.clear();

        synchronized (TRACKED) {
            final Iterator<Tracked> iterator = TRACKED.iterator();
            while (iterator.hasNext()) {
                final Tracked tracked = iterator.next();
                if (!isUsable(tracked)) {
                    PocketTrace.scale(
                            "discarding unusable tracked joint bodyA={} bodyB={} sceneLive={} knownRemoved={}",
                            tracked.bodyA(), tracked.bodyB(),
                            tracked.handle() instanceof RepointableConstraint owned
                                    && owned.pocket$isSceneLive(),
                            tracked.handle() instanceof RepointableConstraint owned
                                    && owned.pocket$isKnownRemoved());
                    iterator.remove();
                    continue;
                }
                if (tracked.configuration() == null) continue;
                if (!(tracked.handle() instanceof RepointableConstraint)) continue;

                if (!stillTracked(container, tracked.bodyA())
                        && !stillTracked(container, tracked.bodyB())) {
                    continue;
                }

                if (!ConstraintConfigurations.anchorsWouldMove(
                        tracked.configuration(),
                        bodyOf(container, tracked.bodyA()), tracked.pivotA(), tracked.scaleA(),
                        bodyOf(container, tracked.bodyB()), tracked.pivotB(), tracked.scaleB())) {
                    continue;
                }

                OUTDATED.add(tracked);
            }
        }

        for (final Tracked tracked : OUTDATED) {
            correct(container, tracked);
        }
    }

    private static void correct(final ServerSubLevelContainer container, final Tracked tracked) {
        final PhysicsPipelineBody bodyA = bodyOf(container, tracked.bodyA());
        final PhysicsPipelineBody bodyB = bodyOf(container, tracked.bodyB());

        if (tracked.bodyA() != null && bodyA == null) return;
        if (tracked.bodyB() != null && bodyB == null) return;
        if (bodyA == null && bodyB == null) return;

        final RepointableConstraint owned = (RepointableConstraint) tracked.handle();
        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

        PhysicsConstraintHandle replacement = null;
        REPOINTING.set(Boolean.TRUE);
        try {
            replacement = pipeline.addConstraint(bodyA, bodyB, cast(tracked.configuration()));
        } catch (final RuntimeException exception) {
            PocketTrace.warn(
                    "joint correction refused for bodyA={} bodyB={}: {}",
                    tracked.bodyA(), tracked.bodyB(), exception.toString());
        } finally {
            REPOINTING.set(Boolean.FALSE);
        }

        if (!(replacement instanceof final RepointableConstraint built)
                || built.pocket$isKnownRemoved()
                || !built.pocket$isSceneLive()
                || !replacement.isValid()) {
            PocketTrace.scale(
                    "joint correction unavailable this tick for bodyA={} bodyB={} - leaving it as it is",
                    tracked.bodyA(), tracked.bodyB());
            return;
        }

        final long retired = owned.pocket$nativeHandle();
        final long scene = owned.pocket$sceneHandle();

        owned.pocket$repoint(built.pocket$nativeHandle());

        RapierBridge.removeConstraint(scene, retired);

        owned.pocket$replayMotors();

        synchronized (TRACKED) {
            TRACKED.remove(tracked);
            TRACKED.add(new Tracked(
                    tracked.handle(), tracked.bodyA(), tracked.bodyB(),
                    pivotOf(bodyA), pivotOf(bodyB),
                    scaleOf(bodyA), scaleOf(bodyB), tracked.configuration()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends PhysicsConstraintHandle> PhysicsConstraintConfiguration<T> cast(
            final PhysicsConstraintConfiguration<?> configuration
    ) {
        return (PhysicsConstraintConfiguration<T>) configuration;
    }

    private static PhysicsPipelineBody bodyOf(final ServerSubLevelContainer container, final UUID id) {
        if (id == null) return null;
        return container.getSubLevel(id) instanceof final ServerSubLevel subLevel && !subLevel.isRemoved()
                ? subLevel
                : null;
    }

    private static boolean stillTracked(final ServerSubLevelContainer container, final UUID id) {
        if (id == null) return false;
        return container.getSubLevel(id) instanceof final ServerSubLevel subLevel && !subLevel.isRemoved();
    }

    private static boolean isUsable(final Tracked tracked) {
        if (tracked == null || !(tracked.handle() instanceof final RepointableConstraint owned)) {
            return false;
        }
        if (owned.pocket$isKnownRemoved() || !owned.pocket$isSceneLive()) return false;

        final boolean valid = tracked.handle().isValid();
        if (!valid) owned.pocket$markRemoved();
        return valid;
    }

    private static UUID idOf(final PhysicsPipelineBody body) {
        return body instanceof final ServerSubLevel subLevel ? subLevel.getUniqueId() : null;
    }

    private static Vector3dc pivotOf(final PhysicsPipelineBody body) {
        if (!(body instanceof final ServerSubLevel subLevel)) return null;

        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        return pivot == null ? null : new Vector3d(pivot);
    }

    private static double scaleOf(final PhysicsPipelineBody body) {
        return body instanceof final ServerSubLevel subLevel
                ? ScaleState.getServerScale(subLevel)
                : 1.0D;
    }

    private ConstraintRefresh() {}
}
