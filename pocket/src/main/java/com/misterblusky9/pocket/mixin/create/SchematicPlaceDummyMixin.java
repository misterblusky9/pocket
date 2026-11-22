package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockHelper.class, remap = false)
public abstract class SchematicPlaceDummyMixin {
    @Inject(method = "placeSchematicBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pocket$keepDummy(Level world, BlockState state, BlockPos target, ItemStack stack,
                                         CompoundTag data, CallbackInfo ci) {
        if (world.getBlockState(target).is(ModBlocks.POCKET_DUMMY.get())) ci.cancel();
    }
}
