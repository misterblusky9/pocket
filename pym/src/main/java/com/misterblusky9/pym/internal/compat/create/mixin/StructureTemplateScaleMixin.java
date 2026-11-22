package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.SchematicScales;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateScaleMixin {
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void pym$readScales(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        SchematicScales.read(tag);
    }
}
