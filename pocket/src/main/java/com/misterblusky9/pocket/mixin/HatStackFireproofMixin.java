package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class HatStackFireproofMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true)
    private void pocket$fireproofHat(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.IS_FIRE) && BuildHostBlockEntity.hasHat((ItemStack) (Object) this))
            cir.setReturnValue(false);
    }
}
