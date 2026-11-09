package com.misterblusky9.pym.api;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;

public final class PhysicsBodies {
    public static boolean available(@Nullable PhysicsPipelineBody body) {
        return !(body instanceof ServerSubLevel subLevel) || !Detached.isOpen(subLevel);
    }

    private PhysicsBodies() {}
}
