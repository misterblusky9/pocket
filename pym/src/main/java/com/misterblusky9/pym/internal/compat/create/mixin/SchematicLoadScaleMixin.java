package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.SchematicScale;
import com.misterblusky9.pym.internal.compat.create.SchematicScales;
import com.simibubi.create.content.schematics.SchematicItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchematicItem.class, remap = false)
public abstract class SchematicLoadScaleMixin {
    @Inject(method = "create", at = @At("HEAD"), remap = false, require = 1)
    private static void pym$beginSchematicItem(Level level, String name, String owner, CallbackInfoReturnable<ItemStack> cir) {
        SchematicScales.forget();
    }

    @Inject(method = "create", at = @At("RETURN"), remap = false, require = 1)
    private static void pym$recordSchematicScale(Level level, String name, String owner, CallbackInfoReturnable<ItemStack> cir) {
        SchematicScale.record(cir.getReturnValue(), SchematicScales.grid());
    }

    @WrapOperation(
            method = "loadSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;load(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)V"
            ),
            remap = false,
            require = 1
    )
    private static void pym$openGrid(
            StructureTemplate template,
            HolderGetter<Block> blocks,
            CompoundTag schematic,
            Operation<Void> original,
            @Local(argsOnly = true) Level level
    ) {
        SchematicScales.open(level, schematic);
        original.call(template, blocks, schematic);
    }
}
