package com.misterblusky9.pym.internal.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import com.misterblusky9.pym.internal.entity.FluidSectionCache;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SubLevelFluidScanBoundsMixin {
    @Unique
    private static final long PYM_FLUID_CACHE_MIN_CELLS = 512L;

    @Unique
    private static final ThreadLocal<FluidSectionCache> PYM_FLUID_SECTION_CACHE =
            ThreadLocal.withInitial(FluidSectionCache::new);

    @Unique
    private boolean pym$useFastFluidScan;

    @Shadow
    public abstract AABB getBoundingBox();

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("HEAD"))
    private void pym$prepareFluidScan(final CallbackInfo ci) {
        final AABB bounds = this.getBoundingBox().deflate(0.001);
        final long sizeX = (long) Mth.ceil(bounds.maxX) - Mth.floor(bounds.minX);
        final long sizeY = (long) Mth.ceil(bounds.maxY) - Mth.floor(bounds.minY);
        final long sizeZ = (long) Mth.ceil(bounds.maxZ) - Mth.floor(bounds.minZ);

        this.pym$useFastFluidScan = sizeX > 0L && sizeY > 0L && sizeZ > 0L
                && sizeX * sizeY * sizeZ >= PYM_FLUID_CACHE_MIN_CELLS;

        if (this.pym$useFastFluidScan) {
            PYM_FLUID_SECTION_CACHE.get().clear();
        }
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("RETURN"))
    private void pym$finishFluidScan(final CallbackInfo ci) {
        if (this.pym$useFastFluidScan) {
            PYM_FLUID_SECTION_CACHE.get().clear();
            this.pym$useFastFluidScan = false;
        }
    }

    @WrapOperation(
            method = "updateFluidHeightAndDoFluidPushing()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
            )
    )
    private FluidState pym$fastFluidStateLookup(
            final Level level,
            final BlockPos pos,
            final Operation<FluidState> original
    ) {
        if (!this.pym$useFastFluidScan) {
            return original.call(level, pos);
        }
        return PYM_FLUID_SECTION_CACHE.get().get(level, pos);
    }

    @WrapOperation(
            method = "updateFluidHeightAndDoFluidPushing()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3d;transformInverse(Ldev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/companion/math/BoundingBox3d;)Ldev/ryanhcode/sable/companion/math/BoundingBox3d;",
                    remap = false
            )
    )
    private BoundingBox3d pym$clampFluidScanToPlot(
            final BoundingBox3d global,
            final Pose3dc pose,
            final BoundingBox3d dest,
            final Operation<BoundingBox3d> original,
            @Local(name = "subLevel") final SubLevel subLevel
    ) {
        final BoundingBox3d local = original.call(global, pose, dest);
        PlotScanBounds.clampCells(subLevel, dest);
        return local;
    }
}
