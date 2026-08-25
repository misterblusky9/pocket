package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TweezerLocksPayload(List<UUID> locked) implements CustomPacketPayload {
    public static final Type<TweezerLocksPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "tweezer_locks")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TweezerLocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.locked().size());
                        for (final UUID id : p.locked()) buf.writeUUID(id);
                    },
                    buf -> {
                        final int count = buf.readVarInt();
                        final List<UUID> ids = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) ids.add(buf.readUUID());
                        return new TweezerLocksPayload(ids);
                    }
            );

    @Override
    public Type<TweezerLocksPayload> type() {
        return TYPE;
    }
}
