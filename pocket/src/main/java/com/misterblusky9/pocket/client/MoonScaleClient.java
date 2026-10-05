package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pym.api.ScaleFormat;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MoonScaleClient {
    private static volatile float scale = 1.0F;
    private static volatile boolean present = true;
    private static volatile boolean snapshot;
    private static final double STAGE_TOLERANCE = 1.0E-4D;
    private static double shown = Double.NaN;

    public static float get() {
        return scale;
    }

    public static String readout() {
        return ScaleFormat.label(readoutValue());
    }

    public static double readoutValue() {
        final double stage = CompressionStage.nearest(scale).scale();
        if (Math.abs(scale - stage) <= STAGE_TOLERANCE || Double.isNaN(shown)) shown = stage;
        return shown;
    }

    public static boolean isPresent() {
        return present;
    }

    public static boolean hasSnapshot() {
        return snapshot;
    }

    public static void clear() {
        scale = 1.0F;
        present = true;
        snapshot = false;
    }

    public static void handle(
            final MoonScaleNetwork.MoonScalePayload payload,
            final IPayloadContext context
    ) {
        scale = payload.scale();
        snapshot = true;
    }

    public static void handlePresence(
            final MoonScaleNetwork.MoonPresencePayload payload,
            final IPayloadContext context
    ) {
        present = payload.present();
        snapshot = true;
    }

    public static void handleEffect(
            final MoonScaleNetwork.MoonEffectPayload payload,
            final IPayloadContext context
    ) {
        MoonCompressionFieldRenderer.accept(payload);
    }

    private MoonScaleClient() {}
}
