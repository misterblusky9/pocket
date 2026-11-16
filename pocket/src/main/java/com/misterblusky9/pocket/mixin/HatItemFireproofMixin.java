package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class HatItemFireproofMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void pocket$fireproofHat(CallbackInfoReturnable<Boolean> cir) {
        if (BuildHostBlockEntity.hasHat(getItem())) cir.setReturnValue(true);
    }
}
