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

public record HatNativeContraptionsPayload(UUID token, CompoundTag states) implements CustomPacketPayload {
    public static final Type<HatNativeContraptionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hat_native_contraptions"));
    public static final StreamCodec<ByteBuf, HatNativeContraptionsPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HatNativeContraptionsPayload::token,
            ByteBufCodecs.COMPOUND_TAG, HatNativeContraptionsPayload::states,
            HatNativeContraptionsPayload::new);

    @Override
    public Type<HatNativeContraptionsPayload> type() {
        return TYPE;
    }
}
