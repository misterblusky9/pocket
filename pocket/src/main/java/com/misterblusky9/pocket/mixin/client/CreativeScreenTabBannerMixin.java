package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketTabBanner;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenTabBannerMixin {
    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void pocket$renderTabBanner(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ModCreativeTabs.MAIN.isBound() && selectedTab == ModCreativeTabs.MAIN.get()) {
            PocketTabBanner.render((CreativeModeInventoryScreen) (Object) this, graphics, mouseX, mouseY);
        }
    }
}
