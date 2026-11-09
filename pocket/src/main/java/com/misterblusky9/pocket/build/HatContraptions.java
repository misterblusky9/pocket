package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class HatContraptions {
    public static void disassemble(ServerSubLevel hat) {
        if (hat == null || hat.isRemoved() || hat.getPlot() == null) return;
        ServerLevel level = hat.getLevel();
        List<BlockEntity> controllers = new ArrayList<>();
        AABB area = null;
        for (PlotChunkHolder holder : hat.getPlot().getLoadedChunks()) {
            var chunk = holder.getChunk();
            ChunkPos pos = chunk.getPos();
            AABB column = new AABB(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
            area = area == null ? column : area.minmax(column);
            controllers.addAll(chunk.getBlockEntities().values());
        }
        for (BlockEntity controller : controllers) {
            if (controller.isRemoved()) continue;
            if (controller instanceof MechanicalBearingBlockEntity bearing) bearing.disassemble();
            else if (controller instanceof ClockworkBearingBlockEntity clockwork) clockwork.disassemble();
            else if (controller instanceof LinearActuatorBlockEntity actuator) actuator.disassemble();
            else if (controller instanceof HelmBearingBlockEntity helm) helm.disassemble();
        }
        if (area == null) return;
        for (AbstractContraptionEntity contraption : level.getEntities(
                EntityTypeTest.forClass(AbstractContraptionEntity.class), area,
                entity -> entity.isAlive() && entity.getVehicle() == null
                        && Sable.HELPER.getContaining(entity) == hat)) {
            contraption.disassemble();
        }
    }

    private HatContraptions() {}
}
