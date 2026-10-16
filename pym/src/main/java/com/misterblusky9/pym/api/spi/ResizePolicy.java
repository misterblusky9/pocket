package com.misterblusky9.pym.api.spi;

import com.misterblusky9.pym.api.ScaleDriver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ResizePolicy {
    @Nullable
    String refuse(ServerSubLevel subLevel, double from, double to);

    @Nullable
    default String refuse(ServerSubLevel subLevel, double from, double to, @Nullable ScaleDriver driver) {
        return refuse(subLevel, from, to);
    }
}
