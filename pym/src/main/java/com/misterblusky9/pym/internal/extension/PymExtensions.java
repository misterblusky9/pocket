package com.misterblusky9.pym.internal.extension;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.spi.JointConductor;
import com.misterblusky9.pym.api.spi.Participation;
import com.misterblusky9.pym.api.spi.ResizeFollower;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PymExtensions {
    private static final List<ResizePolicy> POLICIES = new CopyOnWriteArrayList<>();
    private static final List<JointConductor> JOINT_CONDUCTORS = new CopyOnWriteArrayList<>();
    private static final List<ResizeFollower> RESIZE_FOLLOWERS = new CopyOnWriteArrayList<>();
    private static final List<Participation> PARTICIPATION = new CopyOnWriteArrayList<>();
    private static final List<ScaleCoupling> COUPLINGS = new CopyOnWriteArrayList<>();
    private static final List<ScaleCoupling> MASS_COUPLINGS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<Entity>> NATIVE_STATIC_ENTITIES = new CopyOnWriteArrayList<>();
    private static final List<Function<ServerSubLevel, Collection<UUID>>> OWNERSHIP = new CopyOnWriteArrayList<>();
    private static final List<Function<ServerSubLevel, Collection<UUID>>> LINKS = new CopyOnWriteArrayList<>();

    public static void register(ResizePolicy policy) {
        POLICIES.add(Objects.requireNonNull(policy));
    }

    public static void register(JointConductor conductor) {
        JOINT_CONDUCTORS.add(Objects.requireNonNull(conductor));
    }

    public static void register(ResizeFollower follower) {
        RESIZE_FOLLOWERS.add(Objects.requireNonNull(follower));
    }

    public static void register(Participation participation) {
        PARTICIPATION.add(Objects.requireNonNull(participation));
    }

    public static synchronized void coupling(ScaleCoupling provider) {
        Objects.requireNonNull(provider);
        if (!COUPLINGS.contains(provider)) COUPLINGS.add(provider);
    }

    public static synchronized void massCoupling(ScaleCoupling provider) {
        Objects.requireNonNull(provider);
        if (!MASS_COUPLINGS.contains(provider)) MASS_COUPLINGS.add(provider);
    }

    public static void links(Function<ServerSubLevel, Collection<UUID>> linked) {
        LINKS.add(Objects.requireNonNull(linked));
    }

    public static Set<UUID> linkedTo(ServerSubLevel subLevel) {
        if (subLevel == null || LINKS.isEmpty()) return Set.of();
        java.util.LinkedHashSet<UUID> found = new java.util.LinkedHashSet<>();
        for (Function<ServerSubLevel, Collection<UUID>> links : LINKS) {
            Collection<UUID> linked = links.apply(subLevel);
            if (linked != null) found.addAll(linked);
        }
        found.remove(null);
        found.remove(subLevel.getUniqueId());
        return found;
    }

    public static void ownership(Function<ServerSubLevel, Collection<UUID>> owned) {
        OWNERSHIP.add(Objects.requireNonNull(owned));
    }

    public static Set<UUID> ownedBy(ServerSubLevel owner) {
        if (owner == null || OWNERSHIP.isEmpty()) return Set.of();
        java.util.LinkedHashSet<UUID> found = new java.util.LinkedHashSet<>();
        for (Function<ServerSubLevel, Collection<UUID>> ownership : OWNERSHIP) {
            Collection<UUID> owned = ownership.apply(owner);
            if (owned != null) found.addAll(owned);
        }
        found.remove(owner.getUniqueId());
        return found;
    }

    public static void nativeStaticEntity(Predicate<Entity> predicate) {
        NATIVE_STATIC_ENTITIES.add(Objects.requireNonNull(predicate));
    }

    public static boolean supportsNativeStaticEntity(Entity entity) {
        if (entity == null) return false;
        for (Predicate<Entity> predicate : NATIVE_STATIC_ENTITIES) {
            if (predicate.test(entity)) return true;
        }
        return false;
    }

    public static Map<Entity, ScaleBounds> resizeFollowers(ServerSubLevel subLevel) {
        return resizeFollowers(subLevel, null);
    }

    public static Map<Entity, ScaleBounds> resizeFollowers(ServerSubLevel subLevel, ScaleDriver driver) {
        if (subLevel == null || RESIZE_FOLLOWERS.isEmpty()) return Map.of();
        Map<Entity, ScaleBounds> found = new LinkedHashMap<>();
        for (ResizeFollower follower : RESIZE_FOLLOWERS) {
            var entities = follower.followers(subLevel, driver);
            if (entities == null) continue;
            for (Entity entity : entities) {
                if (entity == null || !entity.isAlive()) continue;
                ScaleBounds bounds = follower.bounds(entity);
                found.merge(entity, bounds == null ? ScaleBounds.ANY : bounds, PymExtensions::narrower);
            }
        }
        return found;
    }

    private static ScaleBounds narrower(ScaleBounds a, ScaleBounds b) {
        double min = Math.max(a.min(), b.min());
        double max = Math.min(a.max(), b.max());
        return max >= min ? new ScaleBounds(min, max) : a;
    }

    public static boolean isConductor(JointConductor.Context joint) {
        if (joint == null) return true;
        for (JointConductor conductor : JOINT_CONDUCTORS) {
            if (!conductor.isConductor(joint)) return false;
        }
        return true;
    }

    public static String refuse(ServerSubLevel subLevel, double from, double to,
                                ScaleDriver driver) {
        for (ResizePolicy policy : POLICIES) {
            String reason = policy.refuse(subLevel, from, to, driver);
            if (reason != null) return reason;
        }
        return null;
    }

    public static ScaleCoupling.Graph couplingGraph(SubLevelContainer container) {
        if (container == null || COUPLINGS.isEmpty()) return ScaleCoupling.Graph.EMPTY;
        List<ScaleCoupling.Graph> graphs = new ArrayList<>(COUPLINGS.size());
        for (ScaleCoupling provider : COUPLINGS) {
            ScaleCoupling.Graph graph = provider.graph(container);
            if (graph != null && !graph.isEmpty()) graphs.add(graph);
        }
        return MergedCouplingGraph.of(graphs);
    }

    public static ScaleCoupling.Graph massCouplingGraph(SubLevelContainer container) {
        if (container == null || MASS_COUPLINGS.isEmpty()) return couplingGraph(container);
        List<ScaleCoupling.Graph> graphs = new ArrayList<>(MASS_COUPLINGS.size() + 1);
        ScaleCoupling.Graph coupled = couplingGraph(container);
        if (!coupled.isEmpty()) graphs.add(coupled);
        for (ScaleCoupling provider : MASS_COUPLINGS) {
            ScaleCoupling.Graph graph = provider.graph(container);
            if (graph != null && !graph.isEmpty()) graphs.add(graph);
        }
        return MergedCouplingGraph.of(graphs);
    }

    public static Vector3d couplingWorldAnchor(SubLevelContainer container, UUID origin) {
        if (container == null || origin == null || COUPLINGS.isEmpty()) return null;
        if (COUPLINGS.size() == 1) return COUPLINGS.get(0).worldAnchor(container, origin);

        Set<UUID> component = couplingGraph(container).component(origin);
        for (ScaleCoupling provider : COUPLINGS) {
            Vector3d anchor = provider.worldAnchor(container, origin);
            if (anchor != null) return anchor;
        }
        for (ScaleCoupling provider : COUPLINGS) {
            for (UUID member : component) {
                if (member.equals(origin)) continue;
                Vector3d anchor = provider.worldAnchor(container, member);
                if (anchor != null) return anchor;
            }
        }
        return null;
    }

    public static boolean excludesSubLevel(ServerSubLevel subLevel) {
        if (subLevel == null) return false;
        for (Participation participation : PARTICIPATION) {
            if (participation.excludesSubLevel(subLevel)) return true;
        }
        return false;
    }

    public static boolean excludesConstraintBody(PhysicsPipelineBody body) {
        if (body == null) return false;
        for (Participation participation : PARTICIPATION) {
            if (participation.excludesConstraintBody(body)) return true;
        }
        return false;
    }

    public static boolean holdsScale(UUID subLevelId) {
        if (subLevelId == null) return false;
        for (Participation participation : PARTICIPATION) {
            if (participation.holdsScale(subLevelId)) return true;
        }
        return false;
    }

    public static boolean exemptsEntity(Entity entity) {
        if (entity == null) return false;
        for (Participation participation : PARTICIPATION) {
            if (participation.exemptsEntity(entity)) return true;
        }
        return false;
    }

    private PymExtensions() {}
}
