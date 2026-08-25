package com.misterblusky9.pocket.mixin.create;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PotatoCannonItem.class, remap = false)
public abstract class PotatoCannonSettingsMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$openReleaseSettings(
            final Level level,
            final Player player,
            final InteractionHand hand,
            final CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        if (!player.isShiftKeyDown()) return;

        final ItemStack cannon = player.getItemInHand(hand);

        if (level.isClientSide && FMLEnvironment.dist == Dist.CLIENT) {
            com.misterblusky9.pocket.client.CannonScreenHooks.open(cannon, hand);
        }

        cir.setReturnValue(InteractionResultHolder.success(cannon));
    }
}
