package com.misterblusky9.pocket.scale;

import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

public final class ResizeActor {
    private static final ThreadLocal<Player> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SURVIVAL = ThreadLocal.withInitial(() -> false);

    public static <T> T as(Player player, Supplier<T> resize) {
        Player outer = ACTOR.get();
        ACTOR.set(player);
        try {
            return resize.get();
        } finally {
            ACTOR.set(outer);
        }
    }

    public static <T> T survival(Supplier<T> resize) {
        boolean outer = SURVIVAL.get();
        SURVIVAL.set(true);
        try {
            return resize.get();
        } finally {
            SURVIVAL.set(outer);
        }
    }

    public static boolean isSurvival() {
        return SURVIVAL.get();
    }

    public static Player current() {
        return ACTOR.get();
    }

    private ResizeActor() {}
}
