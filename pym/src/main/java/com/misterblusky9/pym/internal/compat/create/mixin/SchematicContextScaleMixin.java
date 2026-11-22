package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.SchematicScales;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelSchematicSerializationContext.class, remap = false)
public abstract class SchematicContextScaleMixin {
    @Inject(method = "setCurrentContext", at = @At("HEAD"), remap = false, require = 1)
    private static void pym$followContext(SubLevelSchematicSerializationContext context, CallbackInfo ci) {
        SchematicScales.contextChanging(context);
    }
}
