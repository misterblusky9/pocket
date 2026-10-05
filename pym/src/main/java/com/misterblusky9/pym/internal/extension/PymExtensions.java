package com.misterblusky9.pym.internal.extension;

import com.misterblusky9.pym.api.spi.JointConductor;
import com.misterblusky9.pym.api.spi.Participation;
import com.misterblusky9.pym.api.spi.ResizeFollower;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class PymExtensions {
    private static final List<ResizePolicy> POLICIES = new CopyOnWriteArrayList<>();
    private static final List<JointConductor> JOINT_CONDUCTORS = new CopyOnWriteArrayList<>();
    private static final List<ResizeFollower> RESIZE_FOLLOWERS = new CopyOnWriteArrayList<>();
    private static final List<Participation> PARTICIPATION = new CopyOnWriteArrayList<>();
    private static final List<ScaleCoupling> COUPLINGS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<Entity>> NATIVE_STATIC_ENTITIES = new CopyOnWriteArrayList<>();

    public static void register(final ResizePolicy policy) {
        POLICIES.add(Objects.requireNonNull(policy));
    }

    public static void register(final JointConductor conductor) {
        JOINT_CONDUCTORS.add(Objects.requireNonNull(conductor));
    }

    public static void register(final ResizeFollower follower) {
        RESIZE_FOLLOWERS.add(Objects.requireNonNull(follower));
    }

    public static void register(final Participation participation) {
        PARTICIPATION.add(Objects.requireNonNull(participation));
    }

    public static synchronized void coupling(final ScaleCoupling provider) {
        Objects.requireNonNull(provider);
        if (!COUPLINGS.contains(provider)) COUPLINGS.add(provider);
    }

    public static void nativeStaticEntity(final Predicate<Entity> predicate) {
        NATIVE_STATIC_ENTITIES.add(Objects.requireNonNull(predicate));
    }

    public static boolean supportsNativeStaticEntity(final Entity entity) {
        if (entity == null) return false;
        for (final Predicate<Entity> predicate : NATIVE_STATIC_ENTITIES) {
            if (predicate.test(entity)) return true;
        }
        return false;
    }

    public static Set<LivingEntity> resizeFollowers(final ServerSubLevel subLevel) {
        if (subLevel == null || RESIZE_FOLLOWERS.isEmpty()) return Set.of();
        final java.util.LinkedHashSet<LivingEntity> found = new java.util.LinkedHashSet<>();
        for (final ResizeFollower follower : RESIZE_FOLLOWERS) {
            final var entities = follower.followers(subLevel);
            if (entities == null) continue;
            for (final LivingEntity entity : entities) {
                if (entity != null && entity.isAlive()) found.add(entity);
            }
        }
        return Set.copyOf(found);
    }

    public static boolean isConductor(final JointConductor.Context joint) {
        if (joint == null) return true;
        for (final JointConductor conductor : JOINT_CONDUCTORS) {
            if (!conductor.isConductor(joint)) return false;
        }
        return true;
    }

    public static String refuse(final ServerSubLevel subLevel, final double from, final double to) {
        for (final ResizePolicy policy : POLICIES) {
            final String reason = policy.refuse(subLevel, from, to);
            if (reason != null) return reason;
        }
        return null;
    }

    public static ScaleCoupling.Graph couplingGraph(final SubLevelContainer container) {
        if (container == null || COUPLINGS.isEmpty()) return ScaleCoupling.Graph.EMPTY;
        final List<ScaleCoupling.Graph> graphs = new ArrayList<>(COUPLINGS.size());
        for (final ScaleCoupling provider : COUPLINGS) {
            final ScaleCoupling.Graph graph = provider.graph(container);
            if (graph != null && !graph.isEmpty()) graphs.add(graph);
        }
        return MergedCouplingGraph.of(graphs);
    }

    public static Vector3d couplingWorldAnchor(final SubLevelContainer container, final UUID origin) {
        if (container == null || origin == null || COUPLINGS.isEmpty()) return null;
        if (COUPLINGS.size() == 1) return COUPLINGS.get(0).worldAnchor(container, origin);

        final Set<UUID> component = couplingGraph(container).component(origin);
        for (final ScaleCoupling provider : COUPLINGS) {
            final Vector3d anchor = provider.worldAnchor(container, origin);
            if (anchor != null) return anchor;
        }
        for (final ScaleCoupling provider : COUPLINGS) {
            for (final UUID member : component) {
                if (member.equals(origin)) continue;
                final Vector3d anchor = provider.worldAnchor(container, member);
                if (anchor != null) return anchor;
            }
        }
        return null;
    }

    public static boolean excludesSubLevel(final ServerSubLevel subLevel) {
        if (subLevel == null) return false;
        for (final Participation participation : PARTICIPATION) {
            if (participation.excludesSubLevel(subLevel)) return true;
        }
        return false;
    }

    public static boolean excludesConstraintBody(final PhysicsPipelineBody body) {
        if (body == null) return false;
        for (final Participation participation : PARTICIPATION) {
            if (participation.excludesConstraintBody(body)) return true;
        }
        return false;
    }

    public static boolean holdsScale(final UUID subLevelId) {
        if (subLevelId == null) return false;
        for (final Participation participation : PARTICIPATION) {
            if (participation.holdsScale(subLevelId)) return true;
        }
        return false;
    }

    public static boolean exemptsEntity(final Entity entity) {
        if (entity == null) return false;
        for (final Participation participation : PARTICIPATION) {
            if (participation.exemptsEntity(entity)) return true;
        }
        return false;
    }

    private PymExtensions() {}
}
