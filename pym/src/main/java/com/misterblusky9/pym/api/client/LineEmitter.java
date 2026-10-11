package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.world.entity.Entity;

public final class LineEmitter {
    public static <P extends Outline.OutlineParams> P firedBy(final P params, final Entity entity) {
        if (params instanceof final OutlineEmitter emitter) emitter.pym$emitter(entity);
        return params;
    }

    private LineEmitter() {}
}
