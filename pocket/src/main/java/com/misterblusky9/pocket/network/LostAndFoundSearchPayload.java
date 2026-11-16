package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.LostAndFoundMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record LostAndFoundSearchPayload(String text) implements CustomPacketPayload {
    public static final Type<LostAndFoundSearchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "lost_and_found_search"));

    public static final StreamCodec<ByteBuf, LostAndFoundSearchPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(50).map(LostAndFoundSearchPayload::new, LostAndFoundSearchPayload::text);

    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof LostAndFoundMenu menu) menu.search(text);
    }

    @Override
    public Type<LostAndFoundSearchPayload> type() {
        return TYPE;
    }
}
