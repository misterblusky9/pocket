package com.misterblusky9.pym.internal.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Predicate;

public final class FluidSectionCache {
    private static final FluidState EMPTY = Fluids.EMPTY.defaultFluidState();
    private static final Predicate<BlockState> HAS_FLUID = state -> !state.getFluidState().isEmpty();

    private final Long2ObjectOpenHashMap<SectionEntry> sections = new Long2ObjectOpenHashMap<>();

    public void clear() {
        this.sections.clear();
    }

    public FluidState get(final Level level, final BlockPos pos) {
        final int y = pos.getY();
        if (level.isOutsideBuildHeight(y)) {
            return EMPTY;
        }

        final int sectionX = pos.getX() >> 4;
        final int sectionY = y >> 4;
        final int sectionZ = pos.getZ() >> 4;
        final long key = SectionPos.asLong(sectionX, sectionY, sectionZ);

        SectionEntry entry = this.sections.get(key);
        if (entry == null) {
            final LevelChunk chunk = level.getChunk(sectionX, sectionZ);
            final LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            final boolean hasFluid = !section.hasOnlyAir() && section.maybeHas(HAS_FLUID);
            entry = new SectionEntry(section, hasFluid);
            this.sections.put(key, entry);
        }

        if (!entry.hasFluid) {
            return EMPTY;
        }

        return entry.section.getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    private record SectionEntry(LevelChunkSection section, boolean hasFluid) {}
}
