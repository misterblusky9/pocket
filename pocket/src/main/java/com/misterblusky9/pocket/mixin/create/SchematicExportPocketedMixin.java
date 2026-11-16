package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.create.SchematicPocketed;
import com.simibubi.create.content.schematics.SchematicExport;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.OutputStream;

@Mixin(value = SchematicExport.class, remap = false)
public abstract class SchematicExportPocketedMixin {
    @WrapOperation(
            method = "saveSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"
            ),
            remap = false
    )
    private static void pocket$leavePocketedOut(CompoundTag data, OutputStream out, Operation<Void> original) {
        SchematicPocketed.strip(data);
        original.call(data, out);
    }
}
