package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RigidBodyHandle.class, remap = false)
public interface HandleBodyAccess {
    @Accessor("body")
    PhysicsPipelineBody pym$deepSeasBody();
}
