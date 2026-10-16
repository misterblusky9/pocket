package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;

import java.util.UUID;

@FunctionalInterface
public interface JointConductor {
    boolean isConductor(Context joint);

    record Context(
            ServerSubLevelContainer container,
            UUID first,
            UUID second,
            PhysicsConstraintHandle handle,
            PhysicsConstraintConfiguration<?> configuration
    ) {}
}
