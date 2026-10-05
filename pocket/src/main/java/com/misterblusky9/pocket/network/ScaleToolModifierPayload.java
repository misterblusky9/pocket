package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScaleToolModifierPayload(boolean targetOnly) implements CustomPacketPayload {
    public static final Type<ScaleToolModifierPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "scale_tool_modifier")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ScaleToolModifierPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ScaleToolModifierPayload::targetOnly,
                    ScaleToolModifierPayload::new
            );

    @Override
    public Type<ScaleToolModifierPayload> type() {
        return TYPE;
    }
}
