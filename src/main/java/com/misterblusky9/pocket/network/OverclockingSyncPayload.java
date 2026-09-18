package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OverclockingSyncPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<OverclockingSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "overclocking_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OverclockingSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.enabled()),
                    buf -> new OverclockingSyncPayload(buf.readBoolean()));

    @Override
    public Type<OverclockingSyncPayload> type() {
        return TYPE;
    }
}
