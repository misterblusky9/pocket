package com.misterblusky9.pocket.pocket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public enum CannonExpansionMode {
    NONE("pocket.cannon_expansion.none"),

    IMMEDIATE("pocket.cannon_expansion.immediate"),

    IMPACT("pocket.cannon_expansion.impact");

    private static final String KEY = "PocketCannonExpand";

    private final String translationKey;

    CannonExpansionMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
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
