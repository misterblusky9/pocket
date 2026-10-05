package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.network.PersonalLockPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PersonalLock {
    private static final String KEY = "PocketPersonalUnlocked";

    private static boolean clientUnlocked;

    private PersonalLock() {}

    public static boolean unlocked(final Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) return clientUnlocked;
        return player.getPersistentData().getBoolean(KEY);
    }

    public static void set(final ServerPlayer player, final boolean unlocked) {
        if (player == null) return;
        player.getPersistentData().putBoolean(KEY, unlocked);
        sync(player);
    }

    public static void sync(final ServerPlayer player) {
        if (player != null) PacketDistributor.sendToPlayer(player, new PersonalLockPayload(unlocked(player)));
    }

    public static void copy(final ServerPlayer from, final ServerPlayer to) {
        if (from != null && to != null) to.getPersistentData().putBoolean(KEY, unlocked(from));
    }

    public static void setClient(final boolean unlocked) {
        clientUnlocked = unlocked;
    }

    public static boolean protects(final Player player) {
        if (unlocked(player)) return false;
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            final ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof SelfResizeDeviceItem) return true;
        }
        return false;
    }
}
