package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.misterblusky9.pocket.client.HatRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class HatDisguiseShadowMixin {
    @WrapWithCondition(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "FFLnet/minecraft/world/level/LevelReader;F)V"
            )
    )
    private boolean pocket$noShadowUnderHatDisguise(
            PoseStack pose, MultiBufferSource buffers, Entity entity,
            float weight, float partialTicks, LevelReader level, float size
    ) {
        return !(entity instanceof Player player && HatRendering.hatDisguised(player));
    }
}
