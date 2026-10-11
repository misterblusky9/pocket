package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WetSpongeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class NetherEvaporation {
    private static final int MAX_EFFECT_SITES = 12;
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());

    public static void apply(final ServerLevel level, final ServerSubLevel subLevel) {
        if (!level.dimensionType().ultraWarm()) return;

        final List<BlockPos> targets = findTargets(level, subLevel);
        if (targets.isEmpty()) return;

        final List<BlockPos> changed = new ArrayList<>();
        for (final BlockPos pos : targets) {
            final BlockState state = level.getBlockState(pos);
            final BlockState dried = evaporated(state, vaporizes(level, pos, state.getFluidState()));
            if (dried == null) continue;
            level.setBlock(pos, dried, SET_FLAGS);
            changed.add(pos);
        }

        playEffects(level, subLevel, changed);
        PocketTrace.debug(
                "[PocketTransfer] nether evaporation dimension={} uuid={} changed={}",
                level.dimension().location(), subLevel.getUniqueId(), changed.size());
    }

    @Nullable
    public static BlockState evaporated(final BlockState state, final boolean vaporizes) {
        if (state.getBlock() instanceof WetSpongeBlock) return Blocks.SPONGE.defaultBlockState();
        if (!vaporizes) return null;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state.getValue(BlockStateProperties.WATERLOGGED)
                    ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                    : null;
        }
        if (state.getBlock() instanceof LiquidBlock
                || state.getBlock() instanceof LiquidBlockContainer
                || state.getBlock() instanceof BucketPickup) {
            return Blocks.AIR.defaultBlockState();
        }
        return null;
    }

    public static boolean vaporizes(final Level level, final BlockPos pos, final FluidState fluid) {
        if (fluid.isEmpty()) return false;
        return fluid.is(FluidTags.WATER)
                || fluid.getFluidType().isVaporizedOnPlacement(level, pos, new FluidStack(fluid.getType(), 1000));
    }

    public static Drained drainPayload(final Level level, final CompoundTag fullTag) {
        final CompoundTag chunks = fullTag.getCompound("plot").getCompound("chunks");
        final List<Site> sites = new ArrayList<>();
        int changed = 0;
        boolean water = false;
        for (final String chunkKey : chunks.getAllKeys()) {
            final ChunkPos local = new ChunkPos(Long.parseLong(chunkKey));
            final CompoundTag sections = chunks.getCompound(chunkKey).getCompound("sections");
            for (final String sectionKey : sections.getAllKeys()) {
                final CompoundTag section = sections.getCompound(sectionKey);
                final PalettedContainer<BlockState> states = readStates(section);
                if (states == null) continue;

                int sectionChanged = 0;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            final BlockState state = states.get(x, y, z);
                            final FluidState fluid = state.getFluidState();
                            final BlockState dried = evaporated(state, vaporizes(level, BlockPos.ZERO, fluid));
                            if (dried == null) continue;
                            states.getAndSetUnchecked(x, y, z, dried);
                            water |= fluid.isSource() && fluid.is(FluidTags.WATER);
                            sites.add(new Site(local, Integer.parseInt(sectionKey), x, y, z));
                            sectionChanged++;
                        }
                    }
                }
                if (sectionChanged == 0) continue;
                section.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, states).getOrThrow());
                changed += sectionChanged;
            }
        }
        return new Drained(changed, water, sites);
    }

    public static void playEffects(final ServerLevel level, final ServerSubLevel subLevel, final Drained drained) {
        final List<BlockPos> changed = new ArrayList<>(drained.sites().size());
        for (final Site site : drained.sites()) {
            final ChunkPos global = subLevel.getPlot().toGlobal(site.chunk());
            changed.add(new BlockPos(
                    global.getMinBlockX() + site.x(),
                    (level.getSectionYFromSectionIndex(site.section()) << 4) + site.y(),
                    global.getMinBlockZ() + site.z()));
        }
        playEffects(level, subLevel, changed);
    }

    @Nullable
    public static PalettedContainer<BlockState> readStates(final CompoundTag section) {
        return BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result().orElse(null);
    }

    private static List<BlockPos> findTargets(final ServerLevel level, final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final List<BlockPos> targets = new ArrayList<>();

        for (final var holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos chunkPos = chunk.getPos();
            final int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                final LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int minY = Math.max(bounds.minY(), sectionMinY);
                final int maxY = Math.min(bounds.maxY(), sectionMinY + 15);

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.getBlock() instanceof WetSpongeBlock || !state.getFluidState().isEmpty()) {
                                targets.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return targets;
    }

    private static void playEffects(final ServerLevel level, final ServerSubLevel subLevel, final List<BlockPos> changed) {
        if (changed.isEmpty()) return;

        final Pose3dc pose = subLevel.logicalPose();
        final double spread = 0.25D * pose.scale().x();
        final int step = Math.max(1, changed.size() / MAX_EFFECT_SITES);
        final Vector3d centre = new Vector3d();
        int sites = 0;
        for (int i = 0; i < changed.size(); i += step) {
            final BlockPos pos = changed.get(i);
            final Vector3d world = pose.transformPosition(new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, world.x, world.y, world.z, 8, spread, spread, spread, 0.0D);
            centre.add(world);
            sites++;
        }

        centre.div(sites);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
    }

    public record Site(ChunkPos chunk, int section, int x, int y, int z) {}

    public record Drained(int changed, boolean water, List<Site> sites) {}

    private NetherEvaporation() {}
}
