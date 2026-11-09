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
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
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

    public static @Nullable ServerSubLevel worn(ServerPlayer player) {
        return worn.apply(player);
    }

    public static boolean isRoot(@Nullable SubLevel subLevel) {
        return ROOTS.containsKey(subLevel);
    }

    public static boolean isHosted(@Nullable SubLevel subLevel) {
        if (subLevel == null) return false;
        return ROOTS.containsKey(subLevel) || MEMBERS.containsKey(subLevel) || deep(subLevel);
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
        if (ROOTS.containsKey(root)) PRESENTED.put(root, new Pose3d(pose));
    }

    public static @Nullable Pose3d presented(@Nullable SubLevel subLevel) {
        if (subLevel instanceof ServerSubLevel server) {
            Pose3d root = PRESENTED.get(server);
            if (root != null) return new Pose3d(root);
            ServerSubLevel owner = MEMBERS.get(server);
            Pose3d worn = owner == null ? null : PRESENTED.get(owner);
            if (worn != null) return Detached.compose(worn, relative(owner, server));
        }
        return null;
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
        ServerSubLevel root = Detached.release(detached);
        if (root == null) {
            ServerSubLevel back = Detached.find(id);
            if (back != null && back != detached) replaced.accept(detached, back);
            return null;
        }
        Vector3dc centre = root.getMassTracker().getCenterOfMass();
        if (centre == null) {
            LogUtils.getLogger().warn("Hosted root {} has no centre of mass", id);
            replaced.accept(detached, root);
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
            if (mass == null) return;
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
        RIDERS.remove(root);
        relatives.keySet().forEach(MEMBERS::remove);
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
        Pose3d pose = new Pose3d(subLevel.logicalPose());
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

    public static void jointAttached(ServerSubLevel subLevel) {
        ServerSubLevel owner = rootOf(subLevel);
        if (owner == null || owner == subLevel || !PINS.containsKey(subLevel)) return;
        unpin(RapierBridge.sceneHandle(owner.getLevel()), subLevel);
    }

    private static void pin(ServerSubLevel rider) {
        Vector3dc mass = rider.getMassTracker().getCenterOfMass();
        if (mass != null) PINS.put(rider, RapierBridge.pinToWorld(rider, rider.logicalPose().position(), mass));
    }

    private static void unpin(long scene, ServerSubLevel rider) {
        Long pin = PINS.remove(rider);
        if (pin != null && !rider.isRemoved()) RapierBridge.removeConstraint(scene, pin);
    }

    public static void stopped(ServerStoppedEvent event) {
        ROOTS.clear();
        MEMBERS.clear();
        PRESENTED.clear();
        RIDERS.clear();
        PINS.clear();
        GHOSTS.clear();
        BROKEN.clear();
    }

    private Hosted() {}
}
