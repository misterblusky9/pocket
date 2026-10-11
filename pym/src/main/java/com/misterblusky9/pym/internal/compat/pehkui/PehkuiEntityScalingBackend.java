package com.misterblusky9.pym.internal.compat.pehkui;

import net.minecraft.world.entity.Entity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

final class PehkuiEntityScalingBackend implements PehkuiEntityScaling.Backend {
    @Override
    public double scale(final Entity entity, final boolean target) {
        final ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        final float value = target ? base.getTargetScale() : base.getBaseScale();
        return valid(value) ? value : 1.0D;
    }

    @Override
    public double renderScale(final Entity entity, final float partialTick) {
        final float value = ScaleTypes.BASE.getScaleData(entity).getScale(partialTick);
        return valid(value) ? value : 1.0D;
    }

    @Override
    public void setScale(final Entity entity, final double requested, final int ticks) {
        final ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        final float scale = representable(requested);
        if (ticks <= 0) {
            base.setScale(scale);
            base.setTargetScale(scale);
        } else {
            base.setScaleTickDelay(ticks);
            base.setTargetScale(base.getBaseScale());
            base.setTargetScale(scale);
        }
    }

    @Override
    public boolean changing(final Entity entity) {
        final ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        return base.getBaseScale() != base.getTargetScale() || base.getPrevBaseScale() != base.getBaseScale();
    }

    @Override
    public void settle(final Entity entity) {
        final ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        if (base.getBaseScale() == base.getTargetScale() && base.getPrevBaseScale() != base.getBaseScale()) {
            base.tick();
        }
    }

    @Override
    public void disable() {
    }

    private static float representable(final double scale) {
        if (scale >= Float.MAX_VALUE) return Float.MAX_VALUE;
        if (scale <= Float.MIN_VALUE) return Float.MIN_VALUE;
        return (float) scale;
    }

    private static boolean valid(final float scale) {
        return Float.isFinite(scale) && scale > 0.0F;
    }
}
