package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CardboardArmorHandler.class, remap = false)
public abstract class SkullCapCardboardStealthMixin {
    @ModifyReturnValue(method = "isCardboardArmor", at = @At("RETURN"), remap = false)
    private static boolean pocket$hattedSkullCapIsCardboard(boolean original, ItemStack stack) {
        return original || stack.is(ModItems.SKULL_CAP.get()) && BuildHostBlockEntity.hasHat(stack);
    }
}
