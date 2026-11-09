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

public record BuildMirrorPayload(UUID token, CompoundTag plot, CompoundTag children) implements CustomPacketPayload {
    public static final Type<BuildMirrorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "build_mirror")
    );

    public static final StreamCodec<ByteBuf, BuildMirrorPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BuildMirrorPayload::token,
            ByteBufCodecs.COMPOUND_TAG, BuildMirrorPayload::plot,
            ByteBufCodecs.COMPOUND_TAG, BuildMirrorPayload::children,
            BuildMirrorPayload::new);

    @Override
    public Type<BuildMirrorPayload> type() {
        return TYPE;
    }
}
