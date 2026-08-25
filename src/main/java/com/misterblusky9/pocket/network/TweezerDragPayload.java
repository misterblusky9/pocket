package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TweezerDragPayload(
        double goalX, double goalY, double goalZ,
        double qx, double qy, double qz, double qw
) implements CustomPacketPayload {
    public static final Type<TweezerDragPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "tweezer_drag")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TweezerDragPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.goalX());
                        buf.writeDouble(p.goalY());
                        buf.writeDouble(p.goalZ());
                        buf.writeDouble(p.qx());
                        buf.writeDouble(p.qy());
                        buf.writeDouble(p.qz());
                        buf.writeDouble(p.qw());
                    },
                    buf -> new TweezerDragPayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override
    public Type<TweezerDragPayload> type() {
        return TYPE;
    }
}
