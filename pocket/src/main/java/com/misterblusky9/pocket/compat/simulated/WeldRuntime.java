package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pocket.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WeldRuntime {
    private static final double SETTLE_TICKS = 10.0D;
    private static final double SETTLE_CURVE = 5.0D;

    private static final Map<ResourceKey<Level>, Map<UUID, PhysicsConstraintHandle>> LIVE =
            new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, PhysicsConstraintHandle>> ASSIST = new HashMap<>();

    private static final Map<ResourceKey<Level>, Map<UUID, Settle>> SETTLING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BodyPair, PhysicsConstraintHandle>> NO_CONTACT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<UUID>> NEW_WELDS = new HashMap<>();

    public static void tick(ServerLevel level, ServerSubLevelContainer container) {
        if (level == null || container == null) return;

        Map<UUID, PhysicsConstraintHandle> live =
                LIVE.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Map<UUID, PhysicsConstraintHandle> assist =
                ASSIST.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Map<UUID, Settle> settling =
                SETTLING.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Map<BodyPair, PhysicsConstraintHandle> noContact =
                NO_CONTACT.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Set<UUID> newWelds =
                NEW_WELDS.computeIfAbsent(level.dimension(), ignored -> new HashSet<>());

        WeldStore store = WeldStore.get(level);
        if (store.isEmpty()) {
            if (!live.isEmpty()) {
                for (PhysicsConstraintHandle handle : live.values()) release(handle);
                live.clear();
            }
            releaseAll(assist);
            settling.clear();
            newWelds.clear();
            releaseAll(noContact);
            return;
        }

        Set<UUID> wanted = new HashSet<>();
        boolean changed = false;

        for (WeldRecord record : store.all()) {
            ServerSubLevel small = liveSubLevel(container, record.smallSubLevel());
            ServerSubLevel big = record.worldAnchored() ? null : liveSubLevel(container, record.bigSubLevel());

            if (small == null || !record.worldAnchored() && big == null) {
                wanted.add(record.weldId());
                release(live.remove(record.weldId()));
                settling.remove(record.weldId());
                newWelds.remove(record.weldId());
                continue;
            }

            if (glueLost(level, record)) {
                store.remove(record.weldId());
                changed = true;
                continue;
            }
            wanted.add(record.weldId());

            if (hostedPair(small, big)) {
                PhysicsConstraintHandle fixed = live.get(record.weldId());
                if (fixed == null || !fixed.isValid()) {
                    release(live.remove(record.weldId()));
                    if (createHosted(live, container, record, small, big)) {
                        Hosted.jointAttached(small);
                        Hosted.jointAttached(big);
                        if (newWelds.remove(record.weldId())) finishEffect(level, record);
                    }
                }
                settling.remove(record.weldId());
                continue;
            }

            Settle settle = settling.get(record.weldId());
            if (settle == null) {
                settle = new Settle(
                        level.getGameTime(),
                        startAnchor(record, small, big),
                        startOrientation(small, big));
                settling.put(record.weldId(), settle);
            }

            double age = level.getGameTime() - settle.startTick();
            PhysicsConstraintHandle existing = live.get(record.weldId());

            if (age > SETTLE_TICKS && existing != null && existing.isValid()) continue;

            double progress = Mth.clamp(
                    Math.pow(age / SETTLE_TICKS, SETTLE_CURVE), 0.0D, 1.0D);

            release(live.remove(record.weldId()));

            Vector3d anchor = new Vector3d(settle.anchor())
                    .lerp(record.anchorFor(false), progress, new Vector3d());
            Quaterniond orientation = new Quaterniond(settle.orientation())
                    .slerp(record.orientation(), Mth.clamp(progress * 2.0D, 0.0D, 1.0D), new Quaterniond())
                    .normalize();

            if (!create(live, container, record, small, big, anchor, orientation)) {
                settling.remove(record.weldId());
                continue;
            } else if (progress >= 1.0D && newWelds.remove(record.weldId())) {
                finishEffect(level, record);
            }
        }

        Iterator<Map.Entry<UUID, PhysicsConstraintHandle>> iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PhysicsConstraintHandle> entry = iterator.next();
            if (wanted.contains(entry.getKey())) continue;
            release(entry.getValue());
            iterator.remove();
        }
        settling.keySet().retainAll(wanted);
        newWelds.retainAll(wanted);
        syncComponentContacts(container, noContact);
        syncAssist(container, store, live, assist, settling, level.getGameTime());
        if (changed) CrossScaleWeldSync.broadcast(level);
    }

    public static boolean glueLost(ServerLevel level, WeldRecord record) {
        return level.getBlockState(record.smallPos()).isAir()
                || (!record.worldAnchored() || level.isLoaded(record.bigPos()))
                && level.getBlockState(record.bigPos()).isAir();
    }

    public static void markNew(ServerLevel level, UUID weldId) {
        if (level == null || weldId == null) return;
        NEW_WELDS.computeIfAbsent(level.dimension(), ignored -> new HashSet<>()).add(weldId);
    }

    private static void syncComponentContacts(
            ServerSubLevelContainer container,
            Map<BodyPair, PhysicsConstraintHandle> active
    ) {
        CrossScaleWelds.WeldGraph graph = CrossScaleWelds.snapshot(container);
        Set<BodyPair> wanted = new HashSet<>();
        Set<UUID> visited = new HashSet<>();

        for (UUID origin : graph.endpointIds()) {
            if (!visited.add(origin)) continue;
            Set<UUID> component = graph.component(origin);
            visited.addAll(component);
            List<UUID> members = new ArrayList<>(component);

            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    UUID a = members.get(i);
                    UUID b = members.get(j);
                    if (graph.links().getOrDefault(a, Set.of()).contains(b)) continue;

                    ServerSubLevel first = liveSubLevel(container, a);
                    ServerSubLevel second = liveSubLevel(container, b);
                    if (first == null || second == null) continue;

                    BodyPair pair = BodyPair.of(a, b);
                    wanted.add(pair);
                    PhysicsConstraintHandle existing = active.get(pair);
                    if (existing != null && existing.isValid()) continue;
                    release(existing);

                    PhysicsConstraintHandle created = createNoContact(container, first, second);
                    if (created != null) active.put(pair, created);
                    else active.remove(pair);
                }
            }
        }

        Iterator<Map.Entry<BodyPair, PhysicsConstraintHandle>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BodyPair, PhysicsConstraintHandle> entry = iterator.next();
            if (wanted.contains(entry.getKey())) continue;
            release(entry.getValue());
            iterator.remove();
        }
    }

    private static PhysicsConstraintHandle createNoContact(
            ServerSubLevelContainer container,
            ServerSubLevel first,
            ServerSubLevel second
    ) {
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            PhysicsConstraintHandle handle = pipeline.addConstraint(
                    first,
                    second,
                    new FreeConstraintConfiguration(
                            plotAnchor(first),
                            plotAnchor(second),
                            CrossScaleWelds.relativeOrientation(first, second)));
            if (handle == null || !handle.isValid()) return null;
            handle.setContactsEnabled(false);
            return handle;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return null;
        }
    }

    private static boolean hostedPair(ServerSubLevel first, ServerSubLevel second) {
        if (first == null || second == null) return false;
        ServerSubLevel owner = Hosted.rootOf(first);
        return owner != null && owner == Hosted.rootOf(second);
    }

    private static Vector3d plotAnchor(ServerSubLevel subLevel) {
        BlockPos center = subLevel.getPlot().getCenterBlock();
        return new Vector3d(
                center.getX() + 0.5D,
                center.getY() + 0.5D,
                center.getZ() + 0.5D);
    }

    public static void finishEffect(ServerLevel level, WeldRecord record) {
        finishEffect(level, record.smallPos(), record.smallFacing());
        finishEffect(level, record.bigPos(), record.bigFacing());
    }

    private static void finishEffect(
            ServerLevel level,
            BlockPos support,
            net.minecraft.core.Direction facing
    ) {
        double spreadX = facing.getStepX() == 0 ? 0.3D : 0.02D;
        double spreadY = facing.getStepY() == 0 ? 0.3D : 0.02D;
        double spreadZ = facing.getStepZ() == 0 ? 0.3D : 0.02D;
        level.sendParticles(
                ModParticles.HOT_GLUE.get(),
                support.getX() + 0.5D + facing.getStepX() * 0.53D,
                support.getY() + 0.5D + facing.getStepY() * 0.53D,
                support.getZ() + 0.5D + facing.getStepZ() * 0.53D,
                16, spreadX, spreadY, spreadZ, 0.1D);
    }

    private static void releaseAll(Map<?, PhysicsConstraintHandle> handles) {
        for (PhysicsConstraintHandle handle : handles.values()) release(handle);
        handles.clear();
    }

    private static Vector3d startAnchor(
            WeldRecord record,
            ServerSubLevel small,
            ServerSubLevel big
    ) {
        Vector3d anchor = new Vector3d(record.anchorFor(true));
        small.logicalPose().transformPosition(anchor);
        if (big != null) big.logicalPose().transformPositionInverse(anchor);
        return finite(anchor) ? anchor : record.anchorFor(false);
    }

    private static Quaterniond startOrientation(
            ServerSubLevel small,
            ServerSubLevel big
    ) {
        Quaterniond orientation = new Quaterniond(small.logicalPose().orientation()).invert();
        if (big != null) orientation.mul(big.logicalPose().orientation());
        return orientation.normalize();
    }

    private static boolean createHosted(
            Map<UUID, PhysicsConstraintHandle> live,
            ServerSubLevelContainer container,
            WeldRecord record,
            ServerSubLevel small,
            ServerSubLevel big
    ) {
        Vector3d inBig = small.logicalPose().transformPosition(record.anchorFor(true));
        big.logicalPose().transformPositionInverse(inBig);
        if (!finite(inBig)) return false;
        return create(live, container, record, small, big, inBig,
                CrossScaleWelds.relativeOrientation(small, big));
    }

    private static boolean create(
            Map<UUID, PhysicsConstraintHandle> live,
            ServerSubLevelContainer container,
            WeldRecord record,
            ServerSubLevel small,
            ServerSubLevel big,
            Vector3d anchor,
            Quaterniond orientation
    ) {
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            PhysicsConstraintHandle handle = pipeline.addConstraint(
                    small,
                    big,
                    new FixedConstraintConfiguration(record.anchorFor(true), anchor, orientation));

            if (handle == null || !handle.isValid()) return false;

            handle.setContactsEnabled(record.worldAnchored());
            live.put(record.weldId(), handle);
            return true;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return false;
        }
    }

    private static void syncAssist(
            ServerSubLevelContainer container,
            WeldStore store,
            Map<UUID, PhysicsConstraintHandle> live,
            Map<UUID, PhysicsConstraintHandle> assist,
            Map<UUID, Settle> settling,
            long tick
    ) {
        Set<UUID> wanted = new HashSet<>();
        for (WeldRecord record : store.all()) {
            if (record.worldAnchored()) continue;
            ServerSubLevel small = liveSubLevel(container, record.smallSubLevel());
            ServerSubLevel big = liveSubLevel(container, record.bigSubLevel());
            if (hostedPair(small, big)) continue;
            PhysicsConstraintHandle fixed = live.get(record.weldId());
            Settle state = settling.get(record.weldId());
            if (fixed == null || !fixed.isValid() || state == null || tick - state.startTick() <= SETTLE_TICKS) continue;

            if (small == null || big == null) continue;
            wanted.add(record.weldId());

            PhysicsConstraintHandle previous = assist.get(record.weldId());
            if (previous != null && previous.isValid()) continue;
            release(previous);
            PhysicsConstraintHandle created = createAssist(container, record, small, big);
            if (created != null) assist.put(record.weldId(), created);
            else assist.remove(record.weldId());
        }

        Iterator<Map.Entry<UUID, PhysicsConstraintHandle>> iterator = assist.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PhysicsConstraintHandle> entry = iterator.next();
            if (wanted.contains(entry.getKey())) continue;
            release(entry.getValue());
            iterator.remove();
        }
    }

    private static PhysicsConstraintHandle createAssist(
            ServerSubLevelContainer container,
            WeldRecord record,
            ServerSubLevel small,
            ServerSubLevel big
    ) {
        PhysicsConstraintHandle handle = null;
        try {
            handle = container.physicsSystem().getPipeline().addConstraint(
                    small, big, new FreeConstraintConfiguration(
                            record.anchorFor(true), record.anchorFor(false), record.orientation()));
            if (handle == null || !handle.isValid()) return null;
            handle.setContactsEnabled(false);
            for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
                handle.setMotor(axis, 0.0D, 750.0D, 80.0D, true, 250.0D);
            }
            for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                handle.setMotor(axis, 0.0D, 1500.0D, 120.0D, true, 75.0D);
            }
            return handle;
        } catch (RuntimeException rejected) {
            release(handle);
            return null;
        }
    }

    private static boolean finite(Vector3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static void forget(ServerLevel level) {
        if (level == null) return;

        Map<UUID, PhysicsConstraintHandle> live = LIVE.remove(level.dimension());
        if (live != null) {
            for (PhysicsConstraintHandle handle : live.values()) release(handle);
        }
        Map<UUID, PhysicsConstraintHandle> assist = ASSIST.remove(level.dimension());
        if (assist != null) releaseAll(assist);
        SETTLING.remove(level.dimension());
        NEW_WELDS.remove(level.dimension());
        Map<BodyPair, PhysicsConstraintHandle> noContact = NO_CONTACT.remove(level.dimension());
        if (noContact != null) releaseAll(noContact);
    }

    public static void drop(UUID weldId) {
        if (weldId == null) return;
        for (Map<UUID, Settle> settling : SETTLING.values()) settling.remove(weldId);
        for (Set<UUID> newWelds : NEW_WELDS.values()) newWelds.remove(weldId);
        for (Map<UUID, PhysicsConstraintHandle> live : LIVE.values()) {
            PhysicsConstraintHandle handle = live.remove(weldId);
            if (handle != null) release(handle);
        }
        for (Map<UUID, PhysicsConstraintHandle> assist : ASSIST.values()) {
            release(assist.remove(weldId));
        }
    }

    public static void forgetMergedBody(ServerLevel level, UUID removedId) {
        if (level == null || removedId == null) return;
        Map<BodyPair, PhysicsConstraintHandle> pairs = NO_CONTACT.get(level.dimension());
        if (pairs == null) return;
        Iterator<Map.Entry<BodyPair, PhysicsConstraintHandle>> iterator = pairs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BodyPair, PhysicsConstraintHandle> entry = iterator.next();
            if (!removedId.equals(entry.getKey().first())
                    && !removedId.equals(entry.getKey().second())) continue;
            release(entry.getValue());
            iterator.remove();
        }
    }

    public static void rebindMigrated(ServerLevel level, Iterable<UUID> ids) {
        if (level == null || ids == null) return;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return;
        Map<UUID, PhysicsConstraintHandle> live =
                LIVE.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Map<UUID, Settle> settling =
                SETTLING.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        WeldStore store = WeldStore.get(level);
        for (UUID id : ids) {
            WeldRecord record = store.get(id);
            if (record == null) continue;
            ServerSubLevel small = liveSubLevel(container, record.smallSubLevel());
            ServerSubLevel big = record.worldAnchored() ? null
                    : liveSubLevel(container, record.bigSubLevel());
            if (small == null || (!record.worldAnchored() && big == null)) continue;
            if (create(live, container, record, small, big,
                    record.anchorFor(false), record.orientation())) {
                settling.put(id, new Settle(level.getGameTime() - (long) SETTLE_TICKS - 1L,
                        record.anchorFor(false), new Quaterniond(record.orientation())));
            }
        }
    }

    private static void release(PhysicsConstraintHandle handle) {
        if (handle == null) return;
        try {
            handle.remove();
        } catch (RuntimeException ignored) {
        }
    }

    private static ServerSubLevel liveSubLevel(
            ServerSubLevelContainer container,
            UUID id
    ) {
        if (id == null) return null;
        return container.getSubLevel(id) instanceof ServerSubLevel subLevel
                && !subLevel.isRemoved()
                && Pym.physicsLive(subLevel)
                ? subLevel
                : null;
    }

    private record BodyPair(UUID first, UUID second) {
        private static BodyPair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new BodyPair(a, b) : new BodyPair(b, a);
        }
    }

    private record Settle(long startTick, Vector3d anchor, Quaterniond orientation) {}

    private WeldRuntime() {}
}
