package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public record CrossScaleWeldListPayload(List<WeldRecord> welds) implements CustomPacketPayload {
    public static final Type<CrossScaleWeldListPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "cross_scale_weld_list")
    );

    private static final int MAX_WELDS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, CrossScaleWeldListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        final int count = Math.min(MAX_WELDS, packet.welds().size());
                        buf.writeVarInt(count);
                        for (int i = 0; i < count; i++) write(buf, packet.welds().get(i));
                    },
                    buf -> {
                        final int count = Math.min(MAX_WELDS, buf.readVarInt());
                        final List<WeldRecord> welds = new ArrayList<>(Math.max(0, count));
                        for (int i = 0; i < count; i++) welds.add(read(buf));
                        return new CrossScaleWeldListPayload(List.copyOf(welds));
                    }
            );

    private static void write(final RegistryFriendlyByteBuf buf, final WeldRecord record) {
        buf.writeUUID(record.weldId());
        buf.writeUUID(record.smallSubLevel());
        buf.writeBoolean(record.worldAnchored());
        if (!record.worldAnchored()) buf.writeUUID(record.bigSubLevel());
        BlockPos.STREAM_CODEC.encode(buf, record.smallPos());
        BlockPos.STREAM_CODEC.encode(buf, record.bigPos());
        buf.writeByte(record.smallFacing().get3DDataValue());
        buf.writeByte(record.bigFacing().get3DDataValue());
        writeVec(buf, record.smallAnchor());
        writeVec(buf, record.bigAnchor());
        buf.writeDouble(record.smallSpan());
        buf.writeDouble(record.bigSpan());
        buf.writeDouble(record.orientation().x);
        buf.writeDouble(record.orientation().y);
        buf.writeDouble(record.orientation().z);
        buf.writeDouble(record.orientation().w);
    }

    private static WeldRecord read(final RegistryFriendlyByteBuf buf) {
        final java.util.UUID weldId = buf.readUUID();
        final java.util.UUID smallSubLevel = buf.readUUID();
        final boolean worldAnchored = buf.readBoolean();
        final java.util.UUID bigSubLevel = worldAnchored ? null : buf.readUUID();
        return new WeldRecord(
                weldId,
                smallSubLevel,
                bigSubLevel,
                BlockPos.STREAM_CODEC.decode(buf),
                BlockPos.STREAM_CODEC.decode(buf),
                Direction.from3DDataValue(buf.readUnsignedByte()),
                Direction.from3DDataValue(buf.readUnsignedByte()),
                readVec(buf),
                readVec(buf),
                buf.readDouble(),
                buf.readDouble(),
                new Quaterniond(
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble()).normalize());
    }

    private static void writeVec(final RegistryFriendlyByteBuf buf, final Vector3d vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vector3d readVec(final RegistryFriendlyByteBuf buf) {
        return new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<CrossScaleWeldListPayload> type() {
        return TYPE;
    }
}
