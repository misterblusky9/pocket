package com.misterblusky9.pym.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class SchematicScale {
    public static final String KEY = "pym_scale";

    public static @Nullable Double read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY, Tag.TAG_ANY_NUMERIC)) return null;
        double scale = tag.getDouble(KEY);
        return ScaleBounds.isValid(scale) ? scale : null;
    }

    public static void record(ItemStack stack, double scale) {
        if (!ScaleBounds.isValid(scale) || ScaleBounds.same(scale, ScaleBounds.FULL)) return;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putDouble(KEY, scale);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private SchematicScale() {}
}
