package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.world.entity.EntityDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PackageEntity.class, remap = false)
public abstract class PackageEntityDimensionsMixin {
    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"), remap = false)
    private EntityDimensions pocket$scalableBox(final EntityDimensions original) {
        if (original == null || !original.fixed()) return original;
        return new EntityDimensions(original.width(), original.height(), original.eyeHeight(), original.attachments(), false);
    }
}
