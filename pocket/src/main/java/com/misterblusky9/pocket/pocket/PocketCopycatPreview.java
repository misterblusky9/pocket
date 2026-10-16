package com.misterblusky9.pocket.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PocketCopycatPreview {
    public static final String COPYCATS_KEY = "preview_copycats";

    private static final String POS_KEY = "p";
    private static final String DATA_KEY = "d";
    private static final String MATERIAL_KEY = "Material";
    private static final String MATERIAL_DATA_KEY = "material_data";
    private static final String ENABLE_CT_KEY = "EnableCT";

    public record Entry(int x, int y, int z, CompoundTag data) {
        public Entry {
            data = data == null ? new CompoundTag() : data.copy();
        }
    }

    public record PackedEntry(int packedPos, CompoundTag data) {
        public PackedEntry {
            data = data == null ? new CompoundTag() : data.copy();
        }
    }

    public static Entry capture(
            Level level,
            BlockPos pos,
            BlockState state,
            int originX,
            int originY,
            int originZ
    ) {
        if (level == null || state == null || !state.hasBlockEntity()) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !looksLikeCopycat(blockEntity, state)) return null;

        try {
            CompoundTag saved = blockEntity.saveWithoutMetadata(level.registryAccess());
            CompoundTag renderData = new CompoundTag();

            if (saved.contains(MATERIAL_KEY, Tag.TAG_COMPOUND)) {
                renderData.put(MATERIAL_KEY, saved.getCompound(MATERIAL_KEY).copy());
            }
            if (saved.contains(MATERIAL_DATA_KEY, Tag.TAG_COMPOUND)) {
                renderData.put(MATERIAL_DATA_KEY, saved.getCompound(MATERIAL_DATA_KEY).copy());
            }
            if (saved.contains(ENABLE_CT_KEY)) {
                renderData.putBoolean(ENABLE_CT_KEY, saved.getBoolean(ENABLE_CT_KEY));
            }

            if (renderData.isEmpty()) return null;
            return new Entry(
                    pos.getX() - originX,
                    pos.getY() - originY,
                    pos.getZ() - originZ,
                    renderData
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ListTag encode(List<Entry> entries) {
        ListTag list = new ListTag();
        if (entries == null || entries.isEmpty()) return list;

        for (Entry entry : entries) {
            if (entry.x() < 0 || entry.y() < 0 || entry.z() < 0
                    || entry.x() > 255 || entry.y() > 255 || entry.z() > 255
                    || entry.data().isEmpty()) {
                continue;
            }

            CompoundTag encoded = new CompoundTag();
            encoded.putInt(POS_KEY, ShellVoxels.packPosition(entry.x(), entry.y(), entry.z()));
            encoded.put(DATA_KEY, entry.data().copy());
            list.add(encoded);
        }
        return list;
    }

    public static List<PackedEntry> decode(ListTag list) {
        if (list == null || list.isEmpty()) return List.of();

        List<PackedEntry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag encoded = list.getCompound(i);
            if (!encoded.contains(POS_KEY) || !encoded.contains(DATA_KEY, Tag.TAG_COMPOUND)) continue;

            CompoundTag data = encoded.getCompound(DATA_KEY);
            if (data.isEmpty()) continue;
            entries.add(new PackedEntry(encoded.getInt(POS_KEY), data));
        }
        return entries;
    }

    private static boolean looksLikeCopycat(BlockEntity blockEntity, BlockState state) {
        if (blockEntity.getClass().getName().toLowerCase(Locale.ROOT).contains("copycat")) return true;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return false;
        if ("copycats".equals(blockId.getNamespace())) return true;
        return "create".equals(blockId.getNamespace()) && blockId.getPath().startsWith("copycat");
    }

    private PocketCopycatPreview() {}
}
