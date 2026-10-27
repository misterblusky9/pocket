package com.misterblusky9.pym.internal.compat.pehkui;

import net.minecraft.world.entity.Entity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.util.ScaleUtils;

final class PehkuiEntityScalingBackend implements PehkuiEntityScaling.Backend {
    @Override
    public double scale(Entity entity, boolean target) {
        ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        float value = target ? base.getTargetScale() : base.getBaseScale();
        return valid(value) ? value : 1.0D;
    }

    @Override
    public double renderScale(Entity entity, float partialTick) {
        float value = ScaleTypes.BASE.getScaleData(entity).getScale(partialTick);
        return valid(value) ? value : 1.0D;
    }

    @Override
    public double blockReachScale(Entity entity) {
        float value = ScaleTypes.BLOCK_REACH.getScaleData(entity).getScale(1.0F);
        return valid(value) ? value : 1.0D;
    }

    @Override
    public double motionScale(Entity entity) {
        float value = ScaleUtils.getMotionScale(entity);
        return valid(value) ? value : 1.0D;
    }

    @Override
    public void setScale(Entity entity, double requested, int ticks) {
        ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        float scale = representable(requested);
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
    public boolean changing(Entity entity) {
        ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        return base.getBaseScale() != base.getTargetScale() || base.getPrevBaseScale() != base.getBaseScale();
    }

    @Override
    public void settle(Entity entity) {
        ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        if (base.getBaseScale() == base.getTargetScale() && base.getPrevBaseScale() != base.getBaseScale()) {
            base.tick();
        }
    }

    @Override
    public void disable() {
    }

    private static float representable(double scale) {
        if (scale >= Float.MAX_VALUE) return Float.MAX_VALUE;
        if (scale <= Float.MIN_VALUE) return Float.MIN_VALUE;
        return (float) scale;
    }

    private static boolean valid(float scale) {
        return Float.isFinite(scale) && scale > 0.0F;
    }
}
