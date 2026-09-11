package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.RapierBridge;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final int CONTACT_CHECK_INTERVAL = 5;
    private static final int CONTACT_MISS_LIMIT = 2;

    private static final Map<ResourceKey<Level>, Map<UUID, PhysicsConstraintHandle>> LIVE =
            new HashMap<>();

    private static final Map<ResourceKey<Level>, Map<UUID, Settle>> SETTLING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, Integer>> EMPTY_CONTACT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BodyPair, PhysicsConstraintHandle>> NO_CONTACT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<UUID>> NEW_WELDS = new HashMap<>();

    public static void tick(final ServerLevel level, final ServerSubLevelContainer container) {
        if (level == null || container == null) return;

        final Map<UUID, PhysicsConstraintHandle> live =
                LIVE.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        final Map<UUID, Settle> settling =
                SETTLING.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        final Map<UUID, Integer> emptyContact =
                EMPTY_CONTACT.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        final Map<BodyPair, PhysicsConstraintHandle> noContact =
                NO_CONTACT.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        final Set<UUID> newWelds =
                NEW_WELDS.computeIfAbsent(level.dimension(), ignored -> new HashSet<>());

        final WeldStore store = WeldStore.get(level);
        if (store.isEmpty()) {
            if (!live.isEmpty()) {
                for (final PhysicsConstraintHandle handle : live.values()) release(handle);
                live.clear();
            }
            settling.clear();
            emptyContact.clear();
            newWelds.clear();
            releaseAll(noContact);
            return;
        }

        final Set<UUID> wanted = new HashSet<>();
        boolean changed = false;

        for (final WeldRecord record : store.all()) {
            wanted.add(record.weldId());

            final ServerSubLevel small = liveSubLevel(container, record.smallSubLevel());
            final ServerSubLevel big = record.worldAnchored() ? null : liveSubLevel(container, record.bigSubLevel());

            if (small == null || !record.worldAnchored() && big == null) {
                release(live.remove(record.weldId()));
                settling.remove(record.weldId());
                emptyContact.remove(record.weldId());
                newWelds.remove(record.weldId());
                continue;
            }

            if (contactCheck(level.getGameTime(), record.weldId())) {
                if (WeldContact.hasContact(level, small, big, record)) {
                    emptyContact.remove(record.weldId());
                } else {
                    final int misses = emptyContact.merge(record.weldId(), 1, Integer::sum);
                    if (misses >= CONTACT_MISS_LIMIT) {
                        release(live.remove(record.weldId()));
                        settling.remove(record.weldId());
                        emptyContact.remove(record.weldId());
                        newWelds.remove(record.weldId());
                        store.remove(record.weldId());
                        changed = true;
                        continue;
                    }
                }
            }

            Settle settle = settling.get(record.weldId());
            if (settle == null) {
                settle = new Settle(
                        level.getGameTime(),
                        startAnchor(record, small, big),
                        startOrientation(small, big));
                settling.put(record.weldId(), settle);
            }

            final double age = level.getGameTime() - settle.startTick();
            final PhysicsConstraintHandle existing = live.get(record.weldId());

            if (age > SETTLE_TICKS && existing != null && existing.isValid()) continue;

            final double progress = Mth.clamp(
                    Math.pow(age / SETTLE_TICKS, SETTLE_CURVE), 0.0D, 1.0D);

            release(live.remove(record.weldId()));

            final Vector3d anchor = new Vector3d(settle.anchor())
                    .lerp(record.anchorFor(false), progress, new Vector3d());
            final Quaterniond orientation = new Quaterniond(settle.orientation())
                    .slerp(record.orientation(), Mth.clamp(progress * 2.0D, 0.0D, 1.0D), new Quaterniond())
                    .normalize();

            if (!create(live, container, record, small, big, anchor, orientation)) {
                PocketTrace.logger().warn(
                        "[Pocket] dropping cross-scale weld {}, the physics anchor left its plot",
                        record.weldId());
                store.remove(record.weldId());
                settling.remove(record.weldId());
                emptyContact.remove(record.weldId());
                newWelds.remove(record.weldId());
                changed = true;
            } else if (progress >= 1.0D && newWelds.remove(record.weldId())) {
                finishEffect(level, record);
            }
        }

        final Iterator<Map.Entry<UUID, PhysicsConstraintHandle>> iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<UUID, PhysicsConstraintHandle> entry = iterator.next();
            if (wanted.contains(entry.getKey())) continue;
            release(entry.getValue());
            iterator.remove();
        }
        settling.keySet().retainAll(wanted);
        emptyContact.keySet().retainAll(wanted);
        newWelds.retainAll(wanted);
        syncComponentContacts(container, noContact);
        if (changed) CrossScaleWeldSync.broadcast(level);
    }

    public static void markNew(final ServerLevel level, final UUID weldId) {
        if (level == null || weldId == null) return;
        NEW_WELDS.computeIfAbsent(level.dimension(), ignored -> new HashSet<>()).add(weldId);
    }

    private static void syncComponentContacts(
            final ServerSubLevelContainer container,
            final Map<BodyPair, PhysicsConstraintHandle> active
    ) {
        final CrossScaleWelds.WeldGraph graph = CrossScaleWelds.snapshot(container);
        final Set<BodyPair> wanted = new HashSet<>();
        final Set<UUID> visited = new HashSet<>();

        for (final UUID origin : graph.endpointIds()) {
            if (!visited.add(origin)) continue;
            final Set<UUID> component = graph.component(origin);
            visited.addAll(component);
            final List<UUID> members = new ArrayList<>(component);

            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    final UUID a = members.get(i);
                    final UUID b = members.get(j);
                    if (graph.links().getOrDefault(a, Set.of()).contains(b)) continue;

                    final ServerSubLevel first = liveSubLevel(container, a);
                    final ServerSubLevel second = liveSubLevel(container, b);
                    if (first == null || second == null) continue;

                    final BodyPair pair = BodyPair.of(a, b);
                    wanted.add(pair);
                    final PhysicsConstraintHandle existing = active.get(pair);
                    if (existing != null && existing.isValid()) continue;
                    release(existing);

                    final PhysicsConstraintHandle created = createNoContact(container, first, second);
                    if (created != null) active.put(pair, created);
                    else active.remove(pair);
                }
            }
        }

        final Iterator<Map.Entry<BodyPair, PhysicsConstraintHandle>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BodyPair, PhysicsConstraintHandle> entry = iterator.next();
            if (wanted.contains(entry.getKey())) continue;
            release(entry.getValue());
            iterator.remove();
        }
    }

    private static PhysicsConstraintHandle createNoContact(
            final ServerSubLevelContainer container,
            final ServerSubLevel first,
            final ServerSubLevel second
    ) {
        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            final PhysicsConstraintHandle handle = pipeline.addConstraint(
                    first,
                    second,
                    new FreeConstraintConfiguration(
                            plotAnchor(first),
                            plotAnchor(second),
                            CrossScaleWelds.relativeOrientation(first, second)));
            if (handle == null || !handle.isValid()) return null;
            handle.setContactsEnabled(false);
            return handle;
        } catch (final IllegalArgumentException | IllegalStateException rejected) {
            return null;
        }
    }

    private static Vector3d plotAnchor(final ServerSubLevel subLevel) {
        final BlockPos center = subLevel.getPlot().getCenterBlock();
        return new Vector3d(
                center.getX() + 0.5D,
                center.getY() + 0.5D,
                center.getZ() + 0.5D);
    }

    private static void finishEffect(final ServerLevel level, final WeldRecord record) {
        finishEffect(level, record.smallPos(), record.smallFacing());
        finishEffect(level, record.bigPos(), record.bigFacing());
    }

    private static void finishEffect(
            final ServerLevel level,
            final BlockPos support,
            final net.minecraft.core.Direction facing
    ) {
        final BlockState state = SimBlocks.MERGING_GLUE.getDefaultState()
                .setValue(MergingGlueBlock.FACING, facing);
        level.levelEvent(
                LevelEvent.PARTICLES_DESTROY_BLOCK,
                support.relative(facing),
                Block.getId(state));
    }

    private static void releaseAll(final Map<?, PhysicsConstraintHandle> handles) {
        for (final PhysicsConstraintHandle handle : handles.values()) release(handle);
        handles.clear();
    }

    private static boolean contactCheck(final long gameTime, final UUID weldId) {
        return Math.floorMod(gameTime + weldId.hashCode(), CONTACT_CHECK_INTERVAL) == 0;
    }

    private static Vector3d startAnchor(
            final WeldRecord record,
            final ServerSubLevel small,
            final ServerSubLevel big
    ) {
        final Vector3d anchor = new Vector3d(record.anchorFor(true));
        small.logicalPose().transformPosition(anchor);
        if (big != null) big.logicalPose().transformPositionInverse(anchor);
        return finite(anchor) ? anchor : record.anchorFor(false);
    }

    private static Quaterniond startOrientation(
            final ServerSubLevel small,
            final ServerSubLevel big
    ) {
        final Quaterniond orientation = new Quaterniond(small.logicalPose().orientation()).invert();
        if (big != null) orientation.mul(big.logicalPose().orientation());
        return orientation.normalize();
    }

    private static boolean create(
            final Map<UUID, PhysicsConstraintHandle> live,
            final ServerSubLevelContainer container,
            final WeldRecord record,
            final ServerSubLevel small,
            final ServerSubLevel big,
            final Vector3d anchor,
            final Quaterniond orientation
    ) {
        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            final PhysicsConstraintHandle handle = pipeline.addConstraint(
                    small,
                    big,
                    new FixedConstraintConfiguration(record.anchorFor(true), anchor, orientation));

            if (handle == null || !handle.isValid()) return false;

            handle.setContactsEnabled(record.worldAnchored());
            live.put(record.weldId(), handle);
            return true;
        } catch (final IllegalArgumentException | IllegalStateException rejected) {
            return false;
        }
    }

    private static boolean finite(final Vector3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static void forget(final ServerLevel level) {
        if (level == null) return;

        final Map<UUID, PhysicsConstraintHandle> live = LIVE.remove(level.dimension());
        if (live != null) {
            for (final PhysicsConstraintHandle handle : live.values()) release(handle);
        }
        SETTLING.remove(level.dimension());
        EMPTY_CONTACT.remove(level.dimension());
        NEW_WELDS.remove(level.dimension());
        final Map<BodyPair, PhysicsConstraintHandle> noContact = NO_CONTACT.remove(level.dimension());
        if (noContact != null) releaseAll(noContact);
        WeldedAssembly.invalidate();
    }

    public static void drop(final UUID weldId) {
        if (weldId == null) return;
        for (final Map<UUID, Settle> settling : SETTLING.values()) settling.remove(weldId);
        for (final Map<UUID, Integer> emptyContact : EMPTY_CONTACT.values()) emptyContact.remove(weldId);
        for (final Set<UUID> newWelds : NEW_WELDS.values()) newWelds.remove(weldId);
        for (final Map<UUID, PhysicsConstraintHandle> live : LIVE.values()) {
            final PhysicsConstraintHandle handle = live.remove(weldId);
            if (handle != null) release(handle);
        }
    }

    private static void release(final PhysicsConstraintHandle handle) {
        if (handle == null) return;
        try {
            handle.remove();
        } catch (final RuntimeException ignored) {
        }
    }

    private static ServerSubLevel liveSubLevel(
            final ServerSubLevelContainer container,
            final UUID id
    ) {
        if (id == null) return null;
        return container.getSubLevel(id) instanceof final ServerSubLevel subLevel
                && !subLevel.isRemoved()
                && RapierBridge.isLive(subLevel)
                ? subLevel
                : null;
    }

    private record BodyPair(UUID first, UUID second) {
        private static BodyPair of(final UUID a, final UUID b) {
            return a.compareTo(b) <= 0 ? new BodyPair(a, b) : new BodyPair(b, a);
        }
    }

    private record Settle(long startTick, Vector3d anchor, Quaterniond orientation) {}

    private WeldRuntime() {}
}
