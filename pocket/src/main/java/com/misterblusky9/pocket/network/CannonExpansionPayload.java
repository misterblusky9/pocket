package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.pocket.CannonExpansionMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record CannonExpansionPayload(InteractionHand hand, CannonExpansionMode mode)
        implements CustomPacketPayload {
    public static final Type<CannonExpansionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "cannon_expansion")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CannonExpansionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
                        buf.writeByte(packet.mode().ordinal());
                    },
                    buf -> new CannonExpansionPayload(
                            buf.readByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            CannonExpansionMode.fromOrdinal(buf.readUnsignedByte())
                    )
            );

    @Override
    public Type<CannonExpansionPayload> type() { return TYPE; }
}
