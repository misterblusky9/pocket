package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record FacadeAlignmentSyncPayload(UUID craft, int[] offset) implements CustomPacketPayload {
    public static final Type<FacadeAlignmentSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "facade_alignment_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeAlignmentSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.craft());
                        for (int axis = 0; axis < 3; axis++) buf.writeVarInt(packet.offset()[axis]);
                    },
                    buf -> new FacadeAlignmentSyncPayload(
                            buf.readUUID(),
                            new int[] {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()})
            );

    @Override
    public Type<FacadeAlignmentSyncPayload> type() {
        return TYPE;
    }
}
