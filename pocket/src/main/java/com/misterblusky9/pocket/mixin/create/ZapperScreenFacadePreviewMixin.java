package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.client.WorldshaperFacadePreview;
import com.simibubi.create.content.equipment.zapper.ZapperScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ZapperScreen.class, remap = false)
public abstract class ZapperScreenFacadePreviewMixin {
    @Shadow(remap = false)
    protected ItemStack zapper;

    @ModifyVariable(method = "renderBlock", at = @At("STORE"), ordinal = 0, remap = false)
    private BlockState pocket$facadeMaterial(BlockState state) {
        return WorldshaperFacadePreview.shown(state, this.zapper);
    }
}
