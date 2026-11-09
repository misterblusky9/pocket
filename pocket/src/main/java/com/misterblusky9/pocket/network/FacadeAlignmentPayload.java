package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.FacadeAlignment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record FacadeAlignmentPayload(BlockPos pos, int face, int step) implements CustomPacketPayload {
    public FacadeAlignmentPayload(BlockPos pos) {
        this(pos, -1, 0);
    }

    public static final Type<FacadeAlignmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "facade_alignment")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeAlignmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                        buf.writeByte(packet.face());
                        buf.writeByte(packet.step());
                    },
                    buf -> new FacadeAlignmentPayload(
                            BlockPos.STREAM_CODEC.decode(buf), buf.readByte(), buf.readByte())
            );

    @Override
    public Type<FacadeAlignmentPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        if (this.face == -1 && this.step == 0) FacadeAlignment.align(player, this.pos);
        else FacadeAlignment.nudge(player, this.pos, this.face, this.step);
    }
}
