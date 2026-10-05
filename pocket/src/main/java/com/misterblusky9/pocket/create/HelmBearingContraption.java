package com.misterblusky9.pocket.create;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class HelmBearingContraption extends BearingContraption implements InteractiveContraption {
    private AABB interactionBounds;

    public HelmBearingContraption() {
        super();
    }

    public HelmBearingContraption(final Direction facing) {
        super(false, facing);
    }

    @Override
    public boolean assemble(final Level level, final BlockPos pos) throws AssemblyException {
        if (!super.assemble(level, pos)) {
            return false;
        }
        rebuildInteractionBounds();
        return true;
    }

    @Override
    public void readNBT(final Level level, final CompoundTag tag, final boolean spawnData) {
        super.readNBT(level, tag, spawnData);
        rebuildInteractionBounds();
    }

    private void rebuildInteractionBounds() {
        if (getBlocks().isEmpty()) {
            interactionBounds = null;
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (final BlockPos pos : getBlocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        interactionBounds = new AABB(
                minX, minY, minZ,
                maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D
        );
    }

    @Override
    public AABB getInteractionBounds() {
        return interactionBounds;
    }

    @Override
    public ContraptionType getType() {
        return PocketContraptionTypes.HELM_BEARING.value();
    }
}
