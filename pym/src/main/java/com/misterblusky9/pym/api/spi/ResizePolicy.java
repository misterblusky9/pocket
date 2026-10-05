package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ResizePolicy {
    @Nullable
    String refuse(ServerSubLevel subLevel, double from, double to);
}
