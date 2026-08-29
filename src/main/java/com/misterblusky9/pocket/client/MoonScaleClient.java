package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MoonScaleClient {
    private static volatile float scale = 1.0F;
    private static volatile boolean present = true;
    private static volatile boolean snapshot;

    public static float get() {
        return scale;
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
