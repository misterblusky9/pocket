package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public interface Participation {
    default boolean excludesSubLevel(final ServerSubLevel subLevel) { return false; }

    default boolean excludesConstraintBody(final PhysicsPipelineBody body) { return false; }

    default boolean holdsScale(final UUID subLevelId) { return false; }

    default boolean exemptsEntity(final Entity entity) { return false; }
}
