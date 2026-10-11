package com.misterblusky9.pym.api.spi;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface ResizeFollower {
    Collection<? extends Entity> followers(ServerSubLevel subLevel);

    default Collection<? extends Entity> followers(final ServerSubLevel subLevel, final ScaleDriver driver) {
        return followers(subLevel);
    }

    default ScaleBounds bounds(final Entity entity) { return ScaleBounds.ANY; }

    ResizeFollower NONE = subLevel -> List.of();
}
