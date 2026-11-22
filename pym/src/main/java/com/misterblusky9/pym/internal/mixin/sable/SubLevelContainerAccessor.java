package com.misterblusky9.pym.internal.mixin.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubLevelContainer.class, remap = false)
public interface SubLevelContainerAccessor {
    @Accessor("subLevels")
    SubLevel[] pym$getSubLevels();
}
