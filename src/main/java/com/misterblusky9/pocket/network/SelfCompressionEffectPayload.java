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

public record SelfCompressionEffectPayload(UUID playerId, Kind kind, boolean growing)
        implements CustomPacketPayload {
    public enum Kind {
        BEGIN,
        PULSE,
        RELEASE
    }

    public static final Type<SelfCompressionEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "self_compression_effect")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelfCompressionEffectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.playerId());
                        buf.writeByte(packet.kind.ordinal());
                        buf.writeBoolean(packet.growing());
                    },
                    buf -> new SelfCompressionEffectPayload(
                            buf.readUUID(),
                            Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, buf.readByte()))],
                            buf.readBoolean())
            );

    @Override
    public Type<SelfCompressionEffectPayload> type() {
        return TYPE;
    }

    public static void sendBegin(final ServerPlayer player, final boolean growing) {
        send(player, Kind.BEGIN, growing);
    }

    public static void sendPulse(final ServerPlayer player, final boolean growing) {
        send(player, Kind.PULSE, growing);
    }

    public static void sendRelease(final ServerPlayer player) {
        send(player, Kind.RELEASE, false);
    }

    private static void send(final ServerPlayer player, final Kind kind, final boolean growing) {
        if (player == null || !(player.level() instanceof final ServerLevel level)) return;
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SelfCompressionEffectPayload(player.getUUID(), kind, growing)
        );
    }
}
