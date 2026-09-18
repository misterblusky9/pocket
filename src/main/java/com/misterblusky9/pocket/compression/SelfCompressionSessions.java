package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.Overclocking;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.network.SelfCompressionEffectPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleLimits;
import com.misterblusky9.pocket.scale.ScaleController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public final class SelfCompressionSessions {
    private static final String STAGE_KEY = "PocketPersonalScaleDepth";
    private static final String SCALE_KEY = "PocketPersonalScale";

    private SelfCompressionSessions() {}

    public static void instant(final ServerPlayer player, final double scale) {
        if (player == null || !PocketSized.isValidScale(scale) || !PehkuiScaleBridge.isOperational()) return;
        final boolean growing = scale > currentScale(player);
        SelfCompressionEffectPayload.sendBegin(player, growing);
        SelfCompressionEffectPayload.sendPulse(player, growing);
        applyScale(player, scale);
        SelfCompressionEffectPayload.sendRelease(player);
    }

    public static void restore(final ServerPlayer player) {
        final double scale = currentScale(player);
        if (!ScaleController.sameScale(scale, PocketSized.FULL_SCALE)) PehkuiScaleBridge.snapPersonalScale(player, scale);
    }

    public static double currentScale(final ServerPlayer player) {
        if (player == null) return PocketSized.FULL_SCALE;
        final CompoundTag data = player.getPersistentData();
        if (data.contains(SCALE_KEY, Tag.TAG_ANY_NUMERIC) && PocketSized.isValidScale(data.getDouble(SCALE_KEY))) {
            return clamp(player, CompressionStage.snap(data.getDouble(SCALE_KEY)));
        }
        return CompressionStage.fromDepth(data.getInt(STAGE_KEY)).scale();
    }

    private static double clamp(final ServerPlayer player, final double scale) {
        return Overclocking.clamp(player, scale, ScaleLimits.CREATIVE);
    }

    private static void applyScale(final ServerPlayer player, final double scale) {
        final double clamped = clamp(player, CompressionStage.snap(scale));
        final CompoundTag data = player.getPersistentData();
        data.putDouble(SCALE_KEY, clamped);
        data.putInt(STAGE_KEY, CompressionStage.nearest(clamped).depth());
        PehkuiScaleBridge.setPersonalScale(player, clamped);
    }
}
