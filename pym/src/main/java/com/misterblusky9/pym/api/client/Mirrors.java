package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.Rules;
import com.misterblusky9.pym.internal.PlacedSubLevels;
import com.misterblusky9.pym.internal.mixin.sable.SubLevelContainerAccessor;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Mirrors {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());
    private static final Set<ClientSubLevel> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());

    public static @Nullable ClientSubLevel open(ClientLevel level, UUID id, CompoundTag plot) {
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || plot.getInt("log_size") != container.getLogPlotSize()) return null;
        int x = plot.getInt("plot_x");
        int z = plot.getInt("plot_z");
        int side = 1 << container.getLogSideLength();
        CompoundTag chunks = plot.getCompound("chunks");
        if (x < 0 || z < 0 || x >= side || z >= side || container.getSubLevel(x, z) != null
                || chunks.getAllKeys().stream().allMatch(key -> chunks.getCompound(key).getCompound("sections").isEmpty()))
            return null;

        ClientSubLevel mirror = new ClientSubLevel(level, x + container.getOrigin().x, z + container.getOrigin().y,
                new Pose3d());
        mirror.setUniqueId(id);
        ((SubLevelContainerAccessor) container).pym$getSubLevels()[container.getIndex(x, z)] = mirror;
        OPEN.add(mirror);

        ClientLevelPlot target = mirror.getPlot();
        LevelLightEngine light = level.getLightEngine();
        BoundingBox3i bounds = null;
        Map<BlockEntity, CompoundTag> loads = new HashMap<>();
        for (String key : chunks.getAllKeys()) {
            long packed = Long.parseLong(key);
            ChunkPos local = new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            ChunkPos global = target.toGlobal(local);
            PlotChunkHolder holder = PlotChunkHolder.create(level, global, light,
                    chunk(level, global, chunks.getCompound(key), light, loads));
            target.addChunkHolder(local, holder, false);
            BoundingBox3ic box = holder.getBoundingBox();
            if (box == null) continue;
            BoundingBox3i moved = box.move(global.getMinBlockX(), 0, global.getMinBlockZ(),
                    new BoundingBox3i(0, 0, 0, 0, 0, 0));
            bounds = bounds == null ? moved : bounds.expandTo(moved, bounds);
        }
        target.setBoundingBox(bounds);
        mirror.updateRenderData();
        loads.forEach((blockEntity, data) -> blockEntity.loadWithComponents(data, level.registryAccess()));
        light.runLightUpdates();
        mirror.setFinalized();
        return mirror;
    }

    private static LevelChunk chunk(ClientLevel level, ChunkPos pos, CompoundTag tag, LevelLightEngine light,
                                    Map<BlockEntity, CompoundTag> loads) {
        LevelChunk chunk = new LevelChunk(level, pos);
        LevelChunkSection[] sections = chunk.getSections();
        CompoundTag sectionsTag = tag.getCompound("sections");
        light.retainData(pos, true);
        for (String key : sectionsTag.getAllKeys()) {
            int index = Integer.parseInt(key);
            CompoundTag section = sectionsTag.getCompound(key);
            sections[index] = new LevelChunkSection(
                    BLOCK_STATES.parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow(),
                    sections[index].getBiomes());
            SectionPos sectionPos = SectionPos.of(pos, level.getSectionYFromSectionIndex(index));
            if (section.contains("BlockLight", Tag.TAG_BYTE_ARRAY))
                light.queueSectionData(LightLayer.BLOCK, sectionPos, new DataLayer(section.getByteArray("BlockLight")));
            if (section.contains("SkyLight", Tag.TAG_BYTE_ARRAY))
                light.queueSectionData(LightLayer.SKY, sectionPos, new DataLayer(section.getByteArray("SkyLight")));
            light.updateSectionStatus(sectionPos, sections[index].hasOnlyAir());
        }
        light.setLightEnabled(pos, true);
        for (Tag entry : tag.getList("block_entities", Tag.TAG_COMPOUND)) {
            CompoundTag data = (CompoundTag) entry;
            BlockPos at = BlockEntity.getPosFromTag(data);
            BlockState state = chunk.getBlockState(at);
            if (!(state.getBlock() instanceof EntityBlock block)) continue;
            BlockEntity blockEntity = block.newBlockEntity(at, state);
            if (blockEntity == null) continue;
            blockEntity.setLevel(level);
            chunk.getBlockEntities().put(at, blockEntity);
            loads.put(blockEntity, data);
        }
        return chunk;
    }

    public static boolean isMirror(SubLevel subLevel) {
        return subLevel instanceof ClientSubLevel client && OPEN.contains(client);
    }

    public static void moveTo(ClientSubLevel mirror, Pose3dc pose) {
        mirror.updateLastPose();
        mirror.logicalPose().set(pose);
        mirror.forceUpdateBounds();
    }

    public static void snapTo(ClientSubLevel mirror, Pose3dc pose) {
        mirror.logicalPose().set(pose);
        mirror.updateLastPose();
        mirror.forceUpdateBounds();
    }

    public static void rules(ClientSubLevel mirror, Rules rules) {
        if (!rules.interact()) PlacedSubLevels.remove(mirror);
        else if (OPEN.contains(mirror)) PlacedSubLevels.add(mirror);
    }

    public static void close(ClientSubLevel mirror) {
        if (!OPEN.remove(mirror)) return;
        PlacedSubLevels.remove(mirror);
        ClientSubLevelContainer container = SubLevelContainer.getContainer(mirror.getLevel());
        LevelLightEngine light = mirror.getLevel().getLightEngine();
        for (PlotChunkHolder holder : mirror.getPlot().getLoadedChunks()) {
            ChunkPos pos = holder.getPos();
            for (int y = light.getMinLightSection(); y < light.getMaxLightSection(); y++) {
                light.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, y), null);
                light.queueSectionData(LightLayer.SKY, SectionPos.of(pos, y), null);
            }
            light.retainData(pos, false);
        }
        ChunkPos plot = mirror.getPlot().plotPos;
        SubLevel[] slots = ((SubLevelContainerAccessor) container).pym$getSubLevels();
        int index = container.getIndex(plot.x - container.getOrigin().x, plot.z - container.getOrigin().y);
        if (slots[index] == mirror) slots[index] = null;
        mirror.onRemove();
    }

    public static void closeAll() {
        for (ClientSubLevel mirror : List.copyOf(OPEN)) close(mirror);
    }

    private Mirrors() {}
}
