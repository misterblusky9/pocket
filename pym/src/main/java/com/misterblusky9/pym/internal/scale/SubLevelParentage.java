package com.misterblusky9.pym.internal.scale;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelParentage {
    private static final String PARENT_KEY = "pym_parent";
    private static final String LEGACY_PARENT_KEY = "pocket_parent";
    private static final long DETACH_GRACE_TICKS = 20L;

    private static final Map<UUID, UUID> PARENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DETACHED_SINCE = new ConcurrentHashMap<>();

    public static void record(final ServerSubLevel child, final ServerSubLevel parent) {
        if (child == null || parent == null) return;
        final UUID childId = child.getUniqueId();
        final UUID parentId = parent.getUniqueId();
        if (childId == null || parentId == null || childId.equals(parentId)) return;

        PARENTS.put(childId, parentId);
        write(child, parentId);
    }

    public static void restore(final ServerSubLevel child) {
        if (child == null || child.getUniqueId() == null) return;
        final CompoundTag userData = child.getUserDataTag();
        if (userData == null) return;

        final String key = userData.hasUUID(PARENT_KEY) ? PARENT_KEY
                : userData.hasUUID(LEGACY_PARENT_KEY) ? LEGACY_PARENT_KEY
                : null;
        if (key == null) return;

        final UUID parentId = userData.getUUID(key);
        if (parentId.equals(child.getUniqueId())) return;
        PARENTS.put(child.getUniqueId(), parentId);
        if (key.equals(LEGACY_PARENT_KEY)) write(child, parentId);
    }

    public static UUID parentOf(final UUID childId) {
        return childId == null ? null : PARENTS.get(childId);
    }

    public static List<UUID> childrenOf(final UUID parentId) {
        final List<UUID> children = new ArrayList<>();
        if (parentId == null) return children;
        for (final Map.Entry<UUID, UUID> entry : PARENTS.entrySet()) {
            if (parentId.equals(entry.getValue())) children.add(entry.getKey());
        }
        return children;
    }

    public static void forget(final UUID childId) {
        if (childId == null) return;
        PARENTS.remove(childId);
        DETACHED_SINCE.remove(childId);
    }

    static void tick(final ServerSubLevelContainer container, final ServerLevel level) {
        if (PARENTS.isEmpty()) return;
        final long now = level.getGameTime();

        for (final Map.Entry<UUID, UUID> entry : PARENTS.entrySet()) {
            if (!(container.getSubLevel(entry.getKey()) instanceof final ServerSubLevel child) || child.isRemoved()) {
                continue;
            }
            if (container.getSubLevel(entry.getValue()) instanceof final ServerSubLevel parent
                    && !parent.isRemoved()
                    && areJoined(parent, child)) {
                DETACHED_SINCE.remove(entry.getKey());
                continue;
            }
            final long since = DETACHED_SINCE.computeIfAbsent(entry.getKey(), ignored -> now);
            if (now - since >= DETACH_GRACE_TICKS) release(child);
        }
    }

    public static boolean areJoined(final ServerSubLevel first, final ServerSubLevel second) {
        return first != null && second != null && first != second
                && (declaresConnection(first, second) || declaresConnection(second, first));
    }

    public static boolean isJoinedToAnother(final ServerSubLevelContainer container, final ServerSubLevel subLevel) {
        if (container == null || subLevel == null || subLevel.isRemoved()) return false;
        for (final ServerSubLevel other : container.getAllSubLevels()) {
            if (other != subLevel && !other.isRemoved() && areJoined(subLevel, other)) return true;
        }
        return false;
    }

    private static void release(final ServerSubLevel child) {
        forget(child.getUniqueId());
        final CompoundTag userData = child.getUserDataTag();
        if (userData == null || !userData.hasUUID(PARENT_KEY) && !userData.hasUUID(LEGACY_PARENT_KEY)) return;
        userData.remove(PARENT_KEY);
        userData.remove(LEGACY_PARENT_KEY);
        child.setUserDataTag(userData);
    }

    private static void write(final ServerSubLevel child, final UUID parentId) {
        final CompoundTag userData = child.getUserDataTag() == null ? new CompoundTag() : child.getUserDataTag();
        userData.putUUID(PARENT_KEY, parentId);
        userData.remove(LEGACY_PARENT_KEY);
        child.setUserDataTag(userData);
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
