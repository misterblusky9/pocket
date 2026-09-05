package com.misterblusky9.pocket.mixin.create;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ControlledContraptionEntity.class, remap = false)
public interface ControlledContraptionEntityControllerPosAccessor {
    @Accessor("controllerPos")
    BlockPos pocket$getControllerPos();
}
