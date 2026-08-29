package com.misterblusky9.pocket.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

// Holds the client moon shim. Plot coordinates come from the server so both sides
// agree; an anchor the client picks has to fall inside the server shim's plot.
public final class MoonClientSubLevels {
    private static volatile MoonClientSubLevel shim;
    private static volatile Level shimLevel;
    private static volatile int plotX;
    private static volatile int plotZ;

    public static synchronized void sync(final int newPlotX, final int newPlotZ) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }

        if (shim != null && shimLevel == level && plotX == newPlotX && plotZ == newPlotZ) return;

        plotX = newPlotX;
        plotZ = newPlotZ;
        shimLevel = level;
        shim = new MoonClientSubLevel(level, newPlotX, newPlotZ, new Pose3d());
    }

    public static MoonClientSubLevel get() {
        final MoonClientSubLevel current = shim;
        if (current == null) return null;
        if (Minecraft.getInstance().level != shimLevel || !MoonPhysicsClient.isActive()) return null;
        return current;
    }

    public static synchronized void clear() {
        if (shim != null) shim.markRemoved();
        shim = null;
        shimLevel = null;
    }

    private MoonClientSubLevels() {}
}
