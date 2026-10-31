package com.misterblusky9.pym.internal.compat.photomancy;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.PymMod;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BlueprintScales implements SableBlueprintEvent {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "scales");
    private static final Map<ServerLevel, List<Map<ServerSubLevel, Double>>> PENDING = new WeakHashMap<>();

    public static void register() {
        SableBlueprintEventRegistry.register(new BlueprintScales());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlueprintScales::onLevelTick);
        NeoForge.EVENT_BUS.addListener(BlueprintScales::onLevelUnload);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(BlueprintSaveSession session, CompoundTag data) {
        ListTag scales = new ListTag();
        for (SubLevelSaveFrame frame : session.frames()) {
            double scale = Pym.scale().settled(frame.subLevel());
            if (!ScaleBounds.isValid(scale)) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", frame.blueprintId());
            entry.putDouble("scale", scale);
            scales.add(entry);
        }
        data.put("scales", scales);
    }

    @Override
    public void onPlaceAfterBlocks(BlueprintPlaceSession session, CompoundTag data) {
        Map<ServerSubLevel, Double> scales = new HashMap<>();
        ListTag saved = data.getList("scales", Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            if (!entry.contains("scale", Tag.TAG_ANY_NUMERIC)) continue;
            double scale = entry.getDouble("scale");
            ServerSubLevel subLevel = session.placedSubLevel(entry.getInt("id"));
            if (subLevel != null && ScaleBounds.isValid(scale)) scales.put(subLevel, scale);
        }
        if (!scales.isEmpty()) {
            PENDING.computeIfAbsent(session.level(), ignored -> new ArrayList<>()).add(scales);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<Map<ServerSubLevel, Double>> batches = PENDING.remove(level);
        if (batches == null) return;
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;

        for (Map<ServerSubLevel, Double> batch : batches) {
            for (var entry : batch.entrySet()) {
                ServerSubLevel subLevel = entry.getKey();
                if (subLevel.isRemoved() || container.getSubLevel(subLevel.getUniqueId()) != subLevel) continue;
                Pym.resize().request(subLevel).scaleTo(entry.getValue()).immediate().submit();
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    private BlueprintScales() {}
}
