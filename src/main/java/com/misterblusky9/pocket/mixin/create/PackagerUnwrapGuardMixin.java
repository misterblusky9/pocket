package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerUnwrapGuardMixin {
    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$refuseToUnwrapPackedCraft(
            final ItemStack box,
            final boolean simulate,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (box.getItem() instanceof PocketCaseItem) {
            cir.setReturnValue(false);
        }
    }
}
