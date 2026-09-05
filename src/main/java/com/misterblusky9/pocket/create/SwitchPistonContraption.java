package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.debug.SwitchPistonDebug;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

public final class SwitchPistonContraption extends PistonContraption implements SwitchContraption {
    private AABB interactionBounds;

    public SwitchPistonContraption() {
        super();
    }

    public SwitchPistonContraption(final Direction direction, final boolean retract) {
        super(direction, retract);
    }

    @Override
    public boolean assemble(final Level level, final BlockPos pos) throws AssemblyException {
        if (!super.assemble(level, pos)) {
            SwitchPistonDebug.info("Assembly found no moved structure at controller={} side={}",
                    pos, level.isClientSide ? "client" : "server");
            return false;
        }

        rebuildInteractionBounds();
        SwitchPistonDebug.info(
                "Built interaction box after assembly controller={} side={} blocks={} bounds={}",
                pos,
                level.isClientSide ? "client" : "server",
                getBlocks().size(),
                interactionBounds
        );
        return true;
    }

    @Override
    public void readNBT(final Level level, final CompoundTag tag, final boolean spawnData) {
        super.readNBT(level, tag, spawnData);
        rebuildInteractionBounds();
        SwitchPistonDebug.info(
                "Rebuilt interaction box from NBT side={} spawnData={} blocks={} bounds={}",
                level.isClientSide ? "client" : "server",
                spawnData,
                getBlocks().size(),
                interactionBounds
        );
    }

    private void rebuildInteractionBounds() {
        if (getBlocks().isEmpty() || orientation == null) {
            interactionBounds = null;
            return;
        }

        final Set<BlockPos> extension = extensionPositions();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;

        for (final BlockPos pos : getBlocks().keySet()) {
            if (extension.contains(pos)) {
                continue;
            }
            any = true;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (!any) {
            interactionBounds = null;
            return;
        }

        interactionBounds = new AABB(
                minX, minY, minZ,
                maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D
        );
    }

    private Set<BlockPos> extensionPositions() {
        final Set<BlockPos> positions = new HashSet<>();
        for (int offset = 1; offset <= extensionLength + 1; offset++) {
            positions.add(BlockPos.ZERO.relative(orientation, -offset));
        }
        return positions;
    }

    @Override
    public AABB getInteractionBounds() {
        return interactionBounds;
    }

    public int getExtensionLength() {
        return extensionLength;
    }

    public int getInitialExtensionProgress() {
        return initialExtensionProgress;
    }

    @Override
    public ContraptionType getType() {
        return PocketContraptionTypes.SWITCH_PISTON.value();
    }
}
