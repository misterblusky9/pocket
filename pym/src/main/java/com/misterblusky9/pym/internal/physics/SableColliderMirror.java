package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.physics.ColliderGeometry.SectionKey;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SableColliderMirror {
    private static final int OCTREE_SENTINEL_Y = 1_000_000;
    private static final Map<UUID, Applied> APPLIED = new HashMap<>();

    private static final class SectionBinding {
        private final CompiledCollider.Section section;
        private final Set<ColliderShapeKey> retainedShapes;

        private SectionBinding(
                CompiledCollider.Section section,
                Set<ColliderShapeKey> retainedShapes
        ) {
            this.section = section;
            this.retainedShapes = retainedShapes;
        }
    }

    private static final class Applied {
        private final int bodyId;
        private CompiledCollider collider;
        private final Map<SectionKey, SectionBinding> sections;

        private Applied(
                int bodyId,
                CompiledCollider collider,
                Map<SectionKey, SectionBinding> sections
        ) {
            this.bodyId = bodyId;
            this.collider = collider;
            this.sections = sections;
        }
    }

    public static synchronized CompiledCollider current(UUID id) {
        Applied state = id == null ? null : APPLIED.get(id);
        return state == null ? null : state.collider;
    }

    public static synchronized boolean has(UUID id) {
        return id != null && APPLIED.containsKey(id);
    }

    public static synchronized boolean retagMetadata(CompiledCollider replacement) {
        if (replacement == null || replacement.craftId() == null) return false;
        Applied state = APPLIED.get(replacement.craftId());
        if (state == null || state.bodyId != replacement.bodyId()) return false;
        if (!sameNativeImage(state.collider, replacement)) {
            throw new IllegalArgumentException("metadata retag attempted with a different native collider image");
        }
        state.collider = replacement;
        return true;
    }

    private static boolean sameNativeImage(
            CompiledCollider left,
            CompiledCollider right
    ) {
        if (left == right) return true;
        if (left == null || right == null || left.sections().size() != right.sections().size()) return false;
        for (Map.Entry<SectionKey, CompiledCollider.Section> entry : left.sections().entrySet()) {
            CompiledCollider.Section other = right.sections().get(entry.getKey());
            if (!entry.getValue().sameImage(other)) return false;
        }
        return true;
    }

    public static synchronized boolean apply(
            ServerSubLevel subLevel,
            CompiledCollider next
    ) {
        if (subLevel == null || next == null || subLevel.getUniqueId() == null) return false;
        UUID id = subLevel.getUniqueId();
        ServerLevel level = subLevel.getLevel();
        int liveBody = RapierBridge.bodyId(subLevel);
        if (liveBody == RapierBridge.NO_BODY || liveBody != next.bodyId()) return false;

        Applied state = APPLIED.get(id);
        if (state != null && state.bodyId != liveBody) {
            release(state);
            APPLIED.remove(id);
            state = null;
        }

        if (state != null && state.collider.geometryKey().equals(next.geometryKey())) return false;

        if (state != null && sameNativeImage(state.collider, next)) {
            state.collider = next;
            traceApplied(id, next, 0, false);
            return false;
        }

        ScaledRebuildCollisionEffectFilter.markRebuilt(
                RapierBridge.sceneHandle(level), liveBody, level.getGameTime());

        if (requiresFullReplace(state, next)) {
            applyFull(level, subLevel, next, state);
            return true;
        }

        try {
            return applyIncremental(level, subLevel, next, state);
        } catch (ShapeRegistry.CapacityExceededException capacity) {
            PymTrace.warn("incremental collider retain exhausted packed handles; retrying full replacement uuid={} registry[{}]",
                    id, ShapeRegistry.stats());
            applyFull(level, subLevel, next, state);
            return true;
        }
    }

    public static synchronized void restoreOriginal(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        UUID id = subLevel.getUniqueId();
        Applied state = APPLIED.remove(id);
        if (state == null) return;

        ServerLevel level = subLevel.getLevel();
        if (state.bodyId == RapierBridge.bodyId(subLevel)) {
            clearSections(level, subLevel, state.sections.keySet());
            CompiledCollider.Bounds original = originalBounds(subLevel);
            if (original != null) rebuildBodyOctree(level, subLevel, original);
            restoreOriginalChunks(level, subLevel);
        }
        release(state);
    }

    public static synchronized void forget(UUID id) {
        if (id == null) return;
        release(APPLIED.remove(id));
    }

    private static void applyFull(
            ServerLevel level,
            ServerSubLevel subLevel,
            CompiledCollider next,
            Applied oldState
    ) {
        UUID id = subLevel.getUniqueId();
        boolean oldReleased = false;
        Map<SectionKey, SectionBinding> prepared = null;

        try {
            if (oldState == null) {
                clearOriginalBackingChunks(level, subLevel, next.sections().keySet());
            } else {
                clearSections(level, subLevel, oldState.sections.keySet());
                release(oldState);
                oldReleased = true;
            }

            prepared = retainAll(next);
            rebuildBodyOctree(level, subLevel, ScalePhysicsTransitions.nativeBounds(subLevel, next.bounds()));

            for (SectionBinding binding : prepared.values()) {
                uploadWhole(level, subLevel, binding.section);
            }
        } catch (RuntimeException exception) {
            PymTrace.warn("full collider apply failed uuid={} gen={} error={}",
                    id, next.generation(), exception.toString());
            if (prepared != null) releaseBindings(prepared);
            try {
                clearSections(level, subLevel, next.sections().keySet());
                if (oldState == null) {
                    CompiledCollider.Bounds original = originalBounds(subLevel);
                    if (original != null) rebuildBodyOctree(level, subLevel, original);
                    restoreOriginalChunks(level, subLevel);
                    APPLIED.remove(id);
                } else if (oldReleased) {
                    Map<SectionKey, SectionBinding> restored = retainAll(oldState.collider);
                    try {
                        rebuildBodyOctree(level, subLevel, ScalePhysicsTransitions.nativeBounds(subLevel, oldState.collider.bounds()));
                        for (SectionBinding binding : restored.values()) {
                            uploadWhole(level, subLevel, binding.section);
                        }
                    } catch (RuntimeException rollbackFailure) {
                        releaseBindings(restored);
                        throw rollbackFailure;
                    }
                    APPLIED.put(id, new Applied(oldState.bodyId, oldState.collider, restored));
                } else {
                    rebuildBodyOctree(level, subLevel, ScalePhysicsTransitions.nativeBounds(subLevel, oldState.collider.bounds()));
                    for (SectionBinding binding : oldState.sections.values()) {
                        uploadWhole(level, subLevel, binding.section);
                    }
                }
            } catch (RuntimeException rollbackFailure) {
                APPLIED.remove(id);
                exception.addSuppressed(rollbackFailure);
                PymTrace.warn("full collider rollback failed uuid={} error={}",
                        id, rollbackFailure.toString());
            }
            throw exception;
        }

        Applied replacement = new Applied(next.bodyId(), next, prepared);
        APPLIED.put(id, replacement);
        if (!oldReleased) release(oldState);
        traceApplied(id, next, next.sections().size(), true);
    }

    private static boolean applyIncremental(
            ServerLevel level,
            ServerSubLevel subLevel,
            CompiledCollider next,
            Applied state
    ) {
        if (state == null) throw new IllegalStateException("incremental collider apply without prior state");
        UUID id = subLevel.getUniqueId();
        Map<SectionKey, SectionBinding> oldBindings = new HashMap<>(state.sections);
        Map<SectionKey, SectionBinding> newBindings = new HashMap<>();
        List<SectionKey> changed = new ArrayList<>();
        Set<SectionKey> allKeys = new HashSet<>(oldBindings.keySet());
        allKeys.addAll(next.sections().keySet());

        try {
            for (SectionKey key : allKeys) {
                SectionBinding old = oldBindings.get(key);
                CompiledCollider.Section wanted = next.sections().get(key);
                if (wanted != null && old != null && old.section.sameImage(wanted)) {
                    newBindings.put(key, old);
                    continue;
                }
                changed.add(key);
                if (wanted != null) newBindings.put(key, retain(wanted));
            }
        } catch (RuntimeException exception) {
            releasePrepared(newBindings, oldBindings);
            throw exception;
        }

        if (changed.isEmpty()) {
            state.collider = next;
            return false;
        }

        List<SectionKey> committed = new ArrayList<>();
        boolean needsOctreeRepair = false;
        try {
            for (SectionKey key : changed) {
                committed.add(key);
                SectionBinding old = oldBindings.get(key);
                SectionBinding wanted = newBindings.get(key);
                needsOctreeRepair |= applySection(
                        level, subLevel, key,
                        old == null ? null : old.section,
                        wanted == null ? null : wanted.section,
                        state.collider.bounds());
            }
            if (needsOctreeRepair) rebuildBodyOctree(level, subLevel, ScalePhysicsTransitions.nativeBounds(subLevel, next.bounds()));
        } catch (RuntimeException exception) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                SectionKey key = committed.get(i);
                try {
                    rollbackBackingSection(level, subLevel, key,
                            oldBindings.get(key) == null ? null : oldBindings.get(key).section);
                } catch (RuntimeException rollbackFailure) {
                    PymTrace.warn("collider section rollback failed uuid={} section={} error={}",
                            id, key, rollbackFailure.toString());
                }
            }
            try {
                rebuildBodyOctree(level, subLevel, ScalePhysicsTransitions.nativeBounds(subLevel, state.collider.bounds()));
            } catch (RuntimeException rollbackFailure) {
                PymTrace.warn("collider octree rollback failed uuid={} error={}",
                        id, rollbackFailure.toString());
            }
            releasePrepared(newBindings, oldBindings);
            throw exception;
        }

        Applied replacement = new Applied(state.bodyId, next, newBindings);
        APPLIED.put(id, replacement);
        for (Map.Entry<SectionKey, SectionBinding> entry : oldBindings.entrySet()) {
            if (newBindings.get(entry.getKey()) == entry.getValue()) continue;
            release(entry.getValue());
        }
        traceApplied(id, next, changed.size(), false);
        return true;
    }

    private static boolean requiresFullReplace(Applied state, CompiledCollider next) {
        return state == null || state.bodyId != next.bodyId();
    }

    private static Map<SectionKey, SectionBinding> retainAll(CompiledCollider collider) {
        Map<SectionKey, SectionBinding> bindings = new HashMap<>();
        try {
            for (Map.Entry<SectionKey, CompiledCollider.Section> entry : collider.sections().entrySet()) {
                bindings.put(entry.getKey(), retain(entry.getValue()));
            }
            return bindings;
        } catch (RuntimeException exception) {
            releaseBindings(bindings);
            throw exception;
        }
    }

    private static SectionBinding retain(CompiledCollider.Section section) {
        Set<ColliderShapeKey> retained = new HashSet<>();
        try {
            for (ColliderShapeKey shape : section.uniqueShapes()) {
                ShapeRegistry.retain(shape);
                retained.add(shape);
            }
            return new SectionBinding(section, Set.copyOf(retained));
        } catch (RuntimeException exception) {
            for (ColliderShapeKey shape : retained) ShapeRegistry.release(shape);
            throw exception;
        }
    }

    private static boolean applySection(
            ServerLevel level,
            ServerSubLevel subLevel,
            SectionKey key,
            CompiledCollider.Section old,
            CompiledCollider.Section wanted,
            CompiledCollider.Bounds currentBounds
    ) {
        if (wanted == null) {
            if (old != null) uploadEmpty(level, subLevel, key);
            return false;
        }

        uploadWhole(level, subLevel, wanted);
        return false;
    }

    private static void rollbackBackingSection(
            ServerLevel level,
            ServerSubLevel subLevel,
            SectionKey key,
            CompiledCollider.Section old
    ) {
        if (old == null) uploadEmpty(level, subLevel, key);
        else uploadWhole(level, subLevel, old);
    }

    private static void uploadWhole(
            ServerLevel level,
            ServerSubLevel subLevel,
            CompiledCollider.Section section
    ) {
        int[] payload = new int[4096];
        for (CompiledCollider.Cell cell : section.cells()) {
            payload[cell.localIndex()] = packed(cell.shape());
        }
        SectionKey key = section.key();
        RapierBridge.addSubLevelChunk(level, subLevel, key.x(), key.y(), key.z(), payload);
    }

    private static int packed(ColliderShapeKey shape) {
        int handle = ShapeRegistry.handle(shape);
        if (handle < 0 || handle > ShapeRegistry.MAX_PACKABLE_HANDLE) {
            throw new IllegalStateException("unpackable Sable collider handle: " + handle);
        }
        return ((int) VoxelNeighborhoodState.CORNER.byteRepresentation()) | ((handle + 1) << 16);
    }

    private static void uploadEmpty(
            ServerLevel level,
            ServerSubLevel subLevel,
            SectionKey key
    ) {
        RapierBridge.addSubLevelChunk(
                level, subLevel, key.x(), key.y(), key.z(), new int[4096]);
    }

    private static void clearSections(
            ServerLevel level,
            ServerSubLevel subLevel,
            Set<SectionKey> sections
    ) {
        for (SectionKey key : sections) uploadEmpty(level, subLevel, key);
    }

    private static void clearMissingSections(
            ServerLevel level,
            ServerSubLevel subLevel,
            Set<SectionKey> oldSections,
            Set<SectionKey> nextSections
    ) {
        for (SectionKey key : oldSections) {
            if (!nextSections.contains(key)) uploadEmpty(level, subLevel, key);
        }
    }

    private static void clearOriginalBackingChunks(
            ServerLevel level,
            ServerSubLevel subLevel,
            Set<SectionKey> replacementSections
    ) {
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos global = chunk.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                if (sections[i].hasOnlyAir()) continue;
                SectionKey key = new SectionKey(
                        global.x, chunk.getSectionYFromSectionIndex(i), global.z);
                if (!replacementSections.contains(key)) uploadEmpty(level, subLevel, key);
            }
        }
    }

    private static void rebuildBodyOctree(
            ServerLevel level,
            ServerSubLevel subLevel,
            CompiledCollider.Bounds target
    ) {
        if (target == null) return;
        RapierBridge.setLocalBoundsQuiet(
                level, subLevel,
                0, OCTREE_SENTINEL_Y, 0,
                0, OCTREE_SENTINEL_Y, 0);
        RapierBridge.setLocalBoundsQuiet(
                level, subLevel,
                target.minX(), target.minY(), target.minZ(),
                target.maxX(), target.maxY(), target.maxZ());
    }

    private static CompiledCollider.Bounds originalBounds(ServerSubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;
        return new CompiledCollider.Bounds(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static void restoreOriginalChunks(
            ServerLevel level,
            ServerSubLevel subLevel
    ) {
        PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos global = chunk.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) continue;
                pipeline.handleChunkSectionAddition(
                        section, global.x, chunk.getSectionYFromSectionIndex(i), global.z, true);
            }
        }
    }

    private static void traceApplied(
            UUID id,
            CompiledCollider next,
            int changedSections,
            boolean full
    ) {
        PymTrace.scale(
                "collider generation applied uuid={} gen={} full={} mode={} sections={} cells={} shapes={} approximateCells={} changedSections={} registry[{}]",
                id, next.generation(), full, next.modeName(), next.sections().size(),
                next.occupiedCellCount(), next.shapeCount(), next.approximateCellCount(),
                changedSections, ShapeRegistry.stats());
    }

    private static void releasePrepared(
            Map<SectionKey, SectionBinding> prepared,
            Map<SectionKey, SectionBinding> old
    ) {
        for (Map.Entry<SectionKey, SectionBinding> entry : prepared.entrySet()) {
            if (old.get(entry.getKey()) == entry.getValue()) continue;
            release(entry.getValue());
        }
    }

    private static void releaseBindings(Map<SectionKey, SectionBinding> bindings) {
        for (SectionBinding binding : bindings.values()) release(binding);
        bindings.clear();
    }

    private static void release(Applied state) {
        if (state == null) return;
        releaseBindings(state.sections);
    }

    private static void release(SectionBinding binding) {
        if (binding == null) return;
        for (ColliderShapeKey shape : binding.retainedShapes) ShapeRegistry.release(shape);
    }

    private SableColliderMirror() {}
}
