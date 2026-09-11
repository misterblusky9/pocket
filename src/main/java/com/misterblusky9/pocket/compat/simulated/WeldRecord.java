package com.misterblusky9.pocket.compat.simulated;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record WeldRecord(
        UUID weldId,
        UUID smallSubLevel,
        UUID bigSubLevel,
        BlockPos smallPos,
        BlockPos bigPos,
        Direction smallFacing,
        Direction bigFacing,
        Vector3d smallAnchor,
        Vector3d bigAnchor,
        double smallSpan,
        double bigSpan,
        Quaterniond orientation
) {
    public boolean worldAnchored() {
        return this.bigSubLevel == null;
    }

    public boolean touches(final UUID subLevelId) {
        return subLevelId != null
                && (subLevelId.equals(this.smallSubLevel) || subLevelId.equals(this.bigSubLevel));
    }

    public UUID other(final UUID subLevelId) {
        if (subLevelId == null) return null;
        if (subLevelId.equals(this.smallSubLevel)) return this.bigSubLevel;
        if (subLevelId.equals(this.bigSubLevel)) return this.smallSubLevel;
        return null;
    }

    public boolean connects(final UUID first, final UUID second) {
        return !worldAnchored() && touches(first) && touches(second) && !first.equals(second);
    }

    public UUID subLevelFor(final boolean small) {
        return small ? this.smallSubLevel : this.bigSubLevel;
    }

    public BlockPos posFor(final boolean small) {
        return small ? this.smallPos : this.bigPos;
    }

    public Direction facingFor(final boolean small) {
        return small ? this.smallFacing : this.bigFacing;
    }

    public Vector3d anchorFor(final boolean small) {
        return new Vector3d(small ? this.smallAnchor : this.bigAnchor);
    }

    public double spanFor(final boolean small) {
        return small ? this.smallSpan : this.bigSpan;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("weld", this.weldId);
        tag.putUUID("small", this.smallSubLevel);
        tag.putBoolean("world", worldAnchored());
        if (!worldAnchored()) tag.putUUID("big", this.bigSubLevel);
        putPos(tag, "small_pos", this.smallPos);
        putPos(tag, "big_pos", this.bigPos);
        tag.putInt("small_facing", this.smallFacing.get3DDataValue());
        tag.putInt("big_facing", this.bigFacing.get3DDataValue());
        putVec(tag, "small_anchor", this.smallAnchor);
        putVec(tag, "big_anchor", this.bigAnchor);
        tag.putDouble("small_span", this.smallSpan);
        tag.putDouble("big_span", this.bigSpan);
        tag.putDouble("orient_x", this.orientation.x);
        tag.putDouble("orient_y", this.orientation.y);
        tag.putDouble("orient_z", this.orientation.z);
        tag.putDouble("orient_w", this.orientation.w);
        return tag;
    }

    public static WeldRecord load(final CompoundTag tag) {
        if (tag == null || !tag.hasUUID("weld") || !tag.hasUUID("small")) {
            return null;
        }
        final boolean world = tag.getBoolean("world") || !tag.hasUUID("big");
        try {
            return new WeldRecord(
                    tag.getUUID("weld"),
                    tag.getUUID("small"),
                    world ? null : tag.getUUID("big"),
                    readPos(tag, "small_pos"),
                    readPos(tag, "big_pos"),
                    Direction.from3DDataValue(tag.getInt("small_facing")),
                    Direction.from3DDataValue(tag.getInt("big_facing")),
                    readVec(tag, "small_anchor"),
                    readVec(tag, "big_anchor"),
                    tag.getDouble("small_span"),
                    tag.getDouble("big_span"),
                    new Quaterniond(
                            tag.getDouble("orient_x"),
                            tag.getDouble("orient_y"),
                            tag.getDouble("orient_z"),
                            tag.getDouble("orient_w")).normalize());
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static void putPos(final CompoundTag tag, final String key, final BlockPos pos) {
        tag.putLong(key, pos.asLong());
    }

    private static BlockPos readPos(final CompoundTag tag, final String key) {
        return BlockPos.of(tag.getLong(key));
    }

    private static void putVec(final CompoundTag tag, final String key, final Vector3d vec) {
        tag.putDouble(key + "_x", vec.x);
        tag.putDouble(key + "_y", vec.y);
        tag.putDouble(key + "_z", vec.z);
    }

    private static Vector3d readVec(final CompoundTag tag, final String key) {
        return new Vector3d(
                tag.getDouble(key + "_x"),
                tag.getDouble(key + "_y"),
                tag.getDouble(key + "_z"));
    }
}
