package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.scale.PlotScan;
import com.misterblusky9.pym.internal.scale.Resizer;
import com.misterblusky9.pym.internal.scale.ScaleDrivers;
import com.misterblusky9.pym.internal.scale.ScaleLifecycle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class Resizing {
    static final Resizing INSTANCE = new Resizing();

    private Resizing() {}

    public ResizeRequest request(ServerSubLevel subLevel) {
        return new ResizeRequest(subLevel);
    }

    public boolean permits(
            ServerSubLevel subLevel,
            double scale,
            ScaleBounds bounds,
            boolean propagate
    ) {
        return this.check(subLevel, scale, bounds, propagate).accepted();
    }

    public ResizeResult check(
            ServerSubLevel subLevel,
            double scale,
            ScaleBounds bounds,
            boolean propagate
    ) {
        return this.request(subLevel).scaleTo(scale).bounds(bounds).propagate(propagate).check();
    }

    public ScaleBounds supported() {
        return ScaleBounds.SAFE;
    }

    @Deprecated(forRemoval = false)
    public boolean unsupportedAllowed() {
        return true;
    }

    public ScaleBounds reachable() {
        return ScaleBounds.ANY;
    }

    public PlotContents contents(ServerSubLevel subLevel) {
        return PlotScan.of(subLevel);
    }

    public boolean isNoShrink(BlockState state) {
        return PlotScan.isNoShrink(state);
    }

    public int shrunkBlockLimit() {
        return PlotScan.shrunkBlockLimit();
    }

    public void drive(ServerSubLevel subLevel, ScaleDriver driver) {
        ScaleDrivers.drive(subLevel, driver);
    }

    public void drive(ServerSubLevel subLevel, ScaleDriver driver, long validUntilTick) {
        ScaleDrivers.drive(subLevel, driver, validUntilTick);
    }

    public void release(UUID subLevelId) {
        ScaleDrivers.release(subLevelId);
    }

    public boolean suspendDrivers(ServerSubLevel subLevel, long gameTime) {
        return ScaleDrivers.suspend(subLevel, gameTime);
    }

    public void sustainSuspension(UUID subLevelId, long gameTime) {
        ScaleDrivers.sustain(subLevelId, gameTime);
    }

    public boolean driversSuspended(UUID subLevelId, long gameTime) {
        return ScaleDrivers.isSuspended(subLevelId, gameTime);
    }

    public double defaultTransitionTicks() {
        return Resizer.DEFAULT_TICKS;
    }

    public void discard(UUID subLevelId) {
        ScaleLifecycle.discard(subLevelId);
    }
}
