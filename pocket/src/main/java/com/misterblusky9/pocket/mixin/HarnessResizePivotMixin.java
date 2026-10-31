package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

@Mixin(Entity.class)
public abstract class HarnessResizePivotMixin {
    @Unique private double pocket$previousHeight = Double.NaN;

    @Inject(method = "refreshDimensions", at = @At("HEAD"))
    private void pocket$rememberCenter(CallbackInfo ci) {
        pocket$previousHeight = Double.NaN;
        if (!((Object) this instanceof Player player) || !player.isAlive()
                || !player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SUBSPACE_HARNESS.get())
                || player.isPassenger() || player.isSpectator()
                || player.getAbilities().flying || player.isFallFlying()
                || player.onGround() && player.getDeltaMovement().y <= 0.0D) return;

        ScaleData base = ScaleTypes.BASE.getScaleData(player);
        if (base.getBaseScale() == base.getPrevBaseScale()) return;
        pocket$previousHeight = player.getBoundingBox().getYsize();
    }

    @Inject(method = "refreshDimensions", at = @At("TAIL"))
    private void pocket$restoreCenter(CallbackInfo ci) {
        double before = pocket$previousHeight;
        pocket$previousHeight = Double.NaN;
        if (!Double.isFinite(before)) return;

        Entity entity = (Entity) (Object) this;
        AABB box = entity.getBoundingBox();
        double shift = (before - box.getYsize()) * 0.5D;
        if (!Double.isFinite(shift) || Math.abs(shift) < 1.0E-7D) return;
        entity.setPos(entity.getX(), entity.getY() + shift, entity.getZ());
    }
}
