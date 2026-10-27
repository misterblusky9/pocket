package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketTabBanner;
import com.misterblusky9.pocket.item.ModCreativeTabs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeTab.class)
public abstract class CreativeTabBannerRowMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Inject(method = "buildContents", at = @At("TAIL"), require = 1)
    private void pocket$reserveBannerRow(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if (!ModCreativeTabs.MAIN.isBound() || ModCreativeTabs.MAIN.get() != (Object) this) return;

        List<ItemStack> padded = new ArrayList<>(PocketTabBanner.PADDING_SLOTS + displayItems.size());
        for (int i = 0; i < PocketTabBanner.PADDING_SLOTS; i++) padded.add(ItemStack.EMPTY);
        padded.addAll(displayItems);
        displayItems = padded;
    }
}
