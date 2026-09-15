package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = AllFanProcessingTypes.HauntingType.class, remap = false)
public abstract class HauntedCompressionGunMixin {
    @Inject(method = "process", at = @At("RETURN"))
    private void pocket$keepGunData(
            final ItemStack stack,
            final Level level,
            final CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        if (!(stack.getItem() instanceof CompressionGunItem)) return;
        final List<ItemStack> results = cir.getReturnValue();
        if (results == null) return;

        for (final ItemStack result : results) {
            if (result.getItem() instanceof CompressionGunItem) result.applyComponents(stack.getComponentsPatch());
        }
    }
}
