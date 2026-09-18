package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleFormat;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

public final class ScaleReadout {
    private static final double MOON_STAGE_TOLERANCE = 1.0E-4D;

    private static final double ENTITY_SETTLE_TOLERANCE = 1.0E-4D;

    private static final Map<SubLevel, Double> SHOWN = new WeakHashMap<>();
    private static final Map<LivingEntity, Double> ENTITY_SHOWN = new WeakHashMap<>();
    private static double moonShown = Double.NaN;

    public static String of(final SubLevel subLevel) {
        if (ScaleState.isSettled(subLevel) || !SHOWN.containsKey(subLevel)) {
            SHOWN.put(subLevel, ScaleState.isSettled(subLevel)
                    ? ScaleState.getScale(subLevel)
                    : ScaleState.getSettledScale(subLevel));
        }
        return ScaleFormat.label(SHOWN.get(subLevel));
    }

    public static String of(final LivingEntity entity) {
        return ScaleFormat.label(value(entity));
    }

    public static double value(final LivingEntity entity) {
        final double current = PehkuiScaleBridge.personalScale(entity);
        final double target = PehkuiScaleBridge.personalTargetScale(entity);
        final boolean settled = !Double.isFinite(target)
                || Math.abs(current - target) <= ENTITY_SETTLE_TOLERANCE;

        if (settled || !ENTITY_SHOWN.containsKey(entity)) {
            ENTITY_SHOWN.put(entity, settled ? current : target);
        }
        return ENTITY_SHOWN.get(entity);
    }

    public static String moon() {
        final double scale = MoonScaleClient.get();
        final double stage = CompressionStage.nearest(scale).scale();
        if (Math.abs(scale - stage) <= MOON_STAGE_TOLERANCE || Double.isNaN(moonShown)) moonShown = stage;
        return ScaleFormat.label(moonShown);
    }

    private ScaleReadout() {}
}
