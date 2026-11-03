package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.CarriedBuilds;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record BuildMirrorRequestPayload(UUID token) implements CustomPacketPayload {
    public static final Type<BuildMirrorRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "build_mirror_request")
    );

    public static final StreamCodec<ByteBuf, BuildMirrorRequestPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(BuildMirrorRequestPayload::new, BuildMirrorRequestPayload::token);

    public void handle(ServerPlayer player) {
        BuildMirrorPayload live = BuildSites.mirror(token, player);
        if (live != null) {
            PacketDistributor.sendToPlayer(player, live);
            return;
        }
        CompoundTag plot = CarriedBuilds.livePlot(token, player);
        if (plot == null) {
            ServerLevel stored = BuildSites.storedIn(player.serverLevel(), token);
            CompoundTag saved = stored == null ? null : PocketedSubLevelSavedData.getOrLoad(stored).getCopy(token);
            plot = saved == null ? new CompoundTag() : saved.getCompound("plot");
        }
        PacketDistributor.sendToPlayer(player, new BuildMirrorPayload(token, plot, new CompoundTag()));
    }

    @Override
    public Type<BuildMirrorRequestPayload> type() {
        return TYPE;
    }
}
