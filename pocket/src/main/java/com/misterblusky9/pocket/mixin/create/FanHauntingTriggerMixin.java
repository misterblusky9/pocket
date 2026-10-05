package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.advancement.HauntedCompressionGunTrigger;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AirCurrent.class, remap = false)
public abstract class FanHauntingTriggerMixin {
    // Loose item
    @WrapOperation(
            method = "tickAffectedEntities",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessing;"
                    + "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;"
                    + "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z"),
            require = 0
    )
    private boolean pocket$creditLooseGun(
            final ItemEntity entity,
            final FanProcessingType type,
            final Operation<Boolean> original
    ) {
        final boolean gun = type == AllFanProcessingTypes.HAUNTING
                && HauntedCompressionGunTrigger.isHauntingInput(entity.getItem());
        final boolean processed = original.call(entity, type);
        if (gun && processed && HauntedCompressionGunTrigger.isHauntingOutput(entity.getItem())) {
            HauntedCompressionGunTrigger.credit(entity.level(), entity.position(), entity.getOwner());
        }
        return processed;
    }

    // Belt
    @WrapOperation(
            method = "lambda$tickAffectedHandlers$0",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessing;"
                    + "applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)"
                    + "Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;"),
            require = 0
    )
    private TransportedResult pocket$creditHeldGun(
            final TransportedItemStack transported,
            final Level level,
            final FanProcessingType type,
            final Operation<TransportedResult> original,
            @Local(argsOnly = true) final TransportedItemStackHandlerBehaviour handler
    ) {
        final boolean gun = type == AllFanProcessingTypes.HAUNTING
                && HauntedCompressionGunTrigger.isHauntingInput(transported.stack);
        final TransportedResult result = original.call(transported, level, type);
        if (gun && !result.doesNothing()
                && result.getOutputs().stream().anyMatch(out -> HauntedCompressionGunTrigger.isHauntingOutput(out.stack))) {
            HauntedCompressionGunTrigger.credit(level, handler.getWorldPositionOf(transported), null);
        }
        return result;
    }
}
