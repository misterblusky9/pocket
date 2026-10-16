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
    private long revision;

    public static WeldStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<WeldRecord> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(this.welds.values()));
    }

    public boolean isEmpty() {
        return this.welds.isEmpty();
    }

    public long revision() {
        return this.revision;
    }

    public WeldRecord get(UUID weldId) {
        return weldId == null ? null : this.welds.get(weldId);
    }

    public List<WeldRecord> touching(UUID subLevelId) {
        if (subLevelId == null) return List.of();
        List<WeldRecord> found = new ArrayList<>();
        for (WeldRecord record : this.welds.values()) {
            if (record.touches(subLevelId)) found.add(record);
        }
        return found;
    }

    public boolean connected(UUID first, UUID second, UUID exceptWeld) {
        if (first == null || second == null || first.equals(second)) return false;
        for (WeldRecord record : this.welds.values()) {
            if (exceptWeld != null && exceptWeld.equals(record.weldId())) continue;
            if (record.connects(first, second)) return true;
        }
        return false;
    }

    public void add(WeldRecord record) {
        if (record == null || record.weldId() == null) return;
        this.welds.put(record.weldId(), record);
        this.revision++;
        setDirty();
    }

    public WeldRecord remove(UUID weldId) {
        if (weldId == null) return null;
        WeldRecord removed = this.welds.remove(weldId);
        if (removed != null) {
            this.revision++;
            setDirty();
        }
        return removed;
    }

    public List<WeldRecord> removeAllTouching(UUID subLevelId) {
        if (subLevelId == null) return List.of();
        List<WeldRecord> removed = new ArrayList<>();
        this.welds.values().removeIf(record -> {
            if (!record.touches(subLevelId)) return false;
            removed.add(record);
            return true;
        });
        if (!removed.isEmpty()) {
            this.revision++;
            setDirty();
        }
        return removed;
    }

    private static WeldStore load(CompoundTag tag, HolderLookup.Provider registries) {
        WeldStore store = new WeldStore();
        ListTag list = tag.getList("welds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WeldRecord record = WeldRecord.load(list.getCompound(i));
            if (record != null) store.welds.put(record.weldId(), record);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WeldRecord record : this.welds.values()) list.add(record.save());
        tag.put("welds", list);
        return tag;
    }
}
