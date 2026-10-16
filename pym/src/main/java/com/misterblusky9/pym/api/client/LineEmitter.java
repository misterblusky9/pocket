package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.world.entity.Entity;

public final class LineEmitter {
    public static <P extends Outline.OutlineParams> P firedBy(P params, Entity entity) {
        if (params instanceof OutlineEmitter emitter) emitter.pym$emitter(entity);
        return params;
    }

    public static <P extends Outline.OutlineParams> P onSubLevel(
            P params, ClientSubLevel subLevel
    ) {
        if (params instanceof OutlineEmitter emitter) emitter.pym$subLevel(subLevel);
        return params;
    }

    public static <P extends Outline.OutlineParams> P weldLine(
            P params, ClientSubLevel owner
    ) {
        if (params instanceof OutlineEmitter outline) {
            outline.pym$subLevel(owner);
            outline.pym$minimumPixels(1.75F);
        }
        return params;
    }

    private LineEmitter() {}
}
