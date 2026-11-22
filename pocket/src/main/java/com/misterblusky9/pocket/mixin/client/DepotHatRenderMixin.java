package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.HatRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(value = DepotRenderer.class, remap = false)
public abstract class DepotHatRenderMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pocket$renderHat(PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                         ItemStack itemStack, int angle, Random r, Vec3 itemPosition,
                                         boolean alwaysUpright, CallbackInfo ci) {
        if (!alwaysUpright && HatRendering.renderOnDepot(itemStack, angle, ms, buffer, light, overlay)) ci.cancel();
    }
}
