package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EntityScaleSyncPayload(
        int entityId,
        double currentScale,
        double targetScale,
        int remainingTicks
) implements CustomPacketPayload {
    public static final Type<EntityScaleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "entity_scale_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScaleSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.entityId());
                        buf.writeDouble(packet.currentScale());
                        buf.writeDouble(packet.targetScale());
                        buf.writeVarInt(packet.remainingTicks());
                    },
                    buf -> new EntityScaleSyncPayload(
                            buf.readVarInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<EntityScaleSyncPayload> type() {
        return TYPE;
    }
}
