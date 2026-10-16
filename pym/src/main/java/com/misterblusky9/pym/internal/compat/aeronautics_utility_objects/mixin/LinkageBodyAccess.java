package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RigidBodyHandle.class, remap = false)
public interface LinkageBodyAccess {
    @Accessor("body")
    PhysicsPipelineBody pym$linkageBody();
}
