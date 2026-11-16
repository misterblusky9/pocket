package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record CaseClonedPayload(ItemStack stack) implements CustomPacketPayload {
    public static final Type<CaseClonedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "case_cloned"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseClonedPayload> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(CaseClonedPayload::new, CaseClonedPayload::stack);

    @Override
    public Type<CaseClonedPayload> type() {
        return TYPE;
    }
}
