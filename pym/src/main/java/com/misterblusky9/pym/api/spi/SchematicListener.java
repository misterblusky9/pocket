package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public interface SchematicListener {
    default void saved(Saved saved) {}

    default void placed(Placed placed) {}

    default boolean permits(ServerLevel level, double scale) {
        return true;
    }

    record Captured(UUID id, SubLevel subLevel, CompoundTag entry, Vec3i offset) {
        public BlockPos local(BlockPos plot) {
            return plot.offset(this.offset);
        }
    }

    record Saved(Level level, Map<UUID, Captured> subLevels, @Nullable SubLevel around, BlockPos origin, Vec3i size) {
        public @Nullable BlockPos local(BlockPos pos) {
            BlockPos local = pos.subtract(this.origin);
            boolean inside = local.getX() >= 0 && local.getY() >= 0 && local.getZ() >= 0
                    && local.getX() < this.size.getX() && local.getY() < this.size.getY() && local.getZ() < this.size.getZ();
            return inside ? local : null;
        }
    }

    record Restored(ServerSubLevel subLevel, Vec3i offset) {
        public BlockPos plot(BlockPos local) {
            return local.offset(this.offset);
        }
    }

    record Placed(
            ServerLevel level,
            Map<UUID, Restored> subLevels,
            @Nullable Restored body,
            StructurePlaceSettings settings,
            BlockPos anchor
    ) {
        public BlockPos world(BlockPos local) {
            return net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                    .calculateRelativePosition(this.settings, local).offset(this.anchor);
        }
    }
}
