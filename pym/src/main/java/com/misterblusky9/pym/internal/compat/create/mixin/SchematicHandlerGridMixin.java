package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.SchematicGrid;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import net.createmod.catnip.outliner.AABBOutline;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerGridMixin {
    @Shadow private SchematicTransformation transformation;
    @Shadow private ItemStack activeSchematicItem;
    @Shadow private int activeHotbarSlot;
    @Shadow private AABB bounds;
    @Shadow private AABBOutline outline;

    @Inject(method = "loadSettings", at = @At("HEAD"), remap = false, require = 1)
    private void pym$readGrid(ItemStack blueprint, CallbackInfo ci) {
        SchematicGrid.show(blueprint);
    }

    @Inject(method = "loadSettings", at = @At("TAIL"), remap = false, require = 1)
    private void pym$outlineSubLevels(ItemStack blueprint, CallbackInfo ci) {
        if (this.outline != null && this.bounds != null) this.outline.setBounds(SchematicGrid.outline(this.bounds));
    }


    @Inject(method = "sync", at = @At("TAIL"), remap = false, require = 1)
    private void pym$syncFine(CallbackInfo ci) {
        if (this.activeSchematicItem != null) SchematicGrid.sync(this.activeSchematicItem, this.activeHotbarSlot, this.transformation);
    }

    @Inject(method = "printInstantly", at = @At("HEAD"), remap = false, require = 1)
    private void pym$printFine(CallbackInfo ci) {
        SchematicGrid.print(this.activeSchematicItem, this.transformation);
    }
}
