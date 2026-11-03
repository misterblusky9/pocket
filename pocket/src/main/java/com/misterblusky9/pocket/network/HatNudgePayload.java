package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record HatNudgePayload(BlockPos pos, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<HatNudgePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hat_nudge")
    );

    public static final StreamCodec<ByteBuf, HatNudgePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HatNudgePayload::pos,
            ByteBufCodecs.VAR_INT, HatNudgePayload::x,
            ByteBufCodecs.VAR_INT, HatNudgePayload::y,
            ByteBufCodecs.VAR_INT, HatNudgePayload::z,
            HatNudgePayload::new);

    public void handle(ServerPlayer player) {
        if (player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof BuildHostBlockEntity host)
            host.nudge(player, Integer.signum(x), Integer.signum(y), Integer.signum(z));
    }

    @Override
    public Type<HatNudgePayload> type() {
        return TYPE;
    }
}
