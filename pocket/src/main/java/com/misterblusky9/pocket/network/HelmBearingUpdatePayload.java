package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HelmBearingUpdatePayload(boolean shouldStop, float targetAngle, BlockPos pos)
        implements CustomPacketPayload {
    public static final Type<HelmBearingUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "helm_bearing_update")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, HelmBearingUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HelmBearingUpdatePayload::shouldStop,
                    ByteBufCodecs.FLOAT, HelmBearingUpdatePayload::targetAngle,
                    BlockPos.STREAM_CODEC, HelmBearingUpdatePayload::pos,
                    HelmBearingUpdatePayload::new
            );

    @Override
    public Type<HelmBearingUpdatePayload> type() {
        return TYPE;
    }
}
