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

    public ResizeRequest request(final ServerSubLevel subLevel) {
        return new ResizeRequest(subLevel);
    }

    public boolean permits(
            final ServerSubLevel subLevel,
            final double scale,
            final ScaleBounds bounds,
            final boolean propagate
    ) {
        return this.check(subLevel, scale, bounds, propagate).accepted();
    }

    public ResizeResult check(
            final ServerSubLevel subLevel,
            final double scale,
            final ScaleBounds bounds,
            final boolean propagate
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

    public PlotContents contents(final ServerSubLevel subLevel) {
        return PlotScan.of(subLevel);
    }

    public boolean isNoShrink(final BlockState state) {
        return PlotScan.isNoShrink(state);
    }

    public int shrunkBlockLimit() {
        return PlotScan.shrunkBlockLimit();
    }

    public void drive(final ServerSubLevel subLevel, final ScaleDriver driver) {
        ScaleDrivers.drive(subLevel, driver);
    }

    public void drive(final ServerSubLevel subLevel, final ScaleDriver driver, final long validUntilTick) {
        ScaleDrivers.drive(subLevel, driver, validUntilTick);
    }

    public void release(final UUID subLevelId) {
        ScaleDrivers.release(subLevelId);
    }

    public boolean suspendDrivers(final ServerSubLevel subLevel, final long gameTime) {
        return ScaleDrivers.suspend(subLevel, gameTime);
    }

    public void sustainSuspension(final UUID subLevelId, final long gameTime) {
        ScaleDrivers.sustain(subLevelId, gameTime);
    }

    public boolean driversSuspended(final UUID subLevelId, final long gameTime) {
        return ScaleDrivers.isSuspended(subLevelId, gameTime);
    }

    public double defaultTransitionTicks() {
        return Resizer.DEFAULT_TICKS;
    }

    public void discard(final UUID subLevelId) {
        ScaleLifecycle.discard(subLevelId);
    }
}
