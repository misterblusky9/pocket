package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.debug.PocketTrace;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelParentage {
    private static final String PARENT_KEY = "pocket_parent";

    private static final Map<UUID, UUID> PARENTS = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> DETACHED_SINCE = new ConcurrentHashMap<>();

    private static final long DETACH_FORGET_TICKS = 20L;

    public static void record(final ServerSubLevel child, final ServerSubLevel parent) {
        if (child == null || parent == null) return;

        final UUID childId = child.getUniqueId();
        final UUID parentId = parent.getUniqueId();
        if (childId == null || parentId == null || childId.equals(parentId)) return;

        PARENTS.put(childId, parentId);
        persist(child, parentId);

        PocketTrace.scale("recorded parentage child={} parent={}", childId, parentId);
    }

    public static UUID parentOf(final UUID childId) {
        return childId == null ? null : PARENTS.get(childId);
    }

    public static void forget(final UUID childId) {
        if (childId == null) return;
        PARENTS.remove(childId);
        RELEASE_REQUESTED.remove(childId);
        DETACHED_SINCE.remove(childId);
    }

    private static void disown(final ServerSubLevel child, final UUID childId, final UUID parentId) {
        PocketTrace.scale(
                "part {} is no longer attached to {} - it is its own craft now", childId, parentId);
        forget(childId);

        final CompoundTag userData = child.getUserDataTag();
        if (userData != null && userData.hasUUID(PARENT_KEY)) {
            userData.remove(PARENT_KEY);
            child.setUserDataTag(userData);
        }
    }

    public static void restore(final ServerSubLevel child) {
        if (child == null || child.getUniqueId() == null) return;

        final CompoundTag userData = child.getUserDataTag();
        if (userData == null || !userData.hasUUID(PARENT_KEY)) return;

        final UUID parentId = userData.getUUID(PARENT_KEY);
        if (parentId == null || parentId.equals(child.getUniqueId())) return;

        PARENTS.put(child.getUniqueId(), parentId);
    }

    private static void persist(final ServerSubLevel child, final UUID parentId) {
        CompoundTag userData = child.getUserDataTag();
        if (userData == null) userData = new CompoundTag();
        userData.putUUID(PARENT_KEY, parentId);
        child.setUserDataTag(userData);
    }

    public static void propagate(final ServerSubLevelContainer container) {
        if (PARENTS.isEmpty()) return;

        for (final ServerSubLevel child : container.getAllSubLevels()) {
            if (child.isRemoved()) continue;

            final UUID childId = child.getUniqueId();
            if (childId == null) continue;

            final UUID parentId = PARENTS.get(childId);
            if (parentId == null) continue;

            if (!ScaleState.hasServerState(parentId)) continue;

            final SubLevel raw = container.getSubLevel(parentId);
            if (!(raw instanceof final ServerSubLevel parent) || parent.isRemoved()) continue;

            if (!stillJoined(parent, child)) {
                final long now = child.getLevel().getGameTime();
                final long since = DETACHED_SINCE.computeIfAbsent(childId, ignored -> now);
                if (now - since >= DETACH_FORGET_TICKS) disown(child, childId, parentId);
                continue;
            }
            DETACHED_SINCE.remove(childId);

            if (com.misterblusky9.pocket.compression.CompressionSessions.isHeld(childId)) continue;

            final ScaleState.ServerState parentState = ScaleState.serverState(parent);
            final CompressionStage commanded = parentState.transitionStage() != null
                    ? parentState.transitionStage()
                    : parentState.stableStage();
            if (commanded == null) continue;

            if (isBarePlate(child) && requestRelease(parent, child)) continue;

            final ScaleState.ServerState childState = ScaleState.serverState(child);
            final CompressionStage childGoal = childState.transitionStage() != null
                    ? childState.transitionStage()
                    : childState.stableStage();
            if (childGoal == commanded && childState.requestedStage() == commanded) continue;

            PocketTrace.scale(
                    "parent stage propagated child={} parent={} stage={} from={}",
                    childId, parentId, commanded, childGoal);

            ScaleController.forceStage(child, commanded, child.getLevel().getGameTime());
        }
    }

    private static boolean stillJoined(final ServerSubLevel parent, final ServerSubLevel child) {
        return declaresConnection(parent, child) || declaresConnection(child, parent);
    }

    private static final Set<UUID> RELEASE_REQUESTED = ConcurrentHashMap.newKeySet();

    private static boolean isBarePlate(final ServerSubLevel child) {
        final BoundingBox3ic bounds = child.getPlot().getBoundingBox();
        return bounds != null
                && bounds.minX() == bounds.maxX()
                && bounds.minY() == bounds.maxY()
                && bounds.minZ() == bounds.maxZ();
    }

    private static boolean requestRelease(final ServerSubLevel parent, final ServerSubLevel child) {
        final UUID childId = child.getUniqueId();
        if (childId == null) return false;
        if (RELEASE_REQUESTED.contains(childId)) return true;

        for (final BlockEntitySubLevelActor actor : parent.getPlot().getBlockEntityActors()) {
            if (!declaresConnectionTo(actor, child)) continue;

            try {
                final Field toggle = actor.getClass().getField("assembleNextTick");
                if (toggle.getType() != boolean.class) continue;
                toggle.setBoolean(actor, true);
                RELEASE_REQUESTED.add(childId);
                PocketTrace.scale(
                        "requested release of bare plate child={} from owner={}",
                        childId, actor.getClass().getSimpleName());
                return true;
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        return false;
    }

    private static boolean declaresConnectionTo(final BlockEntitySubLevelActor actor, final ServerSubLevel to) {
        final Iterable<SubLevel> dependencies = actor.sable$getConnectionDependencies();
        if (dependencies == null) return false;
        for (final SubLevel dependency : dependencies) {
            if (dependency == to) return true;
        }
        return false;
    }

    private static boolean declaresConnection(final ServerSubLevel from, final ServerSubLevel to) {
        for (final BlockEntitySubLevelActor actor : from.getPlot().getBlockEntityActors()) {
            final Iterable<SubLevel> dependencies = actor.sable$getConnectionDependencies();
            if (dependencies == null) continue;
            for (final SubLevel dependency : dependencies) {
                if (dependency == to) return true;
            }
        }
        return false;
    }

    private SubLevelParentage() {}
}
