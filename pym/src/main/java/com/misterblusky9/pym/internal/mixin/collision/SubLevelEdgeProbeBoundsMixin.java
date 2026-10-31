package com.misterblusky9.pym.internal.mixin.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class SubLevelEdgeProbeBoundsMixin {
    @Redirect(
            method = "canFallAtleastWithSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
            ),
            remap = false
    )
    private static Iterable<BlockPos> pym$clampProbeToPlot(
            BlockPos min,
            BlockPos max,
            @Local(name = "subLevel") SubLevel subLevel,
            @Local(name = "pose") Pose3dc pose,
            @Local(argsOnly = true) AABB aabb
    ) {
        BoundingBox3ic plot = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (plot != null) {
            double scale = Math.abs(pose.scale().x());
            SubLevelIndexedCollision.Probe probe = SubLevelIndexedCollision.probe(
                    subLevel, Double.isFinite(scale) && scale > 1.0E-7 ? scale : 1.0, aabb,
                    Math.max(min.getX(), plot.minX()), Math.max(min.getY(), plot.minY()),
                    Math.max(min.getZ(), plot.minZ()), Math.min(max.getX(), plot.maxX()),
                    Math.min(max.getY(), plot.maxY()), Math.min(max.getZ(), plot.maxZ()));
            if (probe != null) return probe;
        }
        return PlotScanBounds.betweenClosed(
                subLevel,
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ()
        );
    }

    @WrapOperation(
            method = "canFallAtleastWithSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            remap = false,
            require = 1
    )
    private static BlockState pym$probeMaterial(
            Level level, BlockPos pos, Operation<BlockState> original,
            @Local(name = "blocks") Iterable<BlockPos> blocks
    ) {
        return original.call(level, blocks instanceof SubLevelIndexedCollision.Probe probe ? probe.material(pos) : pos);
    }

    @WrapOperation(
            method = "canFallAtleastWithSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            ),
            remap = false,
            require = 1
    )
    private static VoxelShape pym$probeShape(
            BlockState state, BlockGetter level, BlockPos pos, Operation<VoxelShape> original,
            @Local(name = "blocks") Iterable<BlockPos> blocks
    ) {
        VoxelShape shape = blocks instanceof SubLevelIndexedCollision.Probe probe ? probe.shape(pos) : null;
        return shape != null ? shape : original.call(state, level, pos);
    }
}
