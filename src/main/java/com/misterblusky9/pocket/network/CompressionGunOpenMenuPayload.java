package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record CompressionGunOpenMenuPayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<CompressionGunOpenMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "compression_gun_open_menu")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CompressionGunOpenMenuPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1),
                    buf -> new CompressionGunOpenMenuPayload(
                            buf.readByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND
                    )
            );

    @Override
    public Type<CompressionGunOpenMenuPayload> type() {
        return TYPE;
    }
}
