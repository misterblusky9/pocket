package com.misterblusky9.pocket.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record CaseSeal(PocketSeal glue, UUID owner, String ownerName) {
    public static final Codec<CaseSeal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PocketSeal.CODEC.fieldOf("glue").forGetter(CaseSeal::glue),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(CaseSeal::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(CaseSeal::ownerName)
    ).apply(instance, CaseSeal::new));

    public static final StreamCodec<FriendlyByteBuf, CaseSeal> STREAM_CODEC = StreamCodec.composite(
            PocketSeal.STREAM_CODEC, CaseSeal::glue,
            UUIDUtil.STREAM_CODEC, CaseSeal::owner,
            ByteBufCodecs.STRING_UTF8, CaseSeal::ownerName,
            CaseSeal::new);

    public static CaseSeal by(final Player player, final PocketSeal glue) {
        return new CaseSeal(glue, player.getUUID(), player.getGameProfile().getName());
    }

    public boolean canCut(final Player player) {
        return player.isCreative() || this.owner.equals(player.getUUID());
    }
}
