package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CardboardArmorHandler.class, remap = false)
public abstract class CardboardBoxEyeHeightMixin {
    @ModifyArg(
        method = "playerHitboxChangesWhenHidingAsBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityDimensions;withEyeHeight(F)Lnet/minecraft/world/entity/EntityDimensions;"),
        remap = false
    )
    private static float pocket$scaleBoxEyeHeight(float eyeHeight, @Local(argsOnly = true) EntityEvent.Size event) {
        return pocket$scaled(eyeHeight, event);
    }

    @ModifyArg(
        method = "playerHitboxChangesWhenHidingAsBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityDimensions;fixed(FF)Lnet/minecraft/world/entity/EntityDimensions;"),
        index = 0,
        remap = false
    )
    private static float pocket$scaleBoxWidth(float width, @Local(argsOnly = true) EntityEvent.Size event) {
        return pocket$scaled(width, event);
    }

    @ModifyArg(
        method = "playerHitboxChangesWhenHidingAsBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityDimensions;fixed(FF)Lnet/minecraft/world/entity/EntityDimensions;"),
        index = 1,
        remap = false
    )
    private static float pocket$scaleBoxHeight(float height, @Local(argsOnly = true) EntityEvent.Size event) {
        return pocket$scaled(height, event);
    }

    private static float pocket$scaled(float value, EntityEvent.Size event) {
        Entity entity = event.getEntity();
        double scale = Pym.entities().scaleOf(entity);
        return Double.isFinite(scale) && scale > 0.0D ? (float) (value * scale) : value;
    }
}
