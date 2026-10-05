package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public record ShrinkRayBeamColourPayload(int colour, Vec3 target) implements CustomPacketPayload {
    public static final int SHRINK_COLOUR = 0x9AF0FF;
    public static final int GROW_COLOUR = 0xFFD24A;
    public static final int INERT_COLOUR = 0xFFFFFF;

    public static final Type<ShrinkRayBeamColourPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "shrink_ray_beam_colour")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShrinkRayBeamColourPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeInt(packet.colour());
                        buf.writeDouble(packet.target().x);
                        buf.writeDouble(packet.target().y);
                        buf.writeDouble(packet.target().z);
                    },
                    buf -> new ShrinkRayBeamColourPayload(
                            buf.readInt(),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
            );

    @Override
    public Type<ShrinkRayBeamColourPayload> type() { return TYPE; }

    public static void send(final ServerPlayer player, final Vec3 target, final int colour) {
        if (player == null || target == null) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, new ShrinkRayBeamColourPayload(colour, target));
    }
}
