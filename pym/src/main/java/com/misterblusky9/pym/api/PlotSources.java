package com.misterblusky9.pym.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class PlotSources {
    public static final String DIMENSION_KEY = "pym_source_dimension";

    public static @Nullable ResourceLocation dimension(@Nullable CompoundTag data, String legacyKey) {
        if (data == null) return null;
        ResourceLocation value = ResourceLocation.tryParse(data.getString(DIMENSION_KEY));
        return value == null ? ResourceLocation.tryParse(data.getString(legacyKey)) : value;
    }

    public static void write(CompoundTag data, ResourceLocation dimension) {
        data.putString(DIMENSION_KEY, dimension.toString());
    }

    public static @Nullable ServerLevel resolve(ServerLevel current, @Nullable ResourceLocation dimension) {
        return dimension == null ? null : current.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private PlotSources() {}
}
