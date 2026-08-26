package com.misterblusky9.pocket.scale;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import java.util.UUID;

public final class DisassemblyScaleAlignment {
    public static final int EXPANSION_BUDGET_TICKS = 100;

    public static UUID begin(final BlockEntity assembler) {
        final ServerSubLevel subLevel = containing(assembler);
        if (subLevel == null || !ScaleState.isScaled(subLevel)) return null;

        request(subLevel, assembler.getBlockPos());
        return subLevel.getUniqueId();
    }

    public static boolean align(final BlockEntity assembler) {
        final ServerSubLevel subLevel = containing(assembler);
        if (subLevel == null) return true;

        if (ScaleState.isSettled(subLevel.getUniqueId())
                && ScaleState.getStage(subLevel) == CompressionStage.NORMAL) {
            return true;
        }

        request(subLevel, assembler.getBlockPos());
        return false;
    }

    public static void end(final UUID subLevelId) {
        ScaleController.clearExternalCommand(subLevelId);
    }

    private static void request(final ServerSubLevel subLevel, final BlockPos anchor) {
        ScaleController.forceStage(
                subLevel,
                CompressionStage.NORMAL,
                subLevel.getLevel().getGameTime(),
                new Vector3d(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D));
    }

    private static ServerSubLevel containing(final BlockEntity assembler) {
        if (assembler == null) return null;

        final SubLevel found = Sable.HELPER.getContaining(assembler);
        return found instanceof final ServerSubLevel subLevel && !subLevel.isRemoved()
                ? subLevel
                : null;
    }

    private DisassemblyScaleAlignment() {}
}
