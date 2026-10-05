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

    CannonExpansionMode(final String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public CannonExpansionMode next() {
        final CannonExpansionMode[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public static CannonExpansionMode fromOrdinal(final int ordinal) {
        final CannonExpansionMode[] all = values();
        return ordinal < 0 || ordinal >= all.length ? IMMEDIATE : all[ordinal];
    }

    public static CannonExpansionMode of(final ItemStack cannon) {
        if (cannon == null || cannon.isEmpty()) return IMMEDIATE;
        final CustomData custom = cannon.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return IMMEDIATE;
        final CompoundTag tag = custom.copyTag();
        return tag.contains(KEY) ? fromOrdinal(tag.getInt(KEY)) : IMMEDIATE;
    }

    public static void set(final ItemStack cannon, final CannonExpansionMode mode) {
        if (cannon == null || cannon.isEmpty() || mode == null) return;
        final CustomData custom = cannon.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putInt(KEY, mode.ordinal());
        cannon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
