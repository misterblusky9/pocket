package com.misterblusky9.pocket.particle;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOT_GLUE =
            PARTICLE_TYPES.register("hot_glue", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, ParticleType<SpinIndicatorParticleData>> SPIN_INDICATOR =
            PARTICLE_TYPES.register("spin_indicator", () -> new ParticleType<>(false) {
                @Override
                public MapCodec<SpinIndicatorParticleData> codec() {
                    return SpinIndicatorParticleData.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, SpinIndicatorParticleData> streamCodec() {
                    return SpinIndicatorParticleData.STREAM_CODEC;
                }
            });

    private ModParticles() {}
}
