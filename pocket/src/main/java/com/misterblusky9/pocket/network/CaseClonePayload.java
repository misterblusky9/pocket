package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public record CaseClonePayload(ItemStack stack, boolean pick) implements CustomPacketPayload {
    public static final Type<CaseClonePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "case_clone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseClonePayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, CaseClonePayload::stack,
            ByteBufCodecs.BOOL, CaseClonePayload::pick,
            CaseClonePayload::new);

    public static @Nullable ItemStack duplicate(ServerLevel level, ItemStack stack) {
        if (PocketCaseItem.isUnique(stack)) return PocketCaseItem.duplicate(level, stack);
        return BuildHostBlockEntity.token(stack) != null ? BuildSites.duplicate(level, stack) : null;
    }

    private static final long DISCARD_WINDOW = 200L;
    private static final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, Long>> PENDING = new java.util.HashMap<>();

    public void handle(ServerPlayer player) {
        if (!player.isCreative()) return;
        ItemStack copy = duplicate(player.serverLevel(), stack);
        if (copy == null) return;
        if (!pick) {
            java.util.UUID token = token(copy);
            long now = player.serverLevel().getGameTime();
            java.util.Map<java.util.UUID, Long> pending = PENDING.computeIfAbsent(player.getUUID(), id -> new java.util.HashMap<>());
            pending.values().removeIf(at -> now - at > DISCARD_WINDOW);
            if (token != null) pending.put(token, now);
            PacketDistributor.sendToPlayer(player, new CaseClonedPayload(copy));
            return;
        }
        player.getInventory().setPickedItem(copy);
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.inventoryMenu.broadcastChanges();
    }

    public static @org.jetbrains.annotations.Nullable java.util.UUID token(ItemStack stack) {
        return stack.getItem() instanceof PocketCaseItem ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
    }

    public static void discard(ServerPlayer player, java.util.UUID token) {
        java.util.Map<java.util.UUID, Long> pending = PENDING.get(player.getUUID());
        Long at = pending == null ? null : pending.remove(token);
        if (at == null || player.serverLevel().getGameTime() - at > DISCARD_WINDOW) return;
        if (pending.isEmpty()) PENDING.remove(player.getUUID());
        BuildSites.discardClone(player.serverLevel(), token);
    }

    @Override
    public Type<CaseClonePayload> type() {
        return TYPE;
    }
}
