package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NeoForgeEventHandler.class, remap = false)
public abstract class CustomItemEntityScaleMixin {
    @WrapOperation(
            method = "onEntityJoinWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;createEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/Entity;"))
    private Entity pocket$keepScale(
            final Item item,
            final Level level,
            final Entity original,
            final ItemStack stack,
            final Operation<Entity> operation
    ) {
        final Entity replacement = operation.call(item, level, original, stack);
        if (!level.isClientSide()) PlayerSpawnScale.inherit(replacement, original);
        return replacement;
    }
}
