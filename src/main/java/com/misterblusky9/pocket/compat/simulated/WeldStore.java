package com.misterblusky9.pocket.compat.simulated;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WeldStore extends SavedData {
    private static final String DATA_NAME = "pocket_cross_scale_welds";
    private static final SavedData.Factory<WeldStore> FACTORY =
            new SavedData.Factory<>(WeldStore::new, WeldStore::load);

    private final Map<UUID, WeldRecord> welds = new LinkedHashMap<>();

    public static WeldStore get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<WeldRecord> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(this.welds.values()));
    }

    public boolean isEmpty() {
        return this.welds.isEmpty();
    }

    public WeldRecord get(final UUID weldId) {
        return weldId == null ? null : this.welds.get(weldId);
    }

    public List<WeldRecord> touching(final UUID subLevelId) {
        if (subLevelId == null) return List.of();
        final List<WeldRecord> found = new ArrayList<>();
        for (final WeldRecord record : this.welds.values()) {
            if (record.touches(subLevelId)) found.add(record);
        }
        return found;
    }

    public boolean connected(final UUID first, final UUID second, final UUID exceptWeld) {
        if (first == null || second == null || first.equals(second)) return false;
        for (final WeldRecord record : this.welds.values()) {
            if (exceptWeld != null && exceptWeld.equals(record.weldId())) continue;
            if (record.connects(first, second)) return true;
        }
        return false;
    }

    public void add(final WeldRecord record) {
        if (record == null || record.weldId() == null) return;
        this.welds.put(record.weldId(), record);
        setDirty();
    }

    public WeldRecord remove(final UUID weldId) {
        if (weldId == null) return null;
        final WeldRecord removed = this.welds.remove(weldId);
        if (removed != null) setDirty();
        return removed;
    }

    public List<WeldRecord> removeAllTouching(final UUID subLevelId) {
        if (subLevelId == null) return List.of();
        final List<WeldRecord> removed = new ArrayList<>();
        this.welds.values().removeIf(record -> {
            if (!record.touches(subLevelId)) return false;
            removed.add(record);
            return true;
        });
        if (!removed.isEmpty()) setDirty();
        return removed;
    }

    private static WeldStore load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final WeldStore store = new WeldStore();
        final ListTag list = tag.getList("welds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final WeldRecord record = WeldRecord.load(list.getCompound(i));
            if (record != null) store.welds.put(record.weldId(), record);
        }
        return store;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (final WeldRecord record : this.welds.values()) list.add(record.save());
        tag.put("welds", list);
        return tag;
    }
}
