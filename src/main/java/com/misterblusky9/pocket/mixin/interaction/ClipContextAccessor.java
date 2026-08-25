package com.misterblusky9.pocket.mixin.interaction;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface ClipContextAccessor {
    @Accessor("block")
    ClipContext.Block pocket$getBlockMode();

    @Accessor("fluid")
    ClipContext.Fluid pocket$getFluidMode();

    @Accessor("collisionContext")
    CollisionContext pocket$getCollisionContext();
}
