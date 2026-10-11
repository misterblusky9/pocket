package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.HotGluePunchHandler;
import com.misterblusky9.pocket.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record HotGluePunchPayload(BlockPos pos, double hitX, double hitY, double hitZ) implements CustomPacketPayload {
    public static final Type<HotGluePunchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hot_glue_punch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HotGluePunchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                buf.writeDouble(packet.hitX());
                buf.writeDouble(packet.hitY());
                buf.writeDouble(packet.hitZ());
            },
            buf -> new HotGluePunchPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()));

    @Override
    public Type<HotGluePunchPayload> type() { return TYPE; }

    public void handle(final ServerPlayer player) {
        if (player == null || this.pos == null) return;
        if (!validHit(this.hitX) || !validHit(this.hitY) || !validHit(this.hitZ)) return;
        if (!player.getMainHandItem().is(ModItems.GLUE_GUN.get())) return;

        HotGluePunchHandler.aim(player, this.pos, new Vec3(
                this.pos.getX() + this.hitX, this.pos.getY() + this.hitY, this.pos.getZ() + this.hitZ));
    }

    private static boolean validHit(final double value) {
        return Double.isFinite(value) && value >= -0.01D && value <= 1.01D;
    }
}
