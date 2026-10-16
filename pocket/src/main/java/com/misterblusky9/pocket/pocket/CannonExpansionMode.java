package com.misterblusky9.pocket.pocket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public enum CannonExpansionMode {
    NONE("None"),

    IMMEDIATE("Immediate"),

    IMPACT("Impact");

    private static final String KEY = "PocketCannonExpand";

    private final String label;

    CannonExpansionMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public CannonExpansionMode next() {
        CannonExpansionMode[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public static CannonExpansionMode fromOrdinal(int ordinal) {
        CannonExpansionMode[] all = values();
        return ordinal < 0 || ordinal >= all.length ? IMMEDIATE : all[ordinal];
    }

    public static CannonExpansionMode of(ItemStack cannon) {
        if (cannon == null || cannon.isEmpty()) return IMMEDIATE;
        CustomData custom = cannon.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return IMMEDIATE;
        CompoundTag tag = custom.copyTag();
        return tag.contains(KEY) ? fromOrdinal(tag.getInt(KEY)) : IMMEDIATE;
    }

    public static void set(ItemStack cannon, CannonExpansionMode mode) {
        if (cannon == null || cannon.isEmpty() || mode == null) return;
        CustomData custom = cannon.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putInt(KEY, mode.ordinal());
        cannon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
