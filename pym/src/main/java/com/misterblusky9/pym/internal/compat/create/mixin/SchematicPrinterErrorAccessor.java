package com.misterblusky9.pym.internal.compat.create.mixin;

import com.simibubi.create.content.schematics.SchematicPrinter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SchematicPrinter.class, remap = false)
public interface SchematicPrinterErrorAccessor {
    @Accessor(value = "isErrored", remap = false)
    void pym$setErrored(boolean errored);
}
