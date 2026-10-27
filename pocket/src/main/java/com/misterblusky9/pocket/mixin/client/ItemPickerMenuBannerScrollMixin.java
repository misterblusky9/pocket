package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketTabBanner;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class ItemPickerMenuBannerScrollMixin {
    @Shadow
    protected abstract int getRowIndexForScroll(float scrollOffs);

    @Inject(method = "scrollTo", at = @At("HEAD"), require = 1)
    private void pocket$trackBannerRow(float pos, CallbackInfo ci) {
        PocketTabBanner.currentRow = getRowIndexForScroll(pos);
    }
}
