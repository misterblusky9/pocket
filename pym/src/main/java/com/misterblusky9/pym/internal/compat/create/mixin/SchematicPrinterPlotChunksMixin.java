package com.misterblusky9.pym.internal.compat.create.mixin;

import com.simibubi.create.content.schematics.SchematicPrinter;
import com.misterblusky9.pym.internal.mixin.sable.ServerChunkCacheAccessor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchematicPrinter.class, remap = false)
public abstract class SchematicPrinterPlotChunksMixin {
    @Shadow private SchematicLevel blockReader;
    @Shadow private BlockPos schematicAnchor;

    @Inject(method = "loadSchematic", at = @At("TAIL"), remap = false)
    private void pym$allocatePlotChunks(ItemStack blueprint, Level world, boolean processNBT, CallbackInfo ci) {
        if (!(world instanceof ServerLevel level) || this.blockReader == null) return;
        LevelPlot plot = SubLevelContainer.getContainer(level).getPlot(new ChunkPos(this.schematicAnchor));
        if (plot == null) return;
        BoundingBox bounds = this.blockReader.getBounds();
        BlockPos anchor = this.schematicAnchor;
        for (int x = anchor.getX() + bounds.minX() >> 4; x <= anchor.getX() + bounds.maxX() >> 4; x++) {
            for (int z = anchor.getZ() + bounds.minZ() >> 4; z <= anchor.getZ() + bounds.maxZ() >> 4; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (plot.contains(chunk) && plot.getChunkHolder(plot.toLocal(chunk)) == null) plot.newEmptyChunk(chunk);
            }
        }
        ((ServerChunkCacheAccessor) level.getChunkSource()).pym$clearCache();
    }
}
