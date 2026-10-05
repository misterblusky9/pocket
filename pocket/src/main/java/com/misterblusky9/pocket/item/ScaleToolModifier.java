package com.misterblusky9.pocket.item;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleToolModifier {
    private static final Set<UUID> TARGET_ONLY = ConcurrentHashMap.newKeySet();

    public static boolean targetOnly(final Player player) {
        return player != null && TARGET_ONLY.contains(player.getUUID());
    }

    public static boolean propagate(final Player player) {
        return !targetOnly(player);
    }

    public static void set(final Player player, final boolean targetOnly) {
        if (player == null) return;
        if (targetOnly) {
            TARGET_ONLY.add(player.getUUID());
        } else {
            TARGET_ONLY.remove(player.getUUID());
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
    }

    private ScaleToolModifier() {}
}
