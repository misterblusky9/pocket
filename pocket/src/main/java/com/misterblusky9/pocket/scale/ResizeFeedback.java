package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.ResizeResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class ResizeFeedback {
    public static String reason(final ResizeResult result) {
        if (result.message() != null) return result.message();
        return switch (result.status()) {
            case UNAVAILABLE -> "That sublevel is no longer there";
            default -> "Resize refused: " + result.status();
        };
    }

    public static boolean report(final Player player, final ResizeResult result) {
        if (result.accepted()) return true;
        if (player != null) player.displayClientMessage(Component.literal(reason(result)), true);
        return false;
    }

    public static String cannotShrink(final PlotContents contents) {
        return contents.hasNoShrinkBlock() ? "Cannot shrink: contains " + contents.noShrink() : null;
    }

    private ResizeFeedback() {}
}
