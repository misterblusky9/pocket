package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface ResizeFollower {
    Collection<? extends LivingEntity> followers(ServerSubLevel subLevel);

    ResizeFollower NONE = subLevel -> List.of();
}
