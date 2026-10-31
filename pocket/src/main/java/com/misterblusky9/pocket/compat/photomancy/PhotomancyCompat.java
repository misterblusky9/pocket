package com.misterblusky9.pocket.compat.photomancy;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PhotomancyCompat implements SableBlueprintEvent {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("pocket", "welds");
    private static final Map<ServerLevel, List<WeldRecord>> PENDING = new WeakHashMap<>();

    public static void register() {
        SableBlueprintEventRegistry.register(new PhotomancyCompat());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, PhotomancyCompat::onLevelTick);
        NeoForge.EVENT_BUS.addListener(PhotomancyCompat::onLevelUnload);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(BlueprintSaveSession session, CompoundTag data) {
        ListTag welds = new ListTag();
        for (WeldRecord weld : WeldStore.get(session.level()).all()) {
            if (weld.worldAnchored()) continue;
            Integer smallId = session.blueprintId(weld.smallSubLevel());
            Integer bigId = session.blueprintId(weld.bigSubLevel());
            if (smallId == null || bigId == null) continue;
            var small = session.blockRef(weld.smallPos());
            var big = session.blockRef(weld.bigPos());
            if (small.isEmpty() || big.isEmpty()) continue;
            if (small.get().subLevelId() != smallId || big.get().subLevelId() != bigId) continue;
            CompoundTag entry = weld.save();
            entry.put("small_ref", saveRef(small.get()));
            entry.put("big_ref", saveRef(big.get()));
            welds.add(entry);
        }
        data.put("welds", welds);
    }

    @Override
    public void onPlaceAfterBlocks(BlueprintPlaceSession session, CompoundTag data) {
        List<WeldRecord> welds = new ArrayList<>();
        ListTag savedWelds = data.getList("welds", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedWelds.size(); i++) {
            CompoundTag entry = savedWelds.getCompound(i);
            WeldRecord original = WeldRecord.load(entry);
            if (original == null || original.worldAnchored()) continue;
            BlueprintBlockRef smallRef = loadRef(entry.getCompound("small_ref"));
            BlueprintBlockRef bigRef = loadRef(entry.getCompound("big_ref"));
            if (smallRef == null || bigRef == null) continue;
            ServerSubLevel small = session.placedSubLevel(smallRef.subLevelId());
            ServerSubLevel big = session.placedSubLevel(bigRef.subLevelId());
            if (small == null || big == null || small == big) continue;
            if (!small.getUniqueId().equals(session.mapSubLevel(original.smallSubLevel()))
                    || !big.getUniqueId().equals(session.mapSubLevel(original.bigSubLevel()))) continue;
            BlockPos smallPos = session.mapBlock(smallRef);
            BlockPos bigPos = session.mapBlock(bigRef);
            if (smallPos == null || bigPos == null) continue;
            Vector3d smallAnchor = translate(original.smallAnchor(), original.smallPos(), smallPos);
            Vector3d bigAnchor = translate(original.bigAnchor(), original.bigPos(), bigPos);
            welds.add(new WeldRecord(
                    UUID.randomUUID(), small.getUniqueId(), big.getUniqueId(),
                    smallPos, bigPos, original.smallFacing(), original.bigFacing(),
                    smallAnchor, bigAnchor, original.smallSpan(), original.bigSpan(),
                    new Quaterniond(original.orientation())));
        }
        if (!welds.isEmpty()) {
            PENDING.computeIfAbsent(session.level(), ignored -> new ArrayList<>()).addAll(welds);
        }
    }

    private static CompoundTag saveRef(BlueprintBlockRef ref) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", ref.subLevelId());
        tag.putLong("pos", ref.localPos().asLong());
        return tag;
    }

    private static BlueprintBlockRef loadRef(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_ANY_NUMERIC) || !tag.contains("pos", Tag.TAG_LONG)) return null;
        return new BlueprintBlockRef(tag.getInt("id"), BlockPos.of(tag.getLong("pos")));
    }

    private static Vector3d translate(Vector3d anchor, BlockPos from, BlockPos to) {
        return new Vector3d(anchor).add(
                to.getX() - from.getX(),
                to.getY() - from.getY(),
                to.getZ() - from.getZ());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<WeldRecord> welds = PENDING.remove(level);
        if (welds == null) return;
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;

        boolean changed = false;
        WeldStore store = WeldStore.get(level);
        for (WeldRecord weld : welds) {
            if (!(container.getSubLevel(weld.smallSubLevel()) instanceof ServerSubLevel small)
                    || !(container.getSubLevel(weld.bigSubLevel()) instanceof ServerSubLevel big)
                    || small.isRemoved() || big.isRemoved()) continue;
            if (level.getBlockState(weld.smallPos()).isAir()
                    || level.getBlockState(weld.bigPos()).isAir()) continue;
            store.add(weld);
            changed = true;
        }
        if (changed) CrossScaleWelds.refreshLoaded(level);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
}
