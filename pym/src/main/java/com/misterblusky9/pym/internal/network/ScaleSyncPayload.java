package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ScaleSyncPayload(
        UUID subLevelId,
        int interpolationTick,
        double currentScale,
        double targetScale,
        boolean snapInterpolation
) implements CustomPacketPayload {
    public static final Type<ScaleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "scale_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ScaleSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.subLevelId());
                        buf.writeVarInt(packet.interpolationTick());
                        buf.writeDouble(packet.currentScale());
                        buf.writeDouble(packet.targetScale());
                        buf.writeBoolean(packet.snapInterpolation());
                    },
                    buf -> new ScaleSyncPayload(
                            buf.readUUID(),
                            buf.readVarInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<ScaleSyncPayload> type() {
        return TYPE;
    }
}
