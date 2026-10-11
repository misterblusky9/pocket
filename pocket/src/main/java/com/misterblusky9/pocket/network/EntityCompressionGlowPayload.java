package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record EntityCompressionGlowPayload(UUID entityId, boolean growing, int ticks)
        implements CustomPacketPayload {
    public static final Type<EntityCompressionGlowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "entity_compression_glow")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityCompressionGlowPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.entityId());
                        buf.writeBoolean(packet.growing());
                        buf.writeVarInt(packet.ticks());
                    },
                    buf -> new EntityCompressionGlowPayload(
                            buf.readUUID(), buf.readBoolean(), buf.readVarInt())
            );

    @Override
    public Type<EntityCompressionGlowPayload> type() { return TYPE; }

    public static void send(final Entity entity, final boolean growing, final int ticks) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || ticks <= 0) return;
        PacketDistributor.sendToPlayersInDimension(
                level, new EntityCompressionGlowPayload(entity.getUUID(), growing, ticks));
    }

    public static void clear(final Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) clear(level, entity.getUUID());
    }

    public static void clear(final ServerLevel level, final UUID entityId) {
        if (level == null || entityId == null) return;
        PacketDistributor.sendToPlayersInDimension(
                level, new EntityCompressionGlowPayload(entityId, false, 0));
    }
}
