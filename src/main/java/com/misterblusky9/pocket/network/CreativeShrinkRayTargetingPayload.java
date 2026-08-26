package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CompressionGunTargetingMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record CreativeShrinkRayTargetingPayload(
        InteractionHand hand,
        CompressionGunTargetingMode targetingMode
) implements CustomPacketPayload {
    public static final Type<CreativeShrinkRayTargetingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "creative_shrink_ray_targeting")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeShrinkRayTargetingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
                        buf.writeByte(packet.targetingMode().id());
                    },
                    buf -> new CreativeShrinkRayTargetingPayload(
                            buf.readByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            CompressionGunTargetingMode.fromId(buf.readUnsignedByte())
                    )
            );

    @Override
    public Type<CreativeShrinkRayTargetingPayload> type() {
        return TYPE;
    }
}
