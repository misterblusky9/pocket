package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuildRiderPosesPayload(UUID token, long tick, CompoundTag poses) implements CustomPacketPayload {
    public static final Type<BuildRiderPosesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "build_rider_poses")
    );

    public static final StreamCodec<ByteBuf, BuildRiderPosesPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BuildRiderPosesPayload::token,
            ByteBufCodecs.VAR_LONG, BuildRiderPosesPayload::tick,
            ByteBufCodecs.COMPOUND_TAG, BuildRiderPosesPayload::poses,
            BuildRiderPosesPayload::new);

    @Override
    public Type<BuildRiderPosesPayload> type() {
        return TYPE;
    }
}
