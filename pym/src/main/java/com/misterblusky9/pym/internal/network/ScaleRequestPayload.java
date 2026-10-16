package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ScaleRequestPayload(UUID subLevelId) implements CustomPacketPayload {
    public static final Type<ScaleRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "scale_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ScaleRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeUUID(packet.subLevelId()),
                    buf -> new ScaleRequestPayload(buf.readUUID())
            );

    @Override
    public Type<ScaleRequestPayload> type() {
        return TYPE;
    }
}
