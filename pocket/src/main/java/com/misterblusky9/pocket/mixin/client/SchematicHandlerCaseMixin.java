package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.HatSchematics;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicInstances;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerCaseMixin {
    @Shadow private boolean active;
    @Shadow private ItemStack activeSchematicItem;
    @Shadow private ToolType currentTool;

    @Inject(method = "tick", at = @At("HEAD"))
    private void pocket$followCase(CallbackInfo ci) {
        HatSchematics.tick();
    }

    @WrapOperation(method = "findBlueprintInHand", at = @At(value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean pocket$caseAsBlueprint(ItemEntry<?> entry, ItemStack stack, Operation<Boolean> original) {
        return original.call(entry, stack) || HatSchematics.placing(stack);
    }

    @WrapOperation(method = {"init", "deploy"}, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/client/tools/ToolType;getTools(Z)Ljava/util/List;"))
    private List<ToolType> pocket$caseTools(boolean creative, Operation<List<ToolType>> original) {
        return original.call(creative || HatSchematics.placing(this.activeSchematicItem));
    }

    @WrapOperation(method = "setupRenderer", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/SchematicItem;loadSchematic(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;"))
    private StructureTemplate pocket$caseAsSchematic(Level level, ItemStack stack, Operation<StructureTemplate> original) {
        return HatSchematics.placing(stack) ? HatSchematics.template() : original.call(level, stack);
    }

    @Inject(method = "printInstantly", at = @At("HEAD"), cancellable = true)
    private void pocket$placeCase(CallbackInfo ci) {
        if (HatSchematics.placing(this.activeSchematicItem)) {
            if (HatSchematics.print()) this.active = false;
            ci.cancel();
        } else if (HatSchematics.printOnHat()) {
            this.activeSchematicItem.set(AllDataComponents.SCHEMATIC_DEPLOYED, false);
            SchematicInstances.clearHash(this.activeSchematicItem);
            this.active = false;
            ((SchematicHandler) (Object) this).markDirty();
            ci.cancel();
        }
    }

    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true)
    private void pocket$deployOnHat(int button, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (this.active && pressed && button == 1 && this.currentTool == ToolType.DEPLOY && HatSchematics.deployOnHat())
            cir.setReturnValue(true);
    }

    @Inject(method = "onMouseInput", at = @At("RETURN"), cancellable = true)
    private void pocket$keepCaseInHand(int button, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && this.active && HatSchematics.swallows(button, pressed)) cir.setReturnValue(true);
    }
}
