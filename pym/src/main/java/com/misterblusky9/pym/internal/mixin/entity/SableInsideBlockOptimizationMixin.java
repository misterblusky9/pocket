package com.misterblusky9.pym.internal.mixin.entity;

import com.bawnorton.mixinsquared.TargetHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SableInsideBlockOptimizationMixin {
    @Unique
    private static final long PYM$MAX_INSIDE_BLOCK_CELLS = 4096L;

    @Unique
    private static final double PYM$BOUNDS_EPSILON = 1.0E-7;

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    private Level level;

    @Shadow
    protected abstract void onInsideBlock(BlockState blockState);

    @TargetHandler(
            mixin = "dev.ryanhcode.sable.mixin.entity.entities_in_blocks.EntityMixin",
            name = "checkInsideBlocks"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$replaceSableInsideBlockScan(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        AABB bounds = this.getBoundingBox();
        BoundingBox3d worldBounds = new BoundingBox3d(bounds);
        BoundingBox3d localBounds = new BoundingBox3d(bounds);
        LevelAccelerator accelerator = new LevelAccelerator(this.level);

        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(this.level, worldBounds)) {
            BoundingBox3ic plotBounds = subLevel.getPlot() == null
                    ? null
                    : subLevel.getPlot().getBoundingBox();
            if (plotBounds == null) continue;

            localBounds.set(bounds);
            localBounds.transformInverse(subLevel.logicalPose(), localBounds);

            int minX = Math.max(Mth.floor(localBounds.minX + PYM$BOUNDS_EPSILON), plotBounds.minX());
            int minY = Math.max(
                    Math.max(Mth.floor(localBounds.minY + PYM$BOUNDS_EPSILON), plotBounds.minY()),
                    this.level.getMinBuildHeight()
            );
            int minZ = Math.max(Mth.floor(localBounds.minZ + PYM$BOUNDS_EPSILON), plotBounds.minZ());
            int maxX = Math.min(Mth.floor(localBounds.maxX - PYM$BOUNDS_EPSILON), plotBounds.maxX());
            int maxY = Math.min(
                    Math.min(Mth.floor(localBounds.maxY - PYM$BOUNDS_EPSILON), plotBounds.maxY()),
                    this.level.getMaxBuildHeight() - 1
            );
            int maxZ = Math.min(Mth.floor(localBounds.maxZ - PYM$BOUNDS_EPSILON), plotBounds.maxZ());

            if (minX > maxX || minY > maxY || minZ > maxZ) continue;

            long cells = (long) (maxX - minX + 1)
                    * (maxY - minY + 1)
                    * (maxZ - minZ + 1);
            if (cells > PYM$MAX_INSIDE_BLOCK_CELLS) continue;

            BlockPos minPos = new BlockPos(minX, minY, minZ);
            BlockPos maxPos = new BlockPos(maxX, maxY, maxZ);
            if (!this.level.hasChunksAt(minPos, maxPos)) continue;

            if (!pym$scanInsideBlocks(entity, accelerator, minX, minY, minZ, maxX, maxY, maxZ)) {
                break;
            }
        }

        ci.cancel();
    }

    @Unique
    private boolean pym$scanInsideBlocks(
            Entity entity,
            LevelAccelerator accelerator,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        int minSectionY = minY >> 4;
        int maxSectionY = maxY >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int x0 = Math.max(minX, chunkX << 4);
            int x1 = Math.min(maxX, (chunkX << 4) + 15);

            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int z0 = Math.max(minZ, chunkZ << 4);
                int z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                LevelChunk chunk = accelerator.getChunk(chunkX, chunkZ);

                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) continue;

                    LevelChunkSection section = chunk.getSections()[sectionIndex];
                    if (section == null || section.hasOnlyAir()) continue;

                    int y0 = Math.max(minY, sectionY << 4);
                    int y1 = Math.min(maxY, (sectionY << 4) + 15);

                    for (int x = x0; x <= x1; x++) {
                        int localX = x & 15;
                        for (int y = y0; y <= y1; y++) {
                            int localY = y & 15;
                            for (int z = z0; z <= z1; z++) {
                                BlockState state = section.getBlockState(localX, localY, z & 15);
                                if (state.isAir()) continue;
                                if (!this.isAlive()) return false;

                                pos.set(x, y, z);
                                try {
                                    state.entityInside(this.level, pos, entity);
                                    this.onInsideBlock(state);
                                } catch (Throwable throwable) {
                                    CrashReport report = CrashReport.forThrowable(
                                            throwable,
                                            "Colliding entity with block"
                                    );
                                    CrashReportCategory category = report.addCategory("Block being collided with");
                                    CrashReportCategory.populateBlockDetails(category, this.level, pos, state);
                                    throw new ReportedException(report);
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
