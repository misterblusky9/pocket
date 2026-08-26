package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ColliderGeometry.SectionKey;
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
                final CompiledCollider.Section section,
                final Set<ColliderShapeKey> retainedShapes
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
                final int bodyId,
                final CompiledCollider collider,
                final Map<SectionKey, SectionBinding> sections
        ) {
            this.bodyId = bodyId;
            this.collider = collider;
            this.sections = sections;
        }
    }

    public static synchronized CompiledCollider current(final UUID id) {
        final Applied state = id == null ? null : APPLIED.get(id);
        return state == null ? null : state.collider;
    }

    public static synchronized boolean has(final UUID id) {
        return id != null && APPLIED.containsKey(id);
    }

    public static synchronized boolean retagMetadata(final CompiledCollider replacement) {
        if (replacement == null || replacement.craftId() == null) return false;
        final Applied state = APPLIED.get(replacement.craftId());
        if (state == null || state.bodyId != replacement.bodyId()) return false;
        if (!sameNativeImage(state.collider, replacement)) {
            throw new IllegalArgumentException("metadata retag attempted with a different native collider image");
        }
        state.collider = replacement;
        return true;
    }

    private static boolean sameNativeImage(
            final CompiledCollider left,
            final CompiledCollider right
    ) {
        if (left == right) return true;
        if (left == null || right == null || left.sections().size() != right.sections().size()) return false;
        for (final Map.Entry<SectionKey, CompiledCollider.Section> entry : left.sections().entrySet()) {
            final CompiledCollider.Section other = right.sections().get(entry.getKey());
            if (!entry.getValue().sameImage(other)) return false;
        }
        return true;
    }

    public static synchronized boolean apply(
            final ServerSubLevel subLevel,
            final CompiledCollider next
    ) {
        if (subLevel == null || next == null || subLevel.getUniqueId() == null) return false;
        final UUID id = subLevel.getUniqueId();
        final ServerLevel level = subLevel.getLevel();
        final int liveBody = RapierBridge.bodyId(subLevel);
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

        return applyIncremental(level, subLevel, next, state);
    }

    public static synchronized void restoreOriginal(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        final UUID id = subLevel.getUniqueId();
        final Applied state = APPLIED.remove(id);
        if (state == null) return;

        final ServerLevel level = subLevel.getLevel();
        if (state.bodyId == RapierBridge.bodyId(subLevel)) {
            clearSections(level, subLevel, state.sections.keySet());
            final CompiledCollider.Bounds original = originalBounds(subLevel);
            if (original != null) rebuildBodyOctree(level, subLevel, original);
            restoreOriginalChunks(level, subLevel);
        }
        release(state);
    }

    public static synchronized void forget(final UUID id) {
        if (id == null) return;
        release(APPLIED.remove(id));
    }

    private static void applyFull(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final CompiledCollider next,
            final Applied oldState
    ) {
        final UUID id = subLevel.getUniqueId();
        final Map<SectionKey, SectionBinding> prepared = retainAll(next);
        try {
            if (oldState == null) {
                clearOriginalBackingChunks(level, subLevel, next.sections().keySet());
            } else {
                clearMissingSections(level, subLevel, oldState.sections.keySet(), next.sections().keySet());
            }

            rebuildBodyOctree(level, subLevel, next.bounds());

            for (final SectionBinding binding : prepared.values()) {
                uploadWhole(level, subLevel, binding.section);
            }
        } catch (final RuntimeException exception) {
            PocketTrace.warn("full collider apply failed uuid={} gen={} error={}",
                    id, next.generation(), exception.toString());
            try {
                clearSections(level, subLevel, next.sections().keySet());
                if (oldState == null) {
                    final CompiledCollider.Bounds original = originalBounds(subLevel);
                    if (original != null) rebuildBodyOctree(level, subLevel, original);
                    restoreOriginalChunks(level, subLevel);
                } else {
                    rebuildBodyOctree(level, subLevel, oldState.collider.bounds());
                    for (final SectionBinding binding : oldState.sections.values()) {
                        uploadWhole(level, subLevel, binding.section);
                    }
                }
            } catch (final RuntimeException rollbackFailure) {
                PocketTrace.warn("full collider rollback failed uuid={} error={}",
                        id, rollbackFailure.toString());
            }
            releaseBindings(prepared);
            throw exception;
        }

        final Applied replacement = new Applied(next.bodyId(), next, prepared);
        APPLIED.put(id, replacement);
        release(oldState);
        traceApplied(id, next, next.sections().size(), true);
    }

    private static boolean applyIncremental(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final CompiledCollider next,
            final Applied state
    ) {
        if (state == null) throw new IllegalStateException("incremental collider apply without prior state");
        final UUID id = subLevel.getUniqueId();
        final Map<SectionKey, SectionBinding> oldBindings = new HashMap<>(state.sections);
        final Map<SectionKey, SectionBinding> newBindings = new HashMap<>();
        final List<SectionKey> changed = new ArrayList<>();
        final Set<SectionKey> allKeys = new HashSet<>(oldBindings.keySet());
        allKeys.addAll(next.sections().keySet());

        try {
            for (final SectionKey key : allKeys) {
                final SectionBinding old = oldBindings.get(key);
                final CompiledCollider.Section wanted = next.sections().get(key);
                if (wanted != null && old != null && old.section.sameImage(wanted)) {
                    newBindings.put(key, old);
                    continue;
                }
                changed.add(key);
                if (wanted != null) newBindings.put(key, retain(wanted));
            }
        } catch (final RuntimeException exception) {
            releasePrepared(newBindings, oldBindings);
            throw exception;
        }

        if (changed.isEmpty()) {
            state.collider = next;
            return false;
        }

        final List<SectionKey> committed = new ArrayList<>();
        boolean needsOctreeRepair = false;
        try {
            for (final SectionKey key : changed) {
                committed.add(key);
                final SectionBinding old = oldBindings.get(key);
                final SectionBinding wanted = newBindings.get(key);
                needsOctreeRepair |= applySection(
                        level, subLevel, key,
                        old == null ? null : old.section,
                        wanted == null ? null : wanted.section,
                        state.collider.bounds());
            }
            if (needsOctreeRepair) rebuildBodyOctree(level, subLevel, next.bounds());
        } catch (final RuntimeException exception) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                final SectionKey key = committed.get(i);
                try {
                    rollbackBackingSection(level, subLevel, key,
                            oldBindings.get(key) == null ? null : oldBindings.get(key).section);
                } catch (final RuntimeException rollbackFailure) {
                    PocketTrace.warn("collider section rollback failed uuid={} section={} error={}",
                            id, key, rollbackFailure.toString());
                }
            }
            try {
                rebuildBodyOctree(level, subLevel, state.collider.bounds());
            } catch (final RuntimeException rollbackFailure) {
                PocketTrace.warn("collider octree rollback failed uuid={} error={}",
                        id, rollbackFailure.toString());
            }
            releasePrepared(newBindings, oldBindings);
            throw exception;
        }

        final Applied replacement = new Applied(state.bodyId, next, newBindings);
        APPLIED.put(id, replacement);
        for (final Map.Entry<SectionKey, SectionBinding> entry : oldBindings.entrySet()) {
            if (newBindings.get(entry.getKey()) == entry.getValue()) continue;
            release(entry.getValue());
        }
        traceApplied(id, next, changed.size(), false);
        return true;
    }

    private static boolean requiresFullReplace(final Applied state, final CompiledCollider next) {
        if (state == null) return true;
        final CompiledCollider.GeometryKey old = state.collider.geometryKey();
        final CompiledCollider.GeometryKey wanted = next.geometryKey();
        return old.bodyId() != wanted.bodyId()
                || old.scaleBits() != wanted.scaleBits()
                || old.pivotX() != wanted.pivotX()
                || old.pivotY() != wanted.pivotY()
                || old.pivotZ() != wanted.pivotZ()
                || !old.bounds().equals(wanted.bounds())
                || old.mode() != wanted.mode();
    }

    private static Map<SectionKey, SectionBinding> retainAll(final CompiledCollider collider) {
        final Map<SectionKey, SectionBinding> bindings = new HashMap<>();
        try {
            for (final Map.Entry<SectionKey, CompiledCollider.Section> entry : collider.sections().entrySet()) {
                bindings.put(entry.getKey(), retain(entry.getValue()));
            }
            return bindings;
        } catch (final RuntimeException exception) {
            releaseBindings(bindings);
            throw exception;
        }
    }

    private static SectionBinding retain(final CompiledCollider.Section section) {
        final Set<ColliderShapeKey> retained = new HashSet<>();
        try {
            for (final ColliderShapeKey shape : section.uniqueShapes()) {
                ShapeRegistry.retain(shape);
                retained.add(shape);
            }
            return new SectionBinding(section, Set.copyOf(retained));
        } catch (final RuntimeException exception) {
            for (final ColliderShapeKey shape : retained) ShapeRegistry.release(shape);
            throw exception;
        }
    }

    private static boolean applySection(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final SectionKey key,
            final CompiledCollider.Section old,
            final CompiledCollider.Section wanted,
            final CompiledCollider.Bounds currentBounds
    ) {
        if (wanted == null) {
            if (old != null) uploadEmpty(level, subLevel, key);
            return false;
        }

        uploadWhole(level, subLevel, wanted);
        return false;
    }

    private static void rollbackBackingSection(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final SectionKey key,
            final CompiledCollider.Section old
    ) {
        if (old == null) uploadEmpty(level, subLevel, key);
        else uploadWhole(level, subLevel, old);
    }

    private static void uploadWhole(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final CompiledCollider.Section section
    ) {
        final int[] payload = new int[4096];
        for (final CompiledCollider.Cell cell : section.cells()) {
            payload[cell.localIndex()] = packed(cell.shape());
        }
        final SectionKey key = section.key();
        RapierBridge.addSubLevelChunk(level, subLevel, key.x(), key.y(), key.z(), payload);
    }

    private static int packed(final ColliderShapeKey shape) {
        final int handle = ShapeRegistry.handle(shape);
        if (handle < 0 || handle > ShapeRegistry.MAX_PACKABLE_HANDLE) {
            throw new IllegalStateException("unpackable Sable collider handle: " + handle);
        }
        return ((int) VoxelNeighborhoodState.CORNER.byteRepresentation()) | ((handle + 1) << 16);
    }

    private static void uploadEmpty(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final SectionKey key
    ) {
        RapierBridge.addSubLevelChunk(
                level, subLevel, key.x(), key.y(), key.z(), new int[4096]);
    }

    private static void clearSections(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Set<SectionKey> sections
    ) {
        for (final SectionKey key : sections) uploadEmpty(level, subLevel, key);
    }

    private static void clearMissingSections(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Set<SectionKey> oldSections,
            final Set<SectionKey> nextSections
    ) {
        for (final SectionKey key : oldSections) {
            if (!nextSections.contains(key)) uploadEmpty(level, subLevel, key);
        }
    }

    private static void clearOriginalBackingChunks(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Set<SectionKey> replacementSections
    ) {
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos global = chunk.getPos();
            final LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                if (sections[i].hasOnlyAir()) continue;
                final SectionKey key = new SectionKey(
                        global.x, chunk.getSectionYFromSectionIndex(i), global.z);
                if (!replacementSections.contains(key)) uploadEmpty(level, subLevel, key);
            }
        }
    }

    private static void rebuildBodyOctree(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final CompiledCollider.Bounds target
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

    private static CompiledCollider.Bounds originalBounds(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;
        return new CompiledCollider.Bounds(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static void restoreOriginalChunks(
            final ServerLevel level,
            final ServerSubLevel subLevel
    ) {
        final PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos global = chunk.getPos();
            final LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                final LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) continue;
                pipeline.handleChunkSectionAddition(
                        section, global.x, chunk.getSectionYFromSectionIndex(i), global.z, true);
            }
        }
    }

    private static void traceApplied(
            final UUID id,
            final CompiledCollider next,
            final int changedSections,
            final boolean full
    ) {
        PocketTrace.scale(
                "collider generation applied uuid={} gen={} full={} mode={} sections={} cells={} shapes={} approximateCells={} changedSections={} registry[{}]",
                id, next.generation(), full, next.modeName(), next.sections().size(),
                next.occupiedCellCount(), next.shapeCount(), next.approximateCellCount(),
                changedSections, ShapeRegistry.stats());
    }

    private static void releasePrepared(
            final Map<SectionKey, SectionBinding> prepared,
            final Map<SectionKey, SectionBinding> old
    ) {
        for (final Map.Entry<SectionKey, SectionBinding> entry : prepared.entrySet()) {
            if (old.get(entry.getKey()) == entry.getValue()) continue;
            release(entry.getValue());
        }
    }

    private static void releaseBindings(final Map<SectionKey, SectionBinding> bindings) {
        for (final SectionBinding binding : bindings.values()) release(binding);
        bindings.clear();
    }

    private static void release(final Applied state) {
        if (state == null) return;
        releaseBindings(state.sections);
    }

    private static void release(final SectionBinding binding) {
        if (binding == null) return;
        for (final ColliderShapeKey shape : binding.retainedShapes) ShapeRegistry.release(shape);
    }

    private SableColliderMirror() {}
}
