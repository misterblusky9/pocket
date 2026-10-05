package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.item.ModCreativeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTab.class)
public abstract class CreativeTabContainsScaleToolMixin {
    @Inject(method = "contains", at = @At("RETURN"), cancellable = true, require = 1)
    private void pocket$matchIgnoringComponents(
            final ItemStack stack,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ() || stack == null || stack.isEmpty()) return;
        if (!ModCreativeTabs.MAIN.isBound() || ModCreativeTabs.MAIN.get() != (Object) this) return;

        for (final ItemStack entry : ((CreativeModeTab) (Object) this).getSearchTabDisplayItems()) {
            if (entry.getItem() == stack.getItem()) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
