package com.misterblusky9.pocket.item;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleToolModifier {
    private static final Set<UUID> TARGET_ONLY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FOLLOW = ConcurrentHashMap.newKeySet();

    public static boolean targetOnly(final Player player) {
        return player != null && TARGET_ONLY.contains(player.getUUID());
    }

    public static boolean propagate(final Player player) {
        return !targetOnly(player);
    }

    public static boolean follows(final Player player) {
        return player != null && FOLLOW.contains(player.getUUID());
    }

    public static void set(final Player player, final boolean targetOnly, final boolean follow) {
        if (player == null) return;
        mark(TARGET_ONLY, player, targetOnly);
        mark(FOLLOW, player, follow);
    }

    private static void mark(final Set<UUID> set, final Player player, final boolean on) {
        if (on) {
            set.add(player.getUUID());
        } else {
            set.remove(player.getUUID());
        }
    }

    public static boolean holdsScaleTool(final Player player) {
        return player != null && (isScaleTool(player.getMainHandItem().getItem())
                || isScaleTool(player.getOffhandItem().getItem()));
    }

    private static boolean isScaleTool(final net.minecraft.world.item.Item item) {
        return item instanceof CompressionGunItem || item instanceof CreativeShrinkRayItem;
    }

    public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        TARGET_ONLY.remove(event.getEntity().getUUID());
        FOLLOW.remove(event.getEntity().getUUID());
    }

    private ScaleToolModifier() {}
}
