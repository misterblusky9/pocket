package com.misterblusky9.pocket.compat.create;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.build.BuildHostBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;

import java.util.Set;

public final class SchematicPocketed {
    private static final String SUB_LEVELS = "sub_levels";
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final Set<String> SCAFFOLD = Set.of(
            ModBlocks.POCKET_DUMMY.getId().toString(), PocketSized.MOD_ID + ":hatters_head_tile");
    private static final Set<String> STAND_BLOCKS = Set.of(
            ModBlocks.HAT_STAND.getId().toString(), ModBlocks.TALL_HAT_STAND.getId().toString(),
            PocketSized.MOD_ID + ":hatters_stand");
    private static final Set<String> STANDS = Set.of(
            ModBlockEntities.HAT_STAND.getId().toString(), PocketSized.MOD_ID + ":hatters_stand");

    public static void strip(CompoundTag schematic) {
        ListTag subLevels = schematic.getList(SUB_LEVELS, Tag.TAG_COMPOUND);
        subLevels.removeIf(subLevel -> scaffolded((CompoundTag) subLevel));
        if (subLevels.isEmpty()) schematic.remove(SUB_LEVELS);
        for (int i = 0; i < subLevels.size(); i++) template(subLevels.getCompound(i));
        template(schematic);
    }

    private static boolean scaffolded(CompoundTag template) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++)
            if (SCAFFOLD.contains(palette.getCompound(i).getString("Name"))) return true;
        return false;
    }

    private static void template(CompoundTag template) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            String name = state.getString("Name");
            if (SCAFFOLD.contains(name)) {
                state.putString("Name", "minecraft:air");
                state.remove("Properties");
            } else if (STAND_BLOCKS.contains(name) && state.getCompound("Properties").contains(BuildHostBlock.ACTIVE.getName())) {
                state.getCompound("Properties").putString(BuildHostBlock.ACTIVE.getName(), "false");
            }
        }

        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) continue;
            CompoundTag saved = block.getCompound("nbt");
            if (STANDS.contains(saved.getString("id"))) BuildHostBlockEntity.empty(saved);
            stacks(saved);
        }

        ListTag entities = template.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) stacks(entities.getCompound(i).getCompound("nbt"));
    }

    private static void stacks(Tag tag) {
        if (tag instanceof ListTag list) {
            for (Tag entry : list) stacks(entry);
            return;
        }
        if (!(tag instanceof CompoundTag compound)) return;
        if (compound.contains("id", Tag.TAG_STRING) && compound.getCompound("components").contains(CUSTOM_DATA, Tag.TAG_COMPOUND))
            stack(compound);
        for (String key : compound.getAllKeys()) stacks(compound.get(key));
    }

    private static void stack(CompoundTag stack) {
        CompoundTag components = stack.getCompound("components");
        CompoundTag data = components.getCompound(CUSTOM_DATA);
        Item container = PocketCaseItem.savedContainer(data);
        if (container != null) {
            stack.putString("id", BuiltInRegistries.ITEM.getKey(container).toString());
            stack.remove("components");
            return;
        }
        if (!BuildHostBlockEntity.strip(data)) return;
        if (data.isEmpty()) components.remove(CUSTOM_DATA);
        components.remove(ModDataComponents.SEAL.getId().toString());
        if (components.isEmpty()) stack.remove("components");
    }

    private SchematicPocketed() {}
}
