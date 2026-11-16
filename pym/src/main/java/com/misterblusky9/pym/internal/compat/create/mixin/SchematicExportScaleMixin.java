package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create.SchematicScales;
import com.simibubi.create.content.schematics.SchematicExport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.OutputStream;

@Mixin(value = SchematicExport.class, remap = false)
public abstract class SchematicExportScaleMixin {
    @WrapOperation(
            method = "saveSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"
            ),
            remap = false,
            require = 1
    )
    private static void pym$writeScales(
            CompoundTag data,
            OutputStream out,
            Operation<Void> original,
            @Local(argsOnly = true) Level level
    ) {
        SchematicScales.write(level, data);
        original.call(data, out);
    }
}
