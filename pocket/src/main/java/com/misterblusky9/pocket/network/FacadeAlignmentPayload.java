package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.FacadeAlignment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record FacadeAlignmentPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<FacadeAlignmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "facade_alignment")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeAlignmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> BlockPos.STREAM_CODEC.encode(buf, packet.pos()),
                    buf -> new FacadeAlignmentPayload(BlockPos.STREAM_CODEC.decode(buf))
            );

    @Override
    public Type<FacadeAlignmentPayload> type() {
        return TYPE;
    }

    public void handle(final ServerPlayer player) {
        FacadeAlignment.align(player, this.pos);
    }
}
