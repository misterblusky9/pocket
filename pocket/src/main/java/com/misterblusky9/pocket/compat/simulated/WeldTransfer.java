package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class WeldTransfer {
    public record Member(UUID id, Vec3i offset) {}

    public record Body(@Nullable UUID around, Function<BlockPos, BlockPos> local) {}

    public record Target(ServerSubLevel subLevel, Vec3i offset) {}

    public record Placement(@Nullable Target subLevel, StructurePlaceSettings settings, BlockPos anchor) {}

    public static ListTag capture(Collection<WeldRecord> records, Map<UUID, Member> members, @Nullable Body body) {
        ListTag welds = new ListTag();
        for (WeldRecord record : records) {
            WeldRecord saved = capture(record, members, body);
            if (saved != null) welds.add(saved.save());
        }
        return welds;
    }

    private static @Nullable WeldRecord capture(WeldRecord record, Map<UUID, Member> members, @Nullable Body body) {
        Member small = members.get(record.smallSubLevel());
        Member big = record.worldAnchored() ? null : members.get(record.bigSubLevel());
        if (small != null && big != null) return moved(record, small, big.id(), big.offset());
        if (body == null) return null;

        boolean bigIsBody = record.worldAnchored() ? body.around() == null : record.bigSubLevel().equals(body.around());
        if (small != null && bigIsBody) {
            BlockPos local = body.local().apply(record.bigPos());
            return local == null ? null : moved(record, small, null, local.subtract(record.bigPos()));
        }

        boolean smallIsBody = record.smallSubLevel().equals(body.around());
        if (big != null && smallIsBody) {
            BlockPos local = body.local().apply(record.smallPos());
            return local == null ? null : moved(swapped(record), big, null, local.subtract(record.smallPos()));
        }
        return null;
    }

    private static WeldRecord swapped(WeldRecord record) {
        return new WeldRecord(record.weldId(), record.bigSubLevel(), record.smallSubLevel(),
                record.bigPos(), record.smallPos(), record.bigFacing(), record.smallFacing(),
                record.anchorFor(false), record.anchorFor(true), record.bigSpan(), record.smallSpan(),
                new Quaterniond(record.orientation()).invert());
    }

    private static WeldRecord moved(WeldRecord record, Member small, @Nullable UUID big, Vec3i bigOffset) {
        return new WeldRecord(record.weldId(), small.id(), big,
                record.smallPos().offset(small.offset()), record.bigPos().offset(bigOffset),
                record.smallFacing(), record.bigFacing(),
                shifted(record.anchorFor(true), small.offset()), shifted(record.anchorFor(false), bigOffset),
                record.smallSpan(), record.bigSpan(), new Quaterniond(record.orientation()));
    }

    public static int restore(ServerLevel level, ListTag welds, Map<UUID, Target> members, @Nullable Placement body) {
        WeldStore store = WeldStore.get(level);
        List<UUID> restored = new ArrayList<>();
        for (int i = 0; i < welds.size(); i++) {
            WeldRecord record = WeldRecord.load(welds.getCompound(i));
            if (record == null) continue;
            WeldRecord placed = restore(record, members, body, level);
            if (placed == null) continue;
            store.add(placed);
            restored.add(placed.weldId());
        }
        if (!restored.isEmpty()) {
            CrossScaleWelds.invalidate(level);
            WeldRuntime.rebindMigrated(level, restored);
            CrossScaleWeldSync.broadcast(level);
        }
        return restored.size();
    }

    private static @Nullable WeldRecord restore(WeldRecord record, Map<UUID, Target> members, @Nullable Placement body,
                                                ServerLevel level) {
        Target small = members.get(record.smallSubLevel());
        if (small == null) return null;
        Target big = record.worldAnchored() ? body == null ? null : body.subLevel() : members.get(record.bigSubLevel());
        if (big != null) return placed(record, small, big.subLevel(), big.offset());
        if (!record.worldAnchored() || body == null) return null;

        StructurePlaceSettings settings = body.settings();
        BlockPos bigPos = StructureTemplate.calculateRelativePosition(settings, record.bigPos()).offset(body.anchor());
        SubLevel container = Sable.HELPER.getContaining(level, bigPos);
        if (container != null && (container.isRemoved() || Detached.isDetached(container) || Hosted.isHosted(container)
                || container == small.subLevel())) {
            return null;
        }
        Vector3d anchor = record.anchorFor(false);
        Vec3 bigAnchor = StructureTemplate.transform(new Vec3(anchor.x, anchor.y, anchor.z),
                settings.getMirror(), settings.getRotation(), settings.getRotationPivot());
        Direction bigFacing = settings.getRotation().rotate(settings.getMirror().mirror(record.bigFacing()));
        Quaterniond orientation = new Quaterniond(record.orientation())
                .mul(new Quaterniond().rotationY(-angle(settings.getRotation()))).normalize();

        double smallScale = Pym.scale().settled(small.subLevel());
        double bigScale = container == null ? ScaleBounds.FULL : Pym.scale().settled(container);
        return new WeldRecord(UUID.randomUUID(), small.subLevel().getUniqueId(),
                container == null ? null : container.getUniqueId(),
                record.smallPos().offset(small.offset()), bigPos,
                record.smallFacing(), bigFacing,
                shifted(record.anchorFor(true), small.offset()),
                new Vector3d(bigAnchor.x, bigAnchor.y, bigAnchor.z).add(body.anchor().getX(), body.anchor().getY(), body.anchor().getZ()),
                WeldGeometry.span(smallScale, bigScale), WeldGeometry.span(bigScale, smallScale), orientation);
    }

    private static @Nullable WeldRecord placed(WeldRecord record, Target small, ServerSubLevel big, Vec3i bigOffset) {
        if (big == small.subLevel()) return null;
        return new WeldRecord(UUID.randomUUID(), small.subLevel().getUniqueId(), big.getUniqueId(),
                record.smallPos().offset(small.offset()), record.bigPos().offset(bigOffset),
                record.smallFacing(), record.bigFacing(),
                shifted(record.anchorFor(true), small.offset()), shifted(record.anchorFor(false), bigOffset),
                record.smallSpan(), record.bigSpan(), new Quaterniond(record.orientation()));
    }

    private static double angle(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0.0D;
            case CLOCKWISE_90 -> -Math.PI / 2.0D;
            case CLOCKWISE_180 -> Math.PI;
            case COUNTERCLOCKWISE_90 -> Math.PI / 2.0D;
        };
    }

    private static Vector3d shifted(Vector3d anchor, Vec3i offset) {
        return anchor.add(offset.getX(), offset.getY(), offset.getZ());
    }

    private WeldTransfer() {}
}
