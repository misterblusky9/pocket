package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.physics.ConstraintRefresh;
import com.misterblusky9.pym.internal.physics.RapierBridge;
import com.misterblusky9.pym.internal.scale.ScaleState;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Hosted {
    private static final double VOID_DEPTH = 64.0D;
    private static final double VOID_MARGIN = 16.0D;
    private static final double DEEP = VOID_DEPTH - 2.0D * VOID_MARGIN;
    private static final double CELL = 64.0D;
    private static final Map<ServerSubLevel, Long> ROOTS = new IdentityHashMap<>();
    private static final Map<ServerSubLevel, ServerSubLevel> MEMBERS = new IdentityHashMap<>();
    private static final Map<ServerSubLevel, Pose3d> PRESENTED = new IdentityHashMap<>();
    private static final Map<ServerSubLevel, Pose3d> PHYSICAL = new IdentityHashMap<>();
    private static Predicate<ServerSubLevel> tangible = root -> false;
    private static Predicate<ServerSubLevel> editable = root -> false;
    private static BiPredicate<SubLevel, Entity> wornBy = (subLevel, entity) -> false;
    private static Function<ServerPlayer, ServerSubLevel> worn = player -> null;
    private static BiConsumer<ServerSubLevel, ServerSubLevel> replaced = (before, after) -> {};
    private static Predicate<ServerSubLevel> prepare = hat -> true;
    private static final Map<ServerSubLevel, Set<ServerSubLevel>> RIDERS = new IdentityHashMap<>();
    private static final Map<ServerSubLevel, Long> PINS = new IdentityHashMap<>();
    private static final Set<ServerSubLevel> GHOSTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<UUID> BROKEN = new HashSet<>();

    public static void wearables(Function<ServerPlayer, ServerSubLevel> resolver,
                                 BiConsumer<ServerSubLevel, ServerSubLevel> onReplaced,
                                 Predicate<ServerSubLevel> beforeHost) {
        worn = resolver;
        replaced = onReplaced;
        prepare = beforeHost;
    }

    public static void tangible(Predicate<ServerSubLevel> rule) {
        tangible = rule;
    }

    public static boolean tangible(@Nullable SubLevel subLevel) {
        ServerSubLevel root = rootOf(subLevel);
        return root == null || tangible.test(root);
    }

    public static void editable(Predicate<ServerSubLevel> rule) {
        editable = rule;
    }

    public static boolean editable(@Nullable SubLevel subLevel) {
        ServerSubLevel root = rootOf(subLevel);
        return root == null || editable.test(root);
    }

    public static void wornBy(BiPredicate<SubLevel, Entity> rule) {
        wornBy = wornBy.or(rule);
    }

    public static boolean wornBy(SubLevel subLevel, Entity entity) {
        return wornBy.test(subLevel, entity);
    }

    public static @Nullable ServerSubLevel worn(ServerPlayer player) {
        return worn.apply(player);
    }

    public static boolean isRoot(@Nullable SubLevel subLevel) {
        return ROOTS.containsKey(subLevel);
    }

    public static boolean isHosted(@Nullable SubLevel subLevel) {
        if (subLevel == null) return false;
        return rootOf(subLevel) != null || deep(subLevel);
    }

    public static @Nullable ServerSubLevel rootOf(@Nullable SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel server)) return null;
        if (ROOTS.containsKey(server)) return server;
        ServerSubLevel indexed = MEMBERS.get(server);
        if (indexed != null) return indexed;
        for (Map.Entry<ServerSubLevel, Set<ServerSubLevel>> entry : RIDERS.entrySet()) {
            if (entry.getValue().contains(server)) return entry.getKey();
        }
        return null;
    }

    public static void broke(@Nullable SubLevel subLevel) {
        if (rootOf(subLevel) != null) BROKEN.add(subLevel.getUniqueId());
    }

    public static List<ServerSubLevel> takeBroken(ServerSubLevel root) {
        if (BROKEN.isEmpty()) return List.of();
        List<ServerSubLevel> broken = new ArrayList<>();
        if (BROKEN.remove(root.getUniqueId())) broken.add(root);
        for (ServerSubLevel member : members(root)) {
            if (BROKEN.remove(member.getUniqueId())) broken.add(member);
        }
        return broken;
    }

    public static void present(ServerSubLevel root, Pose3dc pose) {
        if (!ROOTS.containsKey(root)) return;
        PRESENTED.put(root, new Pose3d(pose));
        show(root);
        for (Map.Entry<ServerSubLevel, ServerSubLevel> member : MEMBERS.entrySet()) {
            if (member.getValue() == root) show(member.getKey());
        }
    }

    public static @Nullable Pose3d presented(@Nullable SubLevel subLevel) {
        ServerSubLevel root = rootOf(subLevel);
        Pose3d shown = root == null ? null : PRESENTED.get(root);
        if (shown == null) return null;
        Pose3d frame = rebased(shown, root.logicalPose().rotationPoint());
        return subLevel == root ? frame : Detached.compose(frame, relative(root, (ServerSubLevel) subLevel));
    }

    public static Pose3d physical(ServerSubLevel subLevel) {
        Pose3d pose = new Pose3d(subLevel.logicalPose());
        Pose3d physical = PHYSICAL.get(subLevel);
        if (physical != null) {
            pose.position().set(physical.position());
            pose.orientation().set(physical.orientation());
        }
        return pose;
    }

    public static void readPhysical(ServerSubLevel subLevel, Pose3dc physical) {
        if (rootOf(subLevel) == null) return;
        PHYSICAL.computeIfAbsent(subLevel, ignored -> new Pose3d()).set(physical);
    }

    public static @Nullable Pose3d toPhysical(ServerSubLevel subLevel, Vector3dc position, Quaterniondc orientation) {
        ServerSubLevel root = rootOf(subLevel);
        Pose3d shown = root == null ? null : PRESENTED.get(root);
        if (shown == null) return null;
        Pose3d target = new Pose3d(subLevel.logicalPose());
        target.position().set(position);
        target.orientation().set(orientation);
        Pose3d relative = Detached.relative(rebased(shown, root.logicalPose().rotationPoint()), target);
        relative.scale().set(ScaleState.getServerScale(subLevel) / ScaleState.getServerScale(root));
        Pose3d physical = Detached.compose(scaled(root), relative);
        PHYSICAL.computeIfAbsent(subLevel, ignored -> new Pose3d()).set(physical);
        return physical;
    }

    public static @Nullable Quaterniond frameRotation(@Nullable SubLevel subLevel) {
        ServerSubLevel root = rootOf(subLevel);
        Pose3d shown = root == null ? null : PRESENTED.get(root);
        if (shown == null) return null;
        return new Quaterniond(shown.orientation()).mul(new Quaterniond(physical(root).orientation()).conjugate());
    }

    public static double frameScale(@Nullable SubLevel subLevel) {
        ServerSubLevel root = rootOf(subLevel);
        Pose3d shown = root == null ? null : PRESENTED.get(root);
        return shown == null ? 1.0D : shown.scale().x() / ScaleState.getServerScale(root);
    }

    private static void show(ServerSubLevel subLevel) {
        Pose3d shown = presented(subLevel);
        if (shown == null) return;
        Pose3d pose = subLevel.logicalPose();
        pose.position().set(shown.position());
        pose.orientation().set(shown.orientation());
        pose.scale().set(shown.scale());
    }

    private static Pose3d rebased(Pose3dc pose, Vector3dc rotationPoint) {
        Pose3d rebased = new Pose3d(pose);
        rebased.rotationPoint().set(rotationPoint);
        rebased.position().set(rotationPoint);
        pose.transformPosition(rebased.position());
        return rebased;
    }

    public static Vector3d site(ServerSubLevel subLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        int half = 1 << (container.getLogSideLength() - 1);
        int x = subLevel.getPlot().plotPos.x - container.getOrigin().x - half;
        int z = subLevel.getPlot().plotPos.z - container.getOrigin().y - half;
        return new Vector3d(x * CELL + 0.5D, subLevel.getLevel().getMinBuildHeight() - VOID_DEPTH + VOID_MARGIN, z * CELL + 0.5D);
    }

    public static @Nullable ServerSubLevel host(ServerSubLevel detached) {
        if (!Detached.isOpen(detached) || Detached.parent(detached) != null || !prepare.test(detached)) return null;
        ServerLevel level = detached.getLevel();
        UUID id = detached.getUniqueId();
        Pose3d worn = new Pose3d(detached.logicalPose());
        Map<ServerSubLevel, Pose3d> riders = new LinkedHashMap<>();
        for (ServerSubLevel child : Detached.children(detached)) {
            Pose3d link = Detached.relative(child);
            if (link == null) continue;
            child.logicalPose().set(Detached.compose(worn, link));
            ServerSubLevel rider = Detached.release(child);
            if (rider == null) {
                LogUtils.getLogger().warn("Hosted rider {} could not be released", child.getUniqueId());
                riders.forEach((released, relative) -> restore(released, detached, relative));
                return null;
            }
            riders.put(rider, link);
        }
        for (ServerSubLevel rider : riders.keySet()) {
            if (rider.getMassTracker().getCenterOfMass() != null) continue;
            LogUtils.getLogger().warn("Hosted rider {} has no centre of mass", rider.getUniqueId());
            riders.forEach((released, relative) -> restore(released, detached, relative));
            return null;
        }
        ServerSubLevel root = Detached.release(detached);
        if (root == null) {
            ServerSubLevel back = Detached.find(id);
            if (back == null) return null;
            riders.forEach((released, relative) -> restore(released, back, relative));
            if (back != detached) replaced.accept(detached, back);
            return null;
        }
        Vector3dc centre = root.getMassTracker().getCenterOfMass();
        if (centre == null) {
            LogUtils.getLogger().warn("Hosted root {} has no centre of mass", id);
            ServerSubLevel back = Detached.adopt(root);
            if (back == null) {
                replaced.accept(detached, root);
                return null;
            }
            riders.forEach((released, relative) -> restore(released, back, relative));
            replaced.accept(detached, back);
            return null;
        }
        PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        Vector3d site = site(root);
        root.logicalPose().rotationPoint().set(centre);
        pipeline.teleport(root, site, new Quaterniond());
        pipeline.resetVelocity(root);
        root.updateLastPose();
        root.updateBoundingBox();
        ROOTS.put(root, RapierBridge.pinToWorld(root, site, centre));
        PRESENTED.put(root, worn);
        Pose3d rootPose = scaled(root);
        Set<ServerSubLevel> adopted = Collections.newSetFromMap(new IdentityHashMap<>());
        riders.forEach((rider, link) -> {
            Vector3dc mass = rider.getMassTracker().getCenterOfMass();
            Pose3d target = Detached.compose(rootPose, link);
            rider.logicalPose().rotationPoint().set(mass);
            pipeline.teleport(rider, target.transformPosition(new Vector3d(mass)), new Quaterniond(target.orientation()));
            pipeline.resetVelocity(rider);
            rider.updateLastPose();
            rider.updateBoundingBox();
            adopted.add(rider);
            pin(rider);
        });
        if (!adopted.isEmpty()) RIDERS.put(root, adopted);
        PHYSICAL.put(root, new Pose3d(root.logicalPose()));
        for (ServerSubLevel rider : adopted) {
            PHYSICAL.put(rider, new Pose3d(rider.logicalPose()));
            MEMBERS.put(rider, root);
        }
        show(root);
        adopted.forEach(Hosted::show);
        root.updateLastPose();
        root.updateBoundingBox();
        for (ServerSubLevel rider : adopted) {
            rider.updateLastPose();
            rider.updateBoundingBox();
        }
        replaced.accept(detached, root);
        return root;
    }

    private static void restore(ServerSubLevel released, ServerSubLevel parent, Pose3d relative) {
        ServerSubLevel child = Detached.adopt(released);
        if (child == null) return;
        Detached.attach(child, parent, relative);
        Detached.rules(child, Rules.EDITABLE);
    }

    public static @Nullable ServerSubLevel unhost(ServerSubLevel root) {
        Long joint = ROOTS.get(root);
        if (joint == null || root.isRemoved()) return null;
        long scene = RapierBridge.sceneHandle(root.getLevel());
        RapierBridge.removeConstraint(scene, joint);
        for (ServerSubLevel rider : RIDERS.getOrDefault(root, Set.of())) unpin(scene, rider);
        Pose3d shown = PRESENTED.get(root);
        Map<ServerSubLevel, Pose3d> relatives = new LinkedHashMap<>();
        for (ServerSubLevel member : members(root)) relatives.put(member, relative(root, member));
        List<Map.Entry<ServerSubLevel, Pose3d>> adopted = new ArrayList<>();
        BROKEN.remove(root.getUniqueId());
        for (Map.Entry<ServerSubLevel, Pose3d> entry : relatives.entrySet()) {
            BROKEN.remove(entry.getKey().getUniqueId());
            ServerSubLevel child = Detached.adopt(entry.getKey());
            if (child == null) LogUtils.getLogger().warn("Hosted member {} could not fold back", entry.getKey().getUniqueId());
            else adopted.add(Map.entry(child, entry.getValue()));
        }
        ServerSubLevel detached = Detached.adopt(root);
        ROOTS.remove(root);
        PRESENTED.remove(root);
        PHYSICAL.remove(root);
        RIDERS.remove(root);
        relatives.keySet().forEach(MEMBERS::remove);
        relatives.keySet().forEach(PHYSICAL::remove);
        if (detached == null) {
            LogUtils.getLogger().warn("Hosted root {} could not fold back", root.getUniqueId());
            return null;
        }
        if (shown != null) {
            detached.logicalPose().set(shown);
            detached.updateLastPose();
        }
        for (Map.Entry<ServerSubLevel, Pose3d> entry : adopted) {
            Detached.attach(entry.getKey(), detached, entry.getValue());
            Detached.rules(entry.getKey(), Rules.EDITABLE);
        }
        replaced.accept(root, detached);
        return detached;
    }

    public static List<ServerSubLevel> members(ServerSubLevel root) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(root.getLevel());
        if (container == null || !ROOTS.containsKey(root)) return List.of();
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        List<ServerSubLevel> members = new ArrayList<>();
        seen.add(root.getUniqueId());
        queue.add(root.getUniqueId());
        for (ServerSubLevel rider : RIDERS.getOrDefault(root, Set.of())) {
            if (rider.isRemoved() || !seen.add(rider.getUniqueId())) continue;
            members.add(rider);
            queue.add(rider.getUniqueId());
        }
        while (!queue.isEmpty()) {
            for (UUID next : ConstraintRefresh.neighbours(queue.poll())) {
                if (!seen.add(next) || !(container.getSubLevel(next) instanceof ServerSubLevel member)
                        || member.isRemoved()) continue;
                members.add(member);
                queue.add(next);
            }
        }
        return members;
    }

    public static Pose3d relative(ServerSubLevel root, ServerSubLevel member) {
        return Detached.relative(scaled(root), scaled(member));
    }

    private static Pose3d scaled(ServerSubLevel subLevel) {
        Pose3d pose = physical(subLevel);
        pose.scale().set(ScaleState.getServerScale(subLevel));
        return pose;
    }

    public static <T> List<T> visible(List<T> all) {
        List<T> shown = new ArrayList<>(all.size());
        for (T subLevel : all) {
            if (!(subLevel instanceof SubLevel candidate) || !isHosted(candidate)) shown.add(subLevel);
        }
        return shown;
    }

    public static void loaded(@Nullable ServerSubLevel subLevel) {
        if (subLevel != null && !Detached.adopting(subLevel) && deep(subLevel)) GHOSTS.add(subLevel);
    }

    private static boolean deep(SubLevel subLevel) {
        return subLevel.logicalPose().position().y() < subLevel.getLevel().getMinBuildHeight() - DEEP;
    }

    public static void tick(ServerTickEvent.Post event) {
        if (!GHOSTS.isEmpty()) {
            for (ServerSubLevel ghost : List.copyOf(GHOSTS)) {
                LogUtils.getLogger().warn("Discarding stale saved copy of hosted body {}", ghost.getUniqueId());
                Detached.exorcise(ghost);
            }
            GHOSTS.clear();
        }
        if (ROOTS.isEmpty()) return;
        ROOTS.keySet().removeIf(ServerSubLevel::isRemoved);
        PRESENTED.keySet().retainAll(ROOTS.keySet());
        PHYSICAL.keySet().removeIf(ServerSubLevel::isRemoved);
        RIDERS.keySet().retainAll(ROOTS.keySet());
        RIDERS.values().forEach(riders -> riders.removeIf(ServerSubLevel::isRemoved));
        MEMBERS.clear();
        for (ServerSubLevel root : ROOTS.keySet()) {
            Detached.syncBounds(root);
            for (ServerSubLevel member : members(root)) {
                MEMBERS.put(member, root);
                Detached.syncBounds(member);
                RIDERS.computeIfAbsent(root, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(member);
                boolean joined = !ConstraintRefresh.neighbours(member.getUniqueId()).isEmpty();
                if (joined && PINS.containsKey(member)) unpin(RapierBridge.sceneHandle(root.getLevel()), member);
                else if (!joined && !PINS.containsKey(member)) pin(member);
            }
        }
        PINS.keySet().removeIf(ServerSubLevel::isRemoved);
    }

    public static boolean adopt(ServerSubLevel root, ServerSubLevel body) {
        if (!ROOTS.containsKey(root) || !PRESENTED.containsKey(root) || body == root || body.isRemoved()
                || rootOf(body) != null || Detached.isDetached(body) || body.getLevel() != root.getLevel()
                || body.getMassTracker().getCenterOfMass() == null) return false;
        Vector3d position = new Vector3d(body.logicalPose().position());
        Quaterniond orientation = new Quaterniond(body.logicalPose().orientation());
        ServerSubLevelContainer container = SubLevelContainer.getContainer(body.getLevel());
        if (container != null) container.trackingSystem().onSubLevelRemoved(body, SubLevelRemovalReason.REMOVED);
        RIDERS.computeIfAbsent(root, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(body);
        MEMBERS.put(body, root);
        PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(root.getLevel()).getPipeline();
        pipeline.teleport(body, position, orientation);
        pipeline.resetVelocity(body);
        body.updateLastPose();
        body.updateBoundingBox();
        if (ConstraintRefresh.neighbours(body.getUniqueId()).isEmpty()) pin(body);
        return true;
    }

    public static void jointAttached(ServerSubLevel subLevel) {
        ServerSubLevel owner = rootOf(subLevel);
        if (owner == null || owner == subLevel || !PINS.containsKey(subLevel)) return;
        unpin(RapierBridge.sceneHandle(owner.getLevel()), subLevel);
    }

    private static void pin(ServerSubLevel rider) {
        Vector3dc mass = rider.getMassTracker().getCenterOfMass();
        if (mass != null) PINS.put(rider, RapierBridge.pinToWorld(rider, physical(rider).position(), mass));
    }

    private static void unpin(long scene, ServerSubLevel rider) {
        Long pin = PINS.remove(rider);
        if (pin != null && !rider.isRemoved()) RapierBridge.removeConstraint(scene, pin);
    }

    public static void stopped(ServerStoppedEvent event) {
        ROOTS.clear();
        MEMBERS.clear();
        PRESENTED.clear();
        PHYSICAL.clear();
        RIDERS.clear();
        PINS.clear();
        GHOSTS.clear();
        BROKEN.clear();
    }

    private Hosted() {}
}
