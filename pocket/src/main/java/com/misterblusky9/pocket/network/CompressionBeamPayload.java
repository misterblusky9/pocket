package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record CompressionBeamPayload(UUID playerId, boolean firing, boolean growing)
        implements CustomPacketPayload {
    public static final Type<CompressionBeamPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "compression_beam")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CompressionBeamPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.playerId());
                        buf.writeBoolean(packet.firing());
                        buf.writeBoolean(packet.growing());
                    },
                    buf -> new CompressionBeamPayload(
                            buf.readUUID(), buf.readBoolean(), buf.readBoolean())
            );

    @Override
    public Type<CompressionBeamPayload> type() { return TYPE; }

    public static void send(final ServerPlayer player, final boolean firing, final boolean growing) {
        if (player == null || !(player.level() instanceof final ServerLevel level)) return;
        PacketDistributor.sendToPlayersInDimension(
                level, new CompressionBeamPayload(player.getUUID(), firing, growing));
    }
}
