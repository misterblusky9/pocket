package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleFormat;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

public final class ScaleReadout {
    private static final double SETTLED = 1.0E-4D;

    private static final Map<SubLevel, Double> SUBLEVELS = new WeakHashMap<>();
    private static final Map<Entity, Double> ENTITIES = new WeakHashMap<>();

    public static String of(final SubLevel subLevel) {
        return ScaleFormat.label(value(subLevel));
    }

    public static double value(final SubLevel subLevel) {
        final boolean settled = Pym.scale().isSettled(subLevel);
        if (settled || !SUBLEVELS.containsKey(subLevel)) {
            SUBLEVELS.put(subLevel, settled ? Pym.scale().of(subLevel) : Pym.scale().settled(subLevel));
        }
        return SUBLEVELS.get(subLevel);
    }

    public static String of(final Entity entity) {
        return ScaleFormat.label(value(entity));
    }

    public static double value(final Entity entity) {
        if (entity == null) return ScaleBounds.FULL;
        final double current = Pym.entities().scaleOf(entity);
        final double target = Pym.entities().target(entity);
        final boolean settled = Math.abs(current - target) <= SETTLED;
        if (settled || !ENTITIES.containsKey(entity)) ENTITIES.put(entity, settled ? current : target);
        return ENTITIES.get(entity);
    }

    private ScaleReadout() {}
}
