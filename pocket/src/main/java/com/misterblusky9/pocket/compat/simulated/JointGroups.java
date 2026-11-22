package com.misterblusky9.pocket.compat.simulated;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JointGroups {
    public record Target(UUID uuid, Vec3i delta) {}

    private enum PosFormat { ARRAY, LONG }

    private record PartnerKeys(String uuid, String pos, PosFormat format) {}

    private static final List<PartnerKeys> PARTNERS = List.of(
            new PartnerKeys("SubLevelID", "SwivelPlate", PosFormat.ARRAY),
            new PartnerKeys("ParentSubLevelId", "ParentPos", PosFormat.ARRAY),
            new PartnerKeys("GoalSubLevel", "Goal", PosFormat.LONG),
            new PartnerKeys("OtherConnectorSubLevelId", "OtherConnector", PosFormat.ARRAY));

    private static final List<String> PARENT_KEYS = List.of("pym_parent", "pocket_parent");

    public static Set<ServerSubLevel> partners(ServerSubLevelContainer container, ServerSubLevel subLevel) {
        Set<ServerSubLevel> partners = new LinkedHashSet<>();
        if (container == null || subLevel == null || subLevel.isRemoved()) return partners;
        for (ServerSubLevel other : container.getAllSubLevels()) {
            if (other == subLevel || other.isRemoved()) continue;
            if (declares(subLevel, other) || declares(other, subLevel)) partners.add(other);
        }
        return partners;
    }

    private static boolean declares(ServerSubLevel from, ServerSubLevel to) {
        for (BlockEntitySubLevelActor actor : from.getPlot().getBlockEntityActors()) {
            if (actor instanceof RopeStrandHolderBlockEntity) continue;
            Iterable<SubLevel> dependencies = actor.sable$getConnectionDependencies();
            if (dependencies == null) continue;
            for (SubLevel dependency : dependencies) {
                if (dependency == to) return true;
            }
        }
        return false;
    }

    public static void breakRopes(ServerSubLevel subLevel) {
        List<RopeStrandHolderBehavior> holders = new ArrayList<>();
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof RopeStrandHolderBlockEntity holder)) continue;
            RopeStrandHolderBehavior behavior = holder.getBehavior();
            if (behavior != null && behavior.isAttached()) holders.add(behavior);
        }
        for (RopeStrandHolderBehavior behavior : holders) {
            if (behavior.isAttached()) behavior.destroy();
        }
    }

    public static void remap(CompoundTag payload, Map<UUID, Target> identities) {
        CompoundTag chunks = payload.getCompound("plot").getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            ListTag entries = chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) remapBlockEntity(entries.getCompound(i), identities);
        }
        if (payload.contains("user_data", Tag.TAG_COMPOUND)) {
            CompoundTag userData = payload.getCompound("user_data");
            for (String parentKey : PARENT_KEYS) {
                if (!userData.hasUUID(parentKey)) continue;
                Target target = identities.get(userData.getUUID(parentKey));
                if (target != null) userData.putUUID(parentKey, target.uuid());
            }
        }
    }

    private static void remapBlockEntity(CompoundTag entry, Map<UUID, Target> identities) {
        for (PartnerKeys keys : PARTNERS) {
            if (!entry.hasUUID(keys.uuid())) continue;
            Target target = identities.get(entry.getUUID(keys.uuid()));
            if (target == null) continue;
            entry.putUUID(keys.uuid(), target.uuid());
            if (!entry.contains(keys.pos())) continue;
            if (keys.format() == PosFormat.LONG) {
                entry.putLong(keys.pos(), BlockPos.of(entry.getLong(keys.pos())).offset(target.delta()).asLong());
            } else {
                NbtUtils.readBlockPos(entry, keys.pos()).ifPresent(pos ->
                        entry.put(keys.pos(), NbtUtils.writeBlockPos(pos.offset(target.delta()))));
            }
        }
    }

    private JointGroups() {}
}
