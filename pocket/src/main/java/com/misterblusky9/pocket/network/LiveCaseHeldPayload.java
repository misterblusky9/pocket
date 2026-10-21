package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record LiveCaseHeldPayload(List<UUID> held) implements CustomPacketPayload {
    public static final Type<LiveCaseHeldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "live_case_held")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LiveCaseHeldPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .<RegistryFriendlyByteBuf>cast()
                    .map(LiveCaseHeldPayload::new, LiveCaseHeldPayload::held);

    @Override
    public Type<LiveCaseHeldPayload> type() {
        return TYPE;
    }
}
