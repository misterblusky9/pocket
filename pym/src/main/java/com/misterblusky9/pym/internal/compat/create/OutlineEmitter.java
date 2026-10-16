package com.misterblusky9.pym.internal.compat.create;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.Entity;

public interface OutlineEmitter {
    Entity pym$emitter();

    void pym$emitter(Entity entity);

    ClientSubLevel pym$subLevel();

    void pym$subLevel(ClientSubLevel subLevel);

    float pym$minimumPixels();

    void pym$minimumPixels(float pixels);
}
