package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CaseCloneDiscardPayload(UUID token) implements CustomPacketPayload {
    public static final Type<CaseCloneDiscardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "case_clone_discard"));

    public static final StreamCodec<ByteBuf, CaseCloneDiscardPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(CaseCloneDiscardPayload::new, CaseCloneDiscardPayload::token);

    @Override
    public Type<CaseCloneDiscardPayload> type() {
        return TYPE;
    }
}
