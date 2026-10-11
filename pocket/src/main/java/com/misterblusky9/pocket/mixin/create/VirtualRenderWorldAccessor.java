package com.misterblusky9.pocket.mixin.create;

import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VirtualRenderWorld.class, remap = false)
public interface VirtualRenderWorldAccessor {
    @Accessor("level")
    Level pocket$getLevel();

    @Accessor("biomeOffset")
    Vec3i pocket$getBiomeOffset();
}
