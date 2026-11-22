package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.create.SchematicPocketed;
import com.misterblusky9.pocket.compat.create.SchematicWelds;
import com.simibubi.create.content.schematics.SchematicItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SchematicItem.class, remap = false)
public abstract class SchematicLoadPocketedMixin {
    @WrapOperation(
            method = "loadSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;load(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)V"
            ),
            remap = false
    )
    private static void pocket$leavePocketedOut(
            StructureTemplate template,
            HolderGetter<Block> blocks,
            CompoundTag schematic,
            Operation<Void> original
    ) {
        SchematicPocketed.strip(schematic);
        SchematicWelds.loaded(schematic);
        original.call(template, blocks, schematic);
    }
}
