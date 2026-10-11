package com.misterblusky9.pocket.config;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class DeviceRanges {
    public enum PvpScalingMode {
        ON,
        OFF,
        PERSONAL_ON,
        PERSONAL_OFF;

        public boolean personal() {
            return this == PERSONAL_ON || this == PERSONAL_OFF;
        }

        public boolean defaultAllowed() {
            return this == ON || this == PERSONAL_ON;
        }
    }

    public enum Device {
        CREATIVE_SHRINK_RAY("creativeShrinkRay", "Creative Shrinkray", new ScaleBounds(1.0D / 32.0D, ScaleBounds.SAFE.max())),
        COMPRESSION_GUN("compressionGun", "Compression guns", new ScaleBounds(1.0D / 32.0D, ScaleBounds.SAFE.max())),
        PERSONAL_COMPRESSOR("personalCompressor", "Personal Subspace Compressor", new ScaleBounds(1.0D / 32.0D, 16.0D)),
        COMPRESSORS("compressors", "Static and Portable Subspace Compressors", ScaleBounds.SAFE);

        private final String key;
        private final String label;
        private final ScaleBounds defaults;

        Device(final String key, final String label, final ScaleBounds defaults) {
            this.key = key;
            this.label = label;
            this.defaults = defaults;
        }
    }

    private static final double CONFIG_FLOOR = 1.0E-6D;
    private static final double CONFIG_CEILING = 1.0E6D;

    public static final ModConfigSpec SPEC;
    private static final Map<Device, ModConfigSpec.DoubleValue> MIN = new EnumMap<>(Device.class);
    private static final Map<Device, ModConfigSpec.DoubleValue> MAX = new EnumMap<>(Device.class);
    private static final ModConfigSpec.EnumValue<PvpScalingMode> PVP_SCALING_MODE;
    private static final ModConfigSpec.BooleanValue ALLOW_PVP_SCALING_TOGGLE;
    private static final ModConfigSpec.BooleanValue RESIZE_ENTITIES_ON_CRAFTS;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "The smallest and largest scale each device can reach.",
                "Defaults are Pym's well-tested band, 1/16 (0.0625) to 8, except the Creative Shrinkray and compression guns which reach 1/32 (0.03125)",
                "and the Personal Subspace Compressor which reaches 1/32 (0.03125) to 16.",
                "Wider values are allowed but less tested.").push("ranges");
        for (final Device device : Device.values()) {
            builder.push(device.key);
            MIN.put(device, builder.comment(device.label + ": smallest scale.")
                    .defineInRange("min", device.defaults.min(), CONFIG_FLOOR, CONFIG_CEILING));
            MAX.put(device, builder.comment(device.label + ": largest scale.")
                    .defineInRange("max", device.defaults.max(), CONFIG_FLOOR, CONFIG_CEILING));
            builder.pop();
        }
        builder.pop();

        builder.comment(
                "Controls whether players may resize other players.",
                "ON: always allowed; OFF: always blocked; PERSONAL_ON/OFF: PSC owners choose, using that default.")
                .push("pvpScaling");
        PVP_SCALING_MODE = builder.defineEnum("mode", PvpScalingMode.PERSONAL_ON);
        ALLOW_PVP_SCALING_TOGGLE = builder.comment(
                "Whether personal-mode players may change their PVP Scaling preference from the PSC menu.")
                .define("allowPlayersToToggleInPsc", true);
        builder.pop();

        RESIZE_ENTITIES_ON_CRAFTS = builder.comment(
                "Whether entities standing on contraptions resize with them.",
                "Powered compressors include standing players automatically; handheld tools require Ctrl.",
                "Riders in Create seats always follow.")
                .define("resizeEntitiesOnCrafts", true);
        SPEC = builder.build();
    }

    public static void register(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public static PvpScalingMode pvpScalingMode() {
        return SPEC.isLoaded() ? PVP_SCALING_MODE.get() : PvpScalingMode.PERSONAL_ON;
    }

    public static boolean allowPlayersToTogglePvpScaling() {
        return !SPEC.isLoaded() || ALLOW_PVP_SCALING_TOGGLE.get();
    }

    public static boolean resizeEntitiesOnCrafts() {
        return !SPEC.isLoaded() || RESIZE_ENTITIES_ON_CRAFTS.get();
    }

    public static ScaleBounds of(final Device device) {
        if (!SPEC.isLoaded()) return device.defaults;
        final ScaleBounds reach = Pym.resize().reachable();
        final double min = reach.clamp(MIN.get(device).get());
        final double max = reach.clamp(MAX.get(device).get());
        return new ScaleBounds(Math.min(min, max), Math.max(min, max));
    }

    private DeviceRanges() {}
}
