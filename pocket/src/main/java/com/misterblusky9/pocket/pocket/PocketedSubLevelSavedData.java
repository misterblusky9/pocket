package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.debug.PocketTrace;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PocketedSubLevelSavedData extends SavedData {
    public static final String FILE_ID = "pocket_pocketed_sublevels";

    private final Map<UUID, CompoundTag> entries = new HashMap<>();
    private final Set<UUID> deployed = new HashSet<>();

    private String dimension = "?";

    private int savedEntryCount = -1;

    public static PocketedSubLevelSavedData getOrLoad(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        PocketedSubLevelSavedData data = level.getChunkSource().getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> created(dimension),
                        (tag, provider) -> load(dimension, tag),
                        DataFixTypes.LEVEL
                ),
                FILE_ID
        );
        data.dimension = dimension;
        return data;
    }

    private static PocketedSubLevelSavedData created(String dimension) {
        PocketedSubLevelSavedData data = new PocketedSubLevelSavedData();
        data.dimension = dimension;
        PocketTrace.debug(
                "[PocketStore] NEW dimension={} entries=0 - no storage file existed", dimension);
        return data;
    }

    private static PocketedSubLevelSavedData load(String dimension, CompoundTag tag) {
        PocketedSubLevelSavedData data = new PocketedSubLevelSavedData();
        data.dimension = dimension;
        CompoundTag entriesTag = tag.getCompound("entries");

        int malformed = 0;
        for (String key : entriesTag.getAllKeys()) {
            try {
                UUID token = UUID.fromString(key);
                data.entries.put(token, entriesTag.getCompound(key).copy());
            } catch (IllegalArgumentException ignored) {
                malformed++;
            }
        }
        for (Tag entry : tag.getList("deployed", Tag.TAG_INT_ARRAY)) {
            data.deployed.add(NbtUtils.loadUUID(entry));
        }

        PocketTrace.debug(
                "[PocketStore] LOAD dimension={} entries={} malformedKeys={} tokens={}",
                dimension, data.entries.size(), malformed, data.tokenSummary());
        if (malformed > 0) {
            PocketTrace.logger().error(
                    "[PocketStore] LOAD dimension={} dropped {} unreadable entries - carriers holding "
                            + "those tokens will report an unreachable payload and will NOT be deleted",
                    dimension, malformed);
        }
        return data;
    }

    public void put(UUID token, CompoundTag subLevelTag) {
        this.entries.put(token, subLevelTag.copy());
        this.setDirty();
        PocketTrace.debug(
                "[PocketStore] PUT dimension={} token={} entries={} dirty=true",
                this.dimension, token, this.entries.size());
    }

    public void commitCapture(ServerLevel level, UUID token, ServerPlayer holder) {
        if (level == null) return;

        level.getChunkSource().getDataStorage().save();
        level.save(null, false, false);

        if (holder != null) {
            holder.getServer().getPlayerList().saveAll();
        }
        this.savedEntryCount = this.entries.size();

        PocketTrace.debug(
                "[PocketStore] COMMIT dimension={} token={} entries={} holder={} - payload, craft "
                        + "removal and carrier are all on disk",
                this.dimension, token, this.entries.size(),
                holder == null ? "none" : holder.getScoreboardName());
        this.auditAfterSave();
    }

    public void commitDeploy(
            ServerLevel sourceLevel,
            ServerLevel targetLevel,
            UUID token,
            ServerPlayer holder
    ) {
        if (sourceLevel == null || targetLevel == null) return;

        sourceLevel.getChunkSource().getDataStorage().save();
        sourceLevel.save(null, false, false);
        if (targetLevel != sourceLevel) {
            targetLevel.getChunkSource().getDataStorage().save();
            targetLevel.save(null, false, false);
        }

        if (holder != null) {
            holder.getServer().getPlayerList().saveAll();
        }
        this.savedEntryCount = this.entries.size();

        PocketTrace.debug(
                "[PocketStore] COMMIT-DEPLOY source={} target={} token={} entries={} holder={} - payload "
                        + "removal, craft and carrier are all on disk",
                sourceLevel.dimension().location(), targetLevel.dimension().location(), token,
                this.entries.size(), holder == null ? "none" : holder.getScoreboardName());
    }

    public CompoundTag getCopy(UUID token) {
        CompoundTag tag = this.entries.get(token);
        return tag == null ? null : tag.copy();
    }

    public void remove(UUID token) {
        if (this.entries.remove(token) != null) {
            this.setDirty();
            PocketTrace.debug(
                    "[PocketStore] REMOVE dimension={} token={} entries={} dirty=true",
                    this.dimension, token, this.entries.size());
        }
    }

    public boolean contains(UUID token) {
        return this.entries.containsKey(token);
    }

    public void markDeployed(UUID token) {
        if (token != null && this.deployed.add(token)) this.setDirty();
    }

    public boolean wasDeployed(UUID token) {
        return token != null && this.deployed.contains(token);
    }

    public int size() {
        return this.entries.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag entriesTag = new CompoundTag();

        for (Map.Entry<UUID, CompoundTag> entry : this.entries.entrySet()) {
            entriesTag.put(entry.getKey().toString(), entry.getValue().copy());
        }

        tag.put("entries", entriesTag);

        ListTag deployedTag = new ListTag();
        for (UUID token : this.deployed) deployedTag.add(NbtUtils.createUUID(token));
        tag.put("deployed", deployedTag);

        this.savedEntryCount = this.entries.size();
        PocketTrace.debug(
                "[PocketStore] SAVE dimension={} entries={} tokens={}",
                this.dimension, this.entries.size(), this.tokenSummary());
        return tag;
    }

    public void auditAfterSave() {
        if (this.savedEntryCount < 0 || this.savedEntryCount == this.entries.size()) return;
        PocketTrace.debugWarn(
                "[PocketStore] RELOAD-RISK dimension={} entries={} lastSaved={} - {} entr{} exist only "
                        + "in memory and would be lost if the server stopped without saving",
                this.dimension, this.entries.size(), this.savedEntryCount,
                this.entries.size() - this.savedEntryCount,
                this.entries.size() - this.savedEntryCount == 1 ? "y" : "ies");
    }

    private String tokenSummary() {
        if (this.entries.isEmpty()) return "[]";
        List<String> tokens = new ArrayList<>(this.entries.size());
        for (UUID token : this.entries.keySet()) tokens.add(token.toString());
        tokens.sort(String::compareTo);
        if (tokens.size() <= 8) return tokens.toString();
        return tokens.subList(0, 8) + " +" + (tokens.size() - 8) + " more (hash=" + tokens.hashCode() + ")";
    }
}
