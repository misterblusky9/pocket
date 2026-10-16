package com.misterblusky9.pym.internal.mixin.interaction;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface ClipContextAccessor {
    @Accessor("block")
    ClipContext.Block pym$getBlockMode();

    @Accessor("fluid")
    ClipContext.Fluid pym$getFluidMode();

    @Accessor("collisionContext")
    CollisionContext pym$getCollisionContext();
}
