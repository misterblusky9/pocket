package com.misterblusky9.pocketdebug.mixin;

import com.simibubi.create.content.schematics.client.SchematicHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SchematicHandler.class, remap = false)
public interface SchematicHandlerAccessor {
    @Accessor(value = "active", remap = false)
    void pocketdebug$setActive(boolean active);
}
