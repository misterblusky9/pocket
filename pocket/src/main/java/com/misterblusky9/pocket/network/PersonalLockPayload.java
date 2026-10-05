package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PersonalLockPayload(boolean unlocked) implements CustomPacketPayload {
    public static final Type<PersonalLockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "personal_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PersonalLockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBoolean(packet.unlocked()),
            buf -> new PersonalLockPayload(buf.readBoolean()));

    @Override
    public Type<PersonalLockPayload> type() { return TYPE; }
}
