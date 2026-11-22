package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.client.CaseCloning;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.CaseClonePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
public abstract class CreativeCaseCloneMixin {
    @Shadow
    private int quickcraftType;

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pocket$cloneEmptyCase(
            ItemStack stack,
            int count,
            Operation<ItemStack> original,
            @Local(argsOnly = true) ClickType clickType,
            @Local(argsOnly = true) Player player
    ) {
        if (!pocket$cloning(clickType, stack)) return original.call(stack, count);
        if (clickType == ClickType.CLONE && player instanceof ServerPlayer server) {
            ItemStack copy = CaseClonePayload.duplicate(server.serverLevel(), stack);
            if (copy != null) return copy;
        } else if (clickType == ClickType.CLONE && player.level().isClientSide()) {
            CaseCloning.request(stack);
            return ItemStack.EMPTY;
        }
        if (BuildHostBlockEntity.token(stack) != null) return original.call(BuildHostBlockEntity.plain(stack), count);
        ItemStack empty = new ItemStack(PocketCaseItem.containerItem(stack));
        return empty.copyWithCount(clickType == ClickType.CLONE ? empty.getMaxStackSize() : Math.min(count, empty.getMaxStackSize()));
    }

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;setCount(I)V"))
    private void pocket$keepDraggedCase(
            ItemStack stack,
            int count,
            Operation<Void> original,
            @Local(argsOnly = true) ClickType clickType
    ) {
        if (!pocket$cloning(clickType, stack)) original.call(stack, count);
    }

    private boolean pocket$cloning(ClickType clickType, ItemStack stack) {
        boolean clone = clickType == ClickType.CLONE
                || clickType == ClickType.QUICK_CRAFT && this.quickcraftType == AbstractContainerMenu.QUICKCRAFT_TYPE_CLONE;
        return clone && (PocketCaseItem.isUnique(stack) || BuildHostBlockEntity.token(stack) != null);
    }
}
