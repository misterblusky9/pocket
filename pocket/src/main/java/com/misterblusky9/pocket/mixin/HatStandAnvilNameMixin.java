package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AnvilMenu.class)
public abstract class HatStandAnvilNameMixin extends ItemCombinerMenu {
    private HatStandAnvilNameMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void pocket$nameStand(CallbackInfo ci) {
        ItemStack result = resultSlots.getItem(0);
        Component custom = result.get(DataComponents.CUSTOM_NAME);
        if (custom == null || !(result.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof HatStandBlock))
            return;
        String name = custom.getString();
        if (!StringUtil.isValidPlayerName(name)) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            return;
        }
        result.remove(DataComponents.CUSTOM_NAME);
        result.set(DataComponents.PROFILE, new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap()));
    }
}
