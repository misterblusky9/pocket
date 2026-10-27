package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SHRINK_RAY_TARGETING =
            COMPONENTS.registerComponentType("shrink_ray_targeting", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODEL_QUARTER_TURNS =
            COMPONENTS.registerComponentType("model_quarter_turns", builder -> builder
                    .persistent(Codec.intRange(0, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CaseSeal>> SEAL =
            COMPONENTS.registerComponentType("seal", builder -> builder
                    .persistent(CaseSeal.CODEC)
                    .networkSynchronized(CaseSeal.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> FLEXIBLE_SHAFT_FIRST =
            COMPONENTS.registerComponentType("flexible_shaft_first", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> MAGNIFYING_GLASS_RAISED =
            COMPONENTS.registerComponentType("magnifying_glass_raised", builder -> builder
                    .networkSynchronized(ByteBufCodecs.BOOL));

    private ModDataComponents() {}
}
