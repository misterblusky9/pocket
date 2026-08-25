package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TweezerGrabPayload(
        UUID subLevelId,
        double anchorX, double anchorY, double anchorZ,
        double goalX, double goalY, double goalZ,
        double qx, double qy, double qz, double qw
) implements CustomPacketPayload {
    public static final Type<TweezerGrabPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "tweezer_grab")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TweezerGrabPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelId());
                        buf.writeDouble(p.anchorX());
                        buf.writeDouble(p.anchorY());
                        buf.writeDouble(p.anchorZ());
                        buf.writeDouble(p.goalX());
                        buf.writeDouble(p.goalY());
                        buf.writeDouble(p.goalZ());
                        buf.writeDouble(p.qx());
                        buf.writeDouble(p.qy());
                        buf.writeDouble(p.qz());
                        buf.writeDouble(p.qw());
                    },
                    buf -> new TweezerGrabPayload(
                            buf.readUUID(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override
    public Type<TweezerGrabPayload> type() {
        return TYPE;
    }
}
