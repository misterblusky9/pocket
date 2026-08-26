package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CompressionGunTargetingMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record CompressionGunSettingsPayload(
        InteractionHand hand,
        CompressionGunTargetingMode targetingMode,
        boolean growing
) implements CustomPacketPayload {
    public static final Type<CompressionGunSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "compression_gun_settings")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CompressionGunSettingsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
                        buf.writeByte(packet.targetingMode().id());
                        buf.writeBoolean(packet.growing());
                    },
                    buf -> new CompressionGunSettingsPayload(
                            buf.readByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            CompressionGunTargetingMode.fromId(buf.readUnsignedByte()),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<CompressionGunSettingsPayload> type() {
        return TYPE;
    }
}
