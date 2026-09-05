package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.block.SwitchBearingBlockEntity;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.mixin.create.ControlledContraptionEntityControllerInvoker;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class PocketedEntities {
    private static final String ENTITIES_KEY = "pocket_entities";

    private static final double CAPTURE_MARGIN = 1.0D;

    public static int capture(final ServerLevel level, final ServerSubLevel subLevel, final CompoundTag target) {
        final ListTag saved = new ListTag();

        for (final Entity entity : collect(level, subLevel)) {
            final CompoundTag entityTag = new CompoundTag();

            if (!entity.save(entityTag)) continue;
            saved.add(entityTag);
            entity.discard();
        }

        if (saved.isEmpty()) return 0;
        target.put(ENTITIES_KEY, saved);
        PocketTrace.scale("captured {} entities with sub-level uuid={}", saved.size(), subLevel.getUniqueId());
        return saved.size();
    }

    public static int restore(final ServerLevel level, final CompoundTag source) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;

        final ListTag saved = source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND);
        int restored = 0;

        for (int i = 0; i < saved.size(); i++) {
            final CompoundTag entityTag = saved.getCompound(i);
            try {
                entityTag.remove("UUID");
                final Entity entity = EntityType.loadEntityRecursive(entityTag, level, e -> e);
                if (entity == null) continue;
                if (level.addFreshEntity(entity)) restored++;
            } catch (final RuntimeException exception) {
                PocketTrace.warn("could not restore a pocketed entity: {}", exception.toString());
            }
        }

        if (restored > 0) PocketTrace.scale("restored {} pocketed entities", restored);
        return restored;
    }

    public static int count(final CompoundTag source) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;
        return source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND).size();
    }

    public static int rebase(
            final CompoundTag source,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        if (source == null || !source.contains(ENTITIES_KEY, Tag.TAG_LIST)) return 0;

        final ListTag saved = source.getList(ENTITIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            rebaseEntity(saved.getCompound(i), deltaX, deltaY, deltaZ);
        }
        return saved.size();
    }

    private static void rebaseEntity(
            final CompoundTag entity,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        if (entity.contains("Pos", Tag.TAG_LIST)) {
            final ListTag pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                pos.set(0, DoubleTag.valueOf(pos.getDouble(0) + deltaX));
                pos.set(1, DoubleTag.valueOf(pos.getDouble(1) + deltaY));
                pos.set(2, DoubleTag.valueOf(pos.getDouble(2) + deltaZ));
            }
        }

        if (!entity.contains("Passengers", Tag.TAG_LIST)) return;
        final ListTag passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            rebaseEntity(passengers.getCompound(i), deltaX, deltaY, deltaZ);
        }
    }

    public static int disassembleContraptions(final ServerLevel level, final ServerSubLevel subLevel) {
        final List<Entity> contraptions = level.getEntities(
                (Entity) null,
                region(subLevel),
                entity -> entity instanceof ControlledContraptionEntity && !entity.isRemoved()
        );

        for (final Entity entity : contraptions) {
            final ControlledContraptionEntity contraption = (ControlledContraptionEntity) entity;
            final IControlContraption controller =
                    ((ControlledContraptionEntityControllerInvoker) contraption).pocket$invokeGetController();

            if (controller instanceof final LinearActuatorBlockEntity actuator) {
                actuator.disassemble();
            } else if (controller instanceof final SwitchBearingBlockEntity bearing) {
                bearing.disassemble();
            } else if (controller instanceof final MechanicalBearingBlockEntity bearing) {
                bearing.disassemble();
            } else {
                contraption.disassemble();
            }
        }

        if (!contraptions.isEmpty()) {
            PocketTrace.scale("disassembled {} contraptions before pocketing sub-level uuid={}",
                    contraptions.size(), subLevel.getUniqueId());
        }
        return contraptions.size();
    }

    private static List<Entity> collect(final ServerLevel level, final ServerSubLevel subLevel) {
        return level.getEntities((Entity) null, region(subLevel), PocketedEntities::isCapturable);
    }

    private static AABB region(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return new AABB(
                bounds.minX() - CAPTURE_MARGIN, bounds.minY() - CAPTURE_MARGIN, bounds.minZ() - CAPTURE_MARGIN,
                bounds.maxX() + 1.0D + CAPTURE_MARGIN,
                bounds.maxY() + 1.0D + CAPTURE_MARGIN,
                bounds.maxZ() + 1.0D + CAPTURE_MARGIN
        );
    }

    private static boolean isCapturable(final Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (entity instanceof Player) return false;

        return !isCreateContraption(entity);
    }

    private static boolean isCreateContraption(final Entity entity) {
        return entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity;
    }

    private PocketedEntities() {}
}
