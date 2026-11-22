package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.PocketKnifeSplit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public record PocketKnifeAssemblePayload(InteractionHand hand, BlockPos min, BlockPos max) implements CustomPacketPayload {
    public static final Type<PocketKnifeAssemblePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "pocket_knife_assemble"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PocketKnifeAssemblePayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.hand() == InteractionHand.OFF_HAND);
                BlockPos.STREAM_CODEC.encode(buf, packet.min());
                BlockPos.STREAM_CODEC.encode(buf, packet.max());
            },
            buf -> new PocketKnifeAssemblePayload(
                    buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf)));

    @Override
    public Type<PocketKnifeAssemblePayload> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        PocketKnifeSplit.assemble(player, hand, min, max);
    }
}
