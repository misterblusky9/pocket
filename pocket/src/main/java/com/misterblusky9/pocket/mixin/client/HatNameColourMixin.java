package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.misterblusky9.pocket.client.HatStandNameTooltip;
import com.misterblusky9.pocket.client.HatWool;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class HatNameColourMixin {
    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component pocket$colourName(Component name) {
        ItemStack self = (ItemStack) (Object) this;
        boolean stand = self.getItem() instanceof BlockItem item && item.getBlock() instanceof HatStandBlock;
        return switch (stand ? NameEgg.of(self.get(DataComponents.PROFILE)) : NameEgg.of(self)) {
            case JEB -> name.copy().withColor(HatWool.textColour());
            case RAINBOW -> HatStandNameTooltip.rainbow(name.getString(), name.getStyle());
            default -> name;
        };
    }
}
