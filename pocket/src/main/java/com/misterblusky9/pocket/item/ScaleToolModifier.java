package com.misterblusky9.pocket.item;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleToolModifier {
    private static final Set<UUID> TARGET_ONLY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FOLLOW = ConcurrentHashMap.newKeySet();

    public static boolean targetOnly(Player player) {
        return player != null && TARGET_ONLY.contains(player.getUUID());
    }

    public static boolean propagate(Player player) {
        return !targetOnly(player);
    }

    public static boolean follows(Player player) {
        return player != null && FOLLOW.contains(player.getUUID());
    }

    public static void set(Player player, boolean targetOnly, boolean follow) {
        if (player == null) return;
        mark(TARGET_ONLY, player, targetOnly);
        mark(FOLLOW, player, follow);
    }

    private static void mark(Set<UUID> set, Player player, boolean on) {
        if (on) {
            set.add(player.getUUID());
        } else {
            set.remove(player.getUUID());
        }
    }

    public static boolean holdsScaleTool(Player player) {
        return player != null && (isScaleTool(player.getMainHandItem().getItem())
                || isScaleTool(player.getOffhandItem().getItem()));
    }

    private static boolean isScaleTool(net.minecraft.world.item.Item item) {
        return item instanceof CompressionGunItem || item instanceof CreativeShrinkRayItem;
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TARGET_ONLY.remove(event.getEntity().getUUID());
        FOLLOW.remove(event.getEntity().getUUID());
    }

    private ScaleToolModifier() {}
}
