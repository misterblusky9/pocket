package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PersonalScale {
    private static final int PERSONAL_RESIZE_TICKS = (int) Pym.resize().defaultTransitionTicks();

    private static final String STAGE_KEY = "PocketPersonalScaleDepth";
    private static final String SCALE_KEY = "PocketPersonalScale";

    private PersonalScale() {}

    public static void instant(final ServerPlayer player, final double scale) {
        if (player == null || !DeviceRanges.of(DeviceRanges.Device.PERSONAL_COMPRESSOR).contains(scale)) return;
        applyScale(player, scale);
    }

    public static void restore(final ServerPlayer player) {
        final double scale = currentScale(player);
        if (!ScaleBounds.same(scale, ScaleBounds.FULL)) Pym.entities().setScale(player, scale, 0);
    }

    public static double currentScale(final ServerPlayer player) {
        if (player == null) return ScaleBounds.FULL;
        final CompoundTag data = player.getPersistentData();
        if (data.contains(SCALE_KEY, Tag.TAG_ANY_NUMERIC) && ScaleBounds.isValid(data.getDouble(SCALE_KEY))) {
            return CompressionStage.snap(data.getDouble(SCALE_KEY));
        }
        return CompressionStage.fromDepth(data.getInt(STAGE_KEY)).scale();
    }

    public static double goalFor(final Entity entity, final double current, final double goal) {
        if (!(entity instanceof Player)) return goal;
        final ScaleBounds bounds = DeviceRanges.of(DeviceRanges.Device.PERSONAL_COMPRESSOR);
        return Math.max(Math.min(current, bounds.min()), Math.min(Math.max(current, bounds.max()), goal));
    }

    public static void remember(final ServerPlayer player, final double scale) {
        if (player == null || !ScaleBounds.isValid(scale)) return;
        final double snapped = CompressionStage.snap(scale);
        final CompoundTag data = player.getPersistentData();
        data.putDouble(SCALE_KEY, snapped);
        data.putInt(STAGE_KEY, CompressionStage.nearest(snapped).depth());
    }

    private static void applyScale(final ServerPlayer player, final double scale) {
        final double clamped = CompressionStage.snap(scale);
        final CompoundTag data = player.getPersistentData();
        data.putDouble(SCALE_KEY, clamped);
        data.putInt(STAGE_KEY, CompressionStage.nearest(clamped).depth());
        Pym.entities().setScale(player, clamped, PERSONAL_RESIZE_TICKS);
    }
}
