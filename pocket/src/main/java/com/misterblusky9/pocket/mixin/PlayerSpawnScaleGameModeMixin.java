package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerSpawnScaleGameModeMixin {
    @WrapMethod(method = "useItem")
    private InteractionResult pocket$scaleUseSpawns(
            final ServerPlayer player,
            final Level level,
            final ItemStack stack,
            final InteractionHand hand,
            final Operation<InteractionResult> original
    ) {
        return PlayerSpawnScale.using(player, null, null, () -> original.call(player, level, stack, hand));
    }

    @WrapMethod(method = "useItemOn")
    private InteractionResult pocket$scaleUseOnSpawns(
            final ServerPlayer player,
            final Level level,
            final ItemStack stack,
            final InteractionHand hand,
            final BlockHitResult hit,
            final Operation<InteractionResult> original
    ) {
        return PlayerSpawnScale.using(player, Sable.HELPER.getContaining(level, hit.getBlockPos()), hit,
                () -> original.call(player, level, stack, hand, hit));
    }
}
