package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PersonalCompressorUsePayload() implements CustomPacketPayload {
    public static final PersonalCompressorUsePayload INSTANCE = new PersonalCompressorUsePayload();
    public static final Type<PersonalCompressorUsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "personal_compressor_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PersonalCompressorUsePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<PersonalCompressorUsePayload> type() { return TYPE; }
}
