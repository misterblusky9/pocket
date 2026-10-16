package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public interface Participation {
    default boolean excludesSubLevel(ServerSubLevel subLevel) { return false; }

    default boolean excludesConstraintBody(PhysicsPipelineBody body) { return false; }

    default boolean holdsScale(UUID subLevelId) { return false; }

    default boolean exemptsEntity(Entity entity) { return false; }
}
