package com.misterblusky9.pocket.item;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllItems;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

public enum PocketSeal implements StringRepresentable {
    SUPER_GLUE("super_glue"),
    HONEY_GLUE("honey_glue"),
    HOT_GLUE("hot_glue");

    public static final Codec<PocketSeal> CODEC = StringRepresentable.fromEnum(PocketSeal::values);
    public static final StreamCodec<FriendlyByteBuf, PocketSeal> STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(PocketSeal.class);

    private final String name;

    PocketSeal(final String name) {
        this.name = name;
    }

    @Nullable
    public static PocketSeal appliedBy(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(AllItems.SUPER_GLUE.get())) return SUPER_GLUE;
        if (stack.is(SimItems.HONEY_GLUE.get())) return HONEY_GLUE;
        if (stack.is(ModItems.GLUE_GUN.get())) return HOT_GLUE;
        return null;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
