package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.client.WorldshaperFacadePreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.zapper.ZapperItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ZapperItemRenderer.class, remap = false)
public abstract class ZapperItemFacadePreviewMixin {
    @ModifyVariable(method = "renderBlockUsed", at = @At("STORE"), ordinal = 0, remap = false)
    private BlockState pocket$facadeMaterial(BlockState state, ItemStack stack, PoseStack ms,
                                             MultiBufferSource buffer, int light, int overlay) {
        return WorldshaperFacadePreview.shown(state, stack);
    }
}
