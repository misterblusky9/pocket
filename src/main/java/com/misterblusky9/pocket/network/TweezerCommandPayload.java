package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record TweezerCommandPayload(
        Command command, @Nullable UUID subLevelId
) implements CustomPacketPayload {
    public enum Command {
        STOP,

        LOCK
    }

    public static TweezerCommandPayload stop() {
        return new TweezerCommandPayload(Command.STOP, null);
    }

    public static TweezerCommandPayload lock(final UUID subLevelId) {
        return new TweezerCommandPayload(Command.LOCK, subLevelId);
    }

    public static final Type<TweezerCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "tweezer_command")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TweezerCommandPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeEnum(p.command());
                        buf.writeBoolean(p.subLevelId() != null);
                        if (p.subLevelId() != null) buf.writeUUID(p.subLevelId());
                    },
                    buf -> {
                        final Command command = buf.readEnum(Command.class);
                        return new TweezerCommandPayload(
                                command, buf.readBoolean() ? buf.readUUID() : null);
                    }
            );

    @Override
    public Type<TweezerCommandPayload> type() {
        return TYPE;
    }
}
