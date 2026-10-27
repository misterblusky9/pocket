package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PersonalCompressorScalePayload(double scale) implements CustomPacketPayload {
    public static final Type<PersonalCompressorScalePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "personal_compressor_scale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PersonalCompressorScalePayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeDouble(packet.scale()),
            buf -> new PersonalCompressorScalePayload(buf.readDouble()));

    @Override
    public Type<PersonalCompressorScalePayload> type() { return TYPE; }
}
