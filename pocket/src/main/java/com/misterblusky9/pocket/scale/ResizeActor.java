package com.misterblusky9.pocket.scale;

import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

public final class ResizeActor {
    private static final ThreadLocal<Player> ACTOR = new ThreadLocal<>();

    public static <T> T as(final Player player, final Supplier<T> resize) {
        final Player outer = ACTOR.get();
        ACTOR.set(player);
        try {
            return resize.get();
        } finally {
            ACTOR.set(outer);
        }
    }

    public static Player current() {
        return ACTOR.get();
    }

    private ResizeActor() {}
}
