package com.misterblusky9.pocket.mixin.create;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ControlledContraptionEntity.class, remap = false)
public interface ControlledContraptionEntityControllerInvoker {
    @Invoker("getController")
    IControlContraption pocket$invokeGetController();
}
