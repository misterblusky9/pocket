package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record ShrinkRayStagePayload(InteractionHand hand, CompressionStage stage) implements CustomPacketPayload {
    public static final Type<ShrinkRayStagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "shrink_ray_stage")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShrinkRayStagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
                buf.writeByte(packet.stage().depth());
            },
            buf -> new ShrinkRayStagePayload(
                    buf.readByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    CompressionStage.fromDepth(buf.readUnsignedByte())
            )
    );

    @Override
    public Type<ShrinkRayStagePayload> type() { return TYPE; }
}
