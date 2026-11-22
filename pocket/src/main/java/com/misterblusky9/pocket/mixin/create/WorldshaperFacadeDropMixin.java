package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.block.CopycatFacadeBlock;
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(value = TerrainTools.class, remap = false)
public abstract class WorldshaperFacadeDropMixin {
    @WrapMethod(method = "run")
    private void pocket$noFacadeDrops(
            Level world,
            List<BlockPos> targetPositions,
            Direction facing,
            @Nullable BlockState paintedState,
            @Nullable CompoundTag data,
            Player player,
            Operation<Void> original
    ) {
        CopycatFacadeBlock.shaping(true);
        try {
            original.call(world, targetPositions, facing, paintedState, data, player);
        } finally {
            CopycatFacadeBlock.shaping(false);
        }
    }
}
