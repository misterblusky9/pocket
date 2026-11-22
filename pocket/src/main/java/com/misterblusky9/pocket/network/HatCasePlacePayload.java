package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.compat.create.HatSchematicPlacement;
import com.misterblusky9.pocket.item.PocketCaseItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record HatCasePlacePayload(BlockPos stand, BlockPos anchor, int rotation, int mirror) implements CustomPacketPayload {
    public static final Type<HatCasePlacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hat_case_place")
    );

    public static final StreamCodec<ByteBuf, HatCasePlacePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HatCasePlacePayload::stand,
            BlockPos.STREAM_CODEC, HatCasePlacePayload::anchor,
            ByteBufCodecs.VAR_INT, HatCasePlacePayload::rotation,
            ByteBufCodecs.VAR_INT, HatCasePlacePayload::mirror,
            HatCasePlacePayload::new);

    public void handle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Rotation[] rotations = Rotation.values();
        Mirror[] mirrors = Mirror.values();
        if (rotation < 0 || rotation >= rotations.length || mirror < 0 || mirror >= mirrors.length || !level.isLoaded(stand)
                || !player.canInteractWithBlock(stand, 4.0D)) return;
        if (!(level.getBlockEntity(stand) instanceof BuildHostBlockEntity host)) return;
        if (HatSchematicPlacement.holds(player.getMainHandItem()))
            HatSchematicPlacement.place(level, player, host, anchor, rotations[rotation], mirrors[mirror]);
        else PocketCaseItem.placeOnHat(level, player, host, anchor, rotations[rotation], mirrors[mirror]);
    }

    @Override
    public Type<HatCasePlacePayload> type() {
        return TYPE;
    }
}
