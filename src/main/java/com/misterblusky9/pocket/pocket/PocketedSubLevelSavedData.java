package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.debug.PocketTrace;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PocketedSubLevelSavedData extends SavedData {
    public static final String FILE_ID = "pocket_pocketed_sublevels";

    private final Map<UUID, CompoundTag> entries = new HashMap<>();

    private String dimension = "?";

    private int savedEntryCount = -1;

    public static PocketedSubLevelSavedData getOrLoad(final ServerLevel level) {
        final String dimension = level.dimension().location().toString();
        final PocketedSubLevelSavedData data = level.getChunkSource().getDataStorage().computeIfAbsent(
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

    private static PocketedSubLevelSavedData created(final String dimension) {
        final PocketedSubLevelSavedData data = new PocketedSubLevelSavedData();
        data.dimension = dimension;
        PocketTrace.logger().info(
                "[PocketStore] NEW dimension={} entries=0 - no storage file existed", dimension);
        return data;
    }

    private static PocketedSubLevelSavedData load(final String dimension, final CompoundTag tag) {
        final PocketedSubLevelSavedData data = new PocketedSubLevelSavedData();
        data.dimension = dimension;
        final CompoundTag entriesTag = tag.getCompound("entries");

        int malformed = 0;
        for (final String key : entriesTag.getAllKeys()) {
            try {
                final UUID token = UUID.fromString(key);
                data.entries.put(token, entriesTag.getCompound(key).copy());
            } catch (final IllegalArgumentException ignored) {
                malformed++;
            }
        }

        PocketTrace.logger().info(
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

    public void put(final UUID token, final CompoundTag subLevelTag) {
        this.entries.put(token, subLevelTag.copy());
        this.setDirty();
        PocketTrace.logger().info(
                "[PocketStore] PUT dimension={} token={} entries={} dirty=true",
                this.dimension, token, this.entries.size());
    }

    public void commitCapture(final ServerLevel level, final UUID token, final ServerPlayer holder) {
        if (level == null) return;

        level.getChunkSource().getDataStorage().save();
        level.save(null, false, false);

        if (holder != null) {
            holder.getServer().getPlayerList().saveAll();
        }
        this.savedEntryCount = this.entries.size();

        PocketTrace.logger().info(
                "[PocketStore] COMMIT dimension={} token={} entries={} holder={} - payload, craft "
                        + "removal and carrier are all on disk",
                this.dimension, token, this.entries.size(),
                holder == null ? "none" : holder.getScoreboardName());
        this.auditAfterSave();
    }

    public void commitDeploy(
            final ServerLevel sourceLevel,
            final ServerLevel targetLevel,
            final UUID token,
            final ServerPlayer holder
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

        PocketTrace.logger().info(
                "[PocketStore] COMMIT-DEPLOY source={} target={} token={} entries={} holder={} - payload "
                        + "removal, craft and carrier are all on disk",
                sourceLevel.dimension().location(), targetLevel.dimension().location(), token,
                this.entries.size(), holder == null ? "none" : holder.getScoreboardName());
    }

    public CompoundTag getCopy(final UUID token) {
        final CompoundTag tag = this.entries.get(token);
        return tag == null ? null : tag.copy();
    }

    public void remove(final UUID token) {
        if (this.entries.remove(token) != null) {
            this.setDirty();
            PocketTrace.logger().info(
                    "[PocketStore] REMOVE dimension={} token={} entries={} dirty=true",
                    this.dimension, token, this.entries.size());
        }
    }

    public boolean contains(final UUID token) {
        return this.entries.containsKey(token);
    }

    public int size() {
        return this.entries.size();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        final CompoundTag entriesTag = new CompoundTag();

        for (final Map.Entry<UUID, CompoundTag> entry : this.entries.entrySet()) {
            entriesTag.put(entry.getKey().toString(), entry.getValue().copy());
        }

        tag.put("entries", entriesTag);

        this.savedEntryCount = this.entries.size();
        PocketTrace.logger().info(
                "[PocketStore] SAVE dimension={} entries={} tokens={}",
                this.dimension, this.entries.size(), this.tokenSummary());
        return tag;
    }

    public void auditAfterSave() {
        if (this.savedEntryCount < 0 || this.savedEntryCount == this.entries.size()) return;
        PocketTrace.logger().warn(
                "[PocketStore] RELOAD-RISK dimension={} entries={} lastSaved={} - {} entr{} exist only "
                        + "in memory and would be lost if the server stopped without saving",
                this.dimension, this.entries.size(), this.savedEntryCount,
                this.entries.size() - this.savedEntryCount,
                this.entries.size() - this.savedEntryCount == 1 ? "y" : "ies");
    }

    private String tokenSummary() {
        if (this.entries.isEmpty()) return "[]";
        final List<String> tokens = new ArrayList<>(this.entries.size());
        for (final UUID token : this.entries.keySet()) tokens.add(token.toString());
        tokens.sort(String::compareTo);
        if (tokens.size() <= 8) return tokens.toString();
        return tokens.subList(0, 8) + " +" + (tokens.size() - 8) + " more (hash=" + tokens.hashCode() + ")";
    }
}
