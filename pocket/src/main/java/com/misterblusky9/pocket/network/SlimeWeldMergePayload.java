package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.SlimeWeldMerge;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record SlimeWeldMergePayload(UUID weldId, InteractionHand hand,
                                    BlockPos hitPos, double x, double y, double z)
        implements CustomPacketPayload {
    public static final Type<SlimeWeldMergePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "slime_weld_merge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlimeWeldMergePayload> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeUUID(packet.weldId());
                buf.writeBoolean(packet.hand() == InteractionHand.OFF_HAND);
                BlockPos.STREAM_CODEC.encode(buf, packet.hitPos());
                buf.writeDouble(packet.x());
                buf.writeDouble(packet.y());
                buf.writeDouble(packet.z());
            }, buf -> new SlimeWeldMergePayload(buf.readUUID(),
                    buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    BlockPos.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<SlimeWeldMergePayload> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        if (player == null || hitPos == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || x < -0.01D || y < -0.01D || z < -0.01D
                || x > 1.01D || y > 1.01D || z > 1.01D) return;
        WeldRecord record = WeldStore.get(player.serverLevel()).get(weldId);
        if (record == null) return;
        Vec3 point = new Vec3(hitPos.getX() + x, hitPos.getY() + y, hitPos.getZ() + z);
        if (!SlimeWeldMerge.aimedAt(player, record, hitPos, point)) return;
        SlimeWeldMerge.convert(player, record, hand);
    }
}
