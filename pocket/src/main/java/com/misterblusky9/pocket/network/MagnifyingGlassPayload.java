package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagnifyingGlassPayload(boolean zoomed) implements CustomPacketPayload {
    public static final Type<MagnifyingGlassPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "magnifying_glass")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MagnifyingGlassPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(MagnifyingGlassPayload::new, MagnifyingGlassPayload::zoomed).cast();

    @Override
    public Type<MagnifyingGlassPayload> type() {
        return TYPE;
    }
}
