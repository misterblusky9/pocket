package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SchematicFinePayload(int slot, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<SchematicFinePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "schematic_fine")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SchematicFinePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.slot());
                        buf.writeDouble(packet.x());
                        buf.writeDouble(packet.y());
                        buf.writeDouble(packet.z());
                    },
                    buf -> new SchematicFinePayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override
    public Type<SchematicFinePayload> type() {
        return TYPE;
    }
}
