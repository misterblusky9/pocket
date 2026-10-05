package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScaling;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import net.minecraft.world.entity.Entity;

public final class EntityScaling {
    static final EntityScaling INSTANCE = new EntityScaling();

    private EntityScaling() {}

    public enum Backend {
        PEHKUI,
        NATIVE_STATIC,
        UNAVAILABLE
    }

    public Backend backend(final Entity entity) {
        return switch (EntityScaleTracker.backend(entity)) {
            case PEHKUI -> Backend.PEHKUI;
            case NATIVE_STATIC -> Backend.NATIVE_STATIC;
            case UNAVAILABLE -> Backend.UNAVAILABLE;
        };
    }

    public boolean supports(final Entity entity) {
        return EntityScaleTracker.supports(entity);
    }

    public boolean pehkuiPresent() {
        return PehkuiEntityScaling.present();
    }

    public boolean pehkuiAvailable() {
        return PehkuiEntityScaling.active();
    }

    public double scaleOf(final Entity entity) {
        return EntityScaleTracker.factor(entity);
    }

    public double target(final Entity entity) {
        return EntityScaleTracker.target(entity);
    }

    public double base(final Entity entity) {
        return scaleOf(entity);
    }

    public double baseTarget(final Entity entity) {
        return target(entity);
    }

    public boolean set(final Entity entity, final double scale, final int ticks) {
        return EntityScaleTracker.set(entity, scale, Math.max(0, ticks));
    }

    public boolean multiply(final Entity entity, final double ratio, final int ticks) {
        return EntityScaleTracker.multiply(entity, ratio, Math.max(0, ticks));
    }

    public double renderScale(final Entity entity, final float partialTick) {
        return EntityScaleTracker.renderScale(entity, partialTick);
    }

    public boolean available() {
        return pehkuiAvailable();
    }

    public boolean setScale(final Entity entity, final double scale, final int ticks) {
        return set(entity, scale, ticks);
    }

    @Deprecated(forRemoval = false)
    public boolean personalAvailable() {
        return pehkuiAvailable();
    }

    @Deprecated(forRemoval = false)
    public double personal(final Entity entity) {
        return scaleOf(entity);
    }

    @Deprecated(forRemoval = false)
    public double personalTarget(final Entity entity) {
        return target(entity);
    }

    @Deprecated(forRemoval = false)
    public boolean setPersonal(final Entity entity, final double scale, final int ticks) {
        return set(entity, scale, ticks);
    }
}
