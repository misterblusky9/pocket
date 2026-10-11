package com.misterblusky9.pocket.mixin.create;

import com.simibubi.create.foundation.utility.worldWrappers.WrappedBlockAndTintGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WrappedBlockAndTintGetter.class, remap = false)
public interface WrappedBlockAndTintGetterAccessor {
    @Accessor("wrapped")
    BlockAndTintGetter pocket$getWrapped();
}
