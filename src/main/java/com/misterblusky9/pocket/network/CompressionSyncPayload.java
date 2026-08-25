package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record CompressionSyncPayload(
        UUID subLevelId,
        UUID sourcePlayerId,
        BlockPos hitLocalPos,
        int acquireTicks,
        boolean beam,
        boolean release,
        boolean pulse,
        boolean growing,
        int cellLimit
) implements CustomPacketPayload {
    public static final Type<CompressionSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "compression_sync")
    );

    private static final UUID NO_SOURCE = new UUID(0L, 0L);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompressionSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.subLevelId());
                        buf.writeUUID(packet.sourcePlayerId() == null ? NO_SOURCE : packet.sourcePlayerId());
                        buf.writeBlockPos(packet.hitLocalPos());
                        buf.writeVarInt(packet.acquireTicks());
                        buf.writeBoolean(packet.beam());
                        buf.writeBoolean(packet.release());
                        buf.writeBoolean(packet.pulse());
                        buf.writeBoolean(packet.growing());
                        buf.writeVarInt(packet.cellLimit());
                    },
                    buf -> {
                        final UUID subLevelId = buf.readUUID();
                        final UUID source = buf.readUUID();
                        return new CompressionSyncPayload(
                                subLevelId,
                                NO_SOURCE.equals(source) ? null : source,
                                buf.readBlockPos(),
                                buf.readVarInt(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readVarInt()
                        );
                    }
            );

    @Override
    public Type<CompressionSyncPayload> type() { return TYPE; }

    public static void sendBegin(
            final ServerSubLevel subLevel,
            final ServerPlayer source,
            final BlockPos hitLocalPos,
            final int acquireTicks,
            final boolean beam,
            final boolean growing,
            final int cellLimit
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        if (!(subLevel.getLevel() instanceof final ServerLevel level)) return;

        PacketDistributor.sendToPlayersInDimension(level, new CompressionSyncPayload(
                subLevel.getUniqueId(),
                source == null ? null : source.getUUID(),
                hitLocalPos,
                acquireTicks,
                beam,
                false,
                false,
                growing,
                cellLimit
        ));
    }

    public static void sendMachineBegin(
            final ServerSubLevel subLevel,
            final BlockPos originLocalPos,
            final int acquireTicks,
            final boolean growing
    ) {
        sendBegin(subLevel, null, originLocalPos, acquireTicks, true, growing, 0);
    }

    public static void sendPulse(final ServerSubLevel subLevel, final UUID holder) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        if (!(subLevel.getLevel() instanceof final ServerLevel level)) return;
        PacketDistributor.sendToPlayersInDimension(level, new CompressionSyncPayload(
                subLevel.getUniqueId(), holder, BlockPos.ZERO, 0, false, false, true, false, 0
        ));
    }

    public static void sendRelease(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        if (!(subLevel.getLevel() instanceof final ServerLevel level)) return;
        sendRelease(level, subLevel.getUniqueId());
    }

    public static void sendRelease(final ServerLevel level, final UUID subLevelId) {
        if (level == null || subLevelId == null) return;
        PacketDistributor.sendToPlayersInDimension(level, new CompressionSyncPayload(
                subLevelId, null, BlockPos.ZERO, 0, false, true, false, false, 0
        ));
    }
}
