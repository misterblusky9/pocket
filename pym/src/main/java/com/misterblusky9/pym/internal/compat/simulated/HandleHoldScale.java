package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScaling;
import net.minecraft.world.entity.player.Player;

public final class HandleHoldScale {
    public static double body(Player player) {
        if (player == null) return 1.0D;
        double scale = Pym.entities().scaleOf(player);
        return scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D;
    }

    public static double reach(Player player) {
        return PehkuiEntityScaling.blockReachScale(player);
    }

    public static double motion(Player player) {
        return PehkuiEntityScaling.motionScale(player);
    }

    private HandleHoldScale() {}
}
