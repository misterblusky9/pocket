package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DebugPayload(boolean on) implements CustomPacketPayload {
    public static final Type<DebugPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBoolean(packet.on()),
            buf -> new DebugPayload(buf.readBoolean()));

    @Override
    public Type<DebugPayload> type() {
        return TYPE;
    }
}
