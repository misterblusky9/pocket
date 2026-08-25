package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ScaleSyncPayload(
        UUID subLevelId,
        double currentScale,
        double targetScale,
        boolean snapInterpolation
) implements CustomPacketPayload {
    public static final Type<ScaleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "scale_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ScaleSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.subLevelId());

                        buf.writeFloat((float) packet.currentScale());
                        buf.writeFloat((float) packet.targetScale());
                        buf.writeBoolean(packet.snapInterpolation());
                    },
                    buf -> new ScaleSyncPayload(
                            buf.readUUID(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<ScaleSyncPayload> type() {
        return TYPE;
    }
}
