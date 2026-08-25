package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TweezerGripsPayload(List<Grip> grips) implements CustomPacketPayload {
    public record Grip(UUID playerId, double anchorX, double anchorY, double anchorZ) {}

    public static final Type<TweezerGripsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "tweezer_grips")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TweezerGripsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.grips().size());
                        for (final Grip grip : p.grips()) {
                            buf.writeUUID(grip.playerId());
                            buf.writeDouble(grip.anchorX());
                            buf.writeDouble(grip.anchorY());
                            buf.writeDouble(grip.anchorZ());
                        }
                    },
                    buf -> {
                        final int count = buf.readVarInt();
                        final List<Grip> grips = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            grips.add(new Grip(
                                    buf.readUUID(),
                                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
                        }
                        return new TweezerGripsPayload(grips);
                    }
            );

    @Override
    public Type<TweezerGripsPayload> type() {
        return TYPE;
    }
}
