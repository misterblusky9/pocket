package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.ResizeResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        if (player == null) return false;
        final MutableComponent message = Component.literal(reason(result));
        if (result.status() == ResizeResult.Status.OUT_OF_BOUNDS) message.withStyle(ChatFormatting.RED);
        player.displayClientMessage(message, true);
        return false;
    }

    public static String cannotShrink(final PlotContents contents) {
        return contents.hasNoShrinkBlock() ? "Cannot shrink: contains " + contents.noShrink() : null;
    }

    private ResizeFeedback() {}
}
