package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record ShrinkRayScalePayload(InteractionHand hand, double scale) implements CustomPacketPayload {
    public static final Type<ShrinkRayScalePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "shrink_ray_scale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShrinkRayScalePayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.hand() == InteractionHand.MAIN_HAND);
                buf.writeDouble(packet.scale());
            },
            buf -> new ShrinkRayScalePayload(
                    buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    buf.readDouble()));

    @Override
    public Type<ShrinkRayScalePayload> type() { return TYPE; }
}
