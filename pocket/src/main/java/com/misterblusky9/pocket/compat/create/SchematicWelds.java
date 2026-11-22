package com.misterblusky9.pocket.compat.create;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldTransfer;
import com.misterblusky9.pym.api.spi.SchematicListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SchematicWelds implements SchematicListener {
    private static final String SUB_LEVELS = "sub_levels";
    private static final String WELDS = "pocket_welds";
    private static final ThreadLocal<Map<UUID, ListTag>> LOADED = new ThreadLocal<>();

    public static void loaded(CompoundTag schematic) {
        Map<UUID, ListTag> welds = new HashMap<>();
        ListTag entries = schematic.getList(SUB_LEVELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("uuid") && entry.contains(WELDS, Tag.TAG_LIST))
                welds.put(entry.getUUID("uuid"), entry.getList(WELDS, Tag.TAG_COMPOUND));
        }
        if (welds.isEmpty()) LOADED.remove();
        else LOADED.set(welds);
    }

    @Override
    public void saved(Saved saved) {
        Map<UUID, WeldTransfer.Member> members = new HashMap<>();
        Map<UUID, CompoundTag> entries = new HashMap<>();
        saved.subLevels().forEach((live, captured) -> {
            members.put(live, new WeldTransfer.Member(captured.id(), captured.offset()));
            entries.put(captured.id(), captured.entry());
        });
        WeldTransfer.Body body = new WeldTransfer.Body(
                saved.around() == null ? null : saved.around().getUniqueId(), saved::local);

        ListTag welds = WeldTransfer.capture(CrossScaleWelds.records(saved.level()), members, body);
        for (int i = 0; i < welds.size(); i++) {
            CompoundTag weld = welds.getCompound(i);
            CompoundTag entry = entries.get(weld.getUUID("small"));
            ListTag list = entry.getList(WELDS, Tag.TAG_COMPOUND);
            list.add(weld);
            entry.put(WELDS, list);
        }
    }

    @Override
    public void placed(Placed placed) {
        Map<UUID, ListTag> loaded = LOADED.get();
        LOADED.remove();
        if (loaded == null) return;

        ListTag welds = new ListTag();
        Map<UUID, WeldTransfer.Target> members = new HashMap<>();
        placed.subLevels().forEach((saved, restored) -> {
            members.put(saved, new WeldTransfer.Target(restored.subLevel(), restored.offset()));
            ListTag own = loaded.get(saved);
            if (own != null) welds.addAll(own);
        });
        if (welds.isEmpty()) return;

        WeldTransfer.Target body = placed.body() == null ? null
                : new WeldTransfer.Target(placed.body().subLevel(), placed.body().offset());
        WeldTransfer.restore(placed.level(), welds, members,
                new WeldTransfer.Placement(body, placed.settings(), placed.anchor()));
    }
}
