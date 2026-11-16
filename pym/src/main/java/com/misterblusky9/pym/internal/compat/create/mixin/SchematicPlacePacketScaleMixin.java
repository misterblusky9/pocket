package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.create.SchematicScales;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.packet.SchematicPlacePacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SchematicPlacePacket.class, remap = false)
public abstract class SchematicPlacePacketScaleMixin {
    @WrapOperation(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V"
            ),
            remap = false,
            require = 1
    )
    private void pym$placeInOwnGrid(SchematicPrinter printer, ItemStack stack, Level level, boolean processNBT, Operation<Void> original) {
        SchematicScales.instant(printer, stack, level, () -> original.call(printer, stack, level, processNBT));
    }
}
