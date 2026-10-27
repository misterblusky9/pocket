package com.misterblusky9.pym.internal.compat.create;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SchematicToolScale {
    public static double distance(double distance) {
        return distance * playerScale(false);
    }

    public static float lineWidth(float width) {
        return (float) (width * playerScale(true));
    }

    private static double playerScale(boolean render) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ScaleBounds.FULL;
        double scale = render
                ? Pym.entities().renderScale(player, AnimationTickHolder.getPartialTicks())
                : Pym.entities().scaleOf(player);
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    private SchematicToolScale() {}
}
