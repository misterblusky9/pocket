package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = TerrainTools.class, remap = false)
public abstract class WorldshaperFacadeDropMixin {
    @Inject(method = "run", at = @At("HEAD"), remap = false)
    private void pocket$noFacadeDrops(
            Level world,
            List<BlockPos> targetPositions,
            Direction facing,
            @Nullable BlockState paintedState,
            @Nullable CompoundTag data,
            Player player,
            CallbackInfo ci
    ) {
        for (BlockPos pos : targetPositions) {
            if (world.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())
                    && world.getBlockEntity(pos) instanceof CopycatBlockEntity copycat) {
                copycat.setConsumedItem(ItemStack.EMPTY);
            }
        }
    }
}
