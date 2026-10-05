package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.client.MoonScaleClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "pocket", bus = EventBusSubscriber.Bus.MOD)
public final class MoonScaleNetwork {
    public static final byte EFFECT_BEGIN = 0;
    public static final byte EFFECT_RELEASE = 1;
    public static final byte EFFECT_PULSE = 2;

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("2");
        registrar.playToClient(
                MoonScalePayload.TYPE,
                MoonScalePayload.STREAM_CODEC,
                MoonScaleClient::handle);
        registrar.playToClient(
                MoonPresencePayload.TYPE,
                MoonPresencePayload.STREAM_CODEC,
                MoonScaleClient::handlePresence);
        registrar.playToClient(
                MoonEffectPayload.TYPE,
                MoonEffectPayload.STREAM_CODEC,
                MoonScaleClient::handleEffect);
        registrar.playToServer(
                MoonScaleRequestPayload.TYPE,
                MoonScaleRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof final ServerPlayer player) {
                        final var server = player.serverLevel().getServer();
                        send(player, MoonScale.get(server));
                        sendPresence(player, MoonScale.isPresent(server));
                    }
                }));
    }

    public static void send(final ServerPlayer player, final float scale) {
        PacketDistributor.sendToPlayer(player, new MoonScalePayload(scale));
    }

    public static void broadcast(final float scale) {
        PacketDistributor.sendToAllPlayers(new MoonScalePayload(scale));
    }

    public static void sendPresence(final ServerPlayer player, final boolean present) {
        PacketDistributor.sendToPlayer(player, new MoonPresencePayload(present));
    }

    public static void broadcastPresence(final boolean present) {
        PacketDistributor.sendToAllPlayers(new MoonPresencePayload(present));
    }

    public static void broadcastEffectBegin(
            final boolean growing,
            final boolean acquired,
            final int acquireTicks,
            final float surfaceX,
            final float surfaceZ
    ) {
        PacketDistributor.sendToAllPlayers(new MoonEffectPayload(
                EFFECT_BEGIN,
                growing,
                acquired,
                Math.max(1, acquireTicks),
                surfaceX,
                surfaceZ
        ));
    }

    public static void broadcastEffectRelease() {
        PacketDistributor.sendToAllPlayers(new MoonEffectPayload(
                EFFECT_RELEASE,
                false,
                false,
                1,
                0.0F,
                0.0F
        ));
    }

    public static void broadcastEffectPulse() {
        PacketDistributor.sendToAllPlayers(new MoonEffectPayload(
                EFFECT_PULSE,
                false,
                false,
                1,
                0.0F,
                0.0F
        ));
    }

    public record MoonScalePayload(float scale) implements CustomPacketPayload {
        public static final Type<MoonScalePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("pocket", "moon_scale"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoonScalePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeFloat(payload.scale),
                        buffer -> new MoonScalePayload(buffer.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    public record MoonPresencePayload(boolean present) implements CustomPacketPayload {
        public static final Type<MoonPresencePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("pocket", "moon_presence"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoonPresencePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBoolean(payload.present),
                        buffer -> new MoonPresencePayload(buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MoonEffectPayload(
            byte action,
            boolean growing,
            boolean acquired,
            int acquireTicks,
            float surfaceX,
            float surfaceZ
    ) implements CustomPacketPayload {
        public static final Type<MoonEffectPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("pocket", "moon_effect"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoonEffectPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeByte(payload.action);
                            buffer.writeBoolean(payload.growing);
                            buffer.writeBoolean(payload.acquired);
                            buffer.writeVarInt(payload.acquireTicks);
                            buffer.writeFloat(payload.surfaceX);
                            buffer.writeFloat(payload.surfaceZ);
                        },
                        buffer -> new MoonEffectPayload(
                                buffer.readByte(),
                                buffer.readBoolean(),
                                buffer.readBoolean(),
                                buffer.readVarInt(),
                                buffer.readFloat(),
                                buffer.readFloat()
                        ));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MoonScaleRequestPayload() implements CustomPacketPayload {
        public static final MoonScaleRequestPayload INSTANCE = new MoonScaleRequestPayload();
        public static final Type<MoonScaleRequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("pocket", "moon_scale_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoonScaleRequestPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {},
                        buffer -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MoonScaleNetwork() {}
}
