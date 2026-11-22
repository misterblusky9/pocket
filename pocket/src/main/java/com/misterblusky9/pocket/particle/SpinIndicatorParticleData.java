package com.misterblusky9.pocket.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public record SpinIndicatorParticleData(int color, float speed, float radius1, float radius2, int lifeSpan,
                                        Direction.Axis axis) implements ParticleOptions {
    public static final MapCodec<SpinIndicatorParticleData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("color").forGetter(SpinIndicatorParticleData::color),
            Codec.FLOAT.fieldOf("speed").forGetter(SpinIndicatorParticleData::speed),
            Codec.FLOAT.fieldOf("radius1").forGetter(SpinIndicatorParticleData::radius1),
            Codec.FLOAT.fieldOf("radius2").forGetter(SpinIndicatorParticleData::radius2),
            Codec.INT.fieldOf("life_span").forGetter(SpinIndicatorParticleData::lifeSpan),
            Direction.Axis.CODEC.fieldOf("axis").forGetter(SpinIndicatorParticleData::axis)
    ).apply(i, SpinIndicatorParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpinIndicatorParticleData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SpinIndicatorParticleData::color,
                    ByteBufCodecs.FLOAT, SpinIndicatorParticleData::speed,
                    ByteBufCodecs.FLOAT, SpinIndicatorParticleData::radius1,
                    ByteBufCodecs.FLOAT, SpinIndicatorParticleData::radius2,
                    ByteBufCodecs.VAR_INT, SpinIndicatorParticleData::lifeSpan,
                    NeoForgeStreamCodecs.enumCodec(Direction.Axis.class), SpinIndicatorParticleData::axis,
                    SpinIndicatorParticleData::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SPIN_INDICATOR.get();
    }
}
