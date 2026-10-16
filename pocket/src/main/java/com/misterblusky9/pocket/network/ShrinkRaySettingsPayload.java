package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record ShrinkRaySettingsPayload(
        InteractionHand hand,
        double scale,
        int targetingMode
) implements CustomPacketPayload {
    public static final Type<ShrinkRaySettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "shrink_ray_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShrinkRaySettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.hand() == InteractionHand.MAIN_HAND);
                buf.writeDouble(packet.scale());
                buf.writeVarInt(packet.targetingMode());
            },
            buf -> new ShrinkRaySettingsPayload(
                    buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    buf.readDouble(),
                    buf.readVarInt()));

    @Override
    public Type<ShrinkRaySettingsPayload> type() {
        return TYPE;
    }
}
