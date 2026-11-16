package com.misterblusky9.pocket.compat.create;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.spi.SchematicListener;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class SchematicLimits implements SchematicListener {
    private static final List<DeviceRanges.Device> CRAFT_DEVICES = List.of(
            DeviceRanges.Device.CREATIVE_SHRINK_RAY, DeviceRanges.Device.COMPRESSION_GUN, DeviceRanges.Device.COMPRESSORS);

    @Override
    public boolean permits(ServerLevel level, double scale) {
        double min = Double.MAX_VALUE;
        double max = 0.0D;
        for (DeviceRanges.Device device : CRAFT_DEVICES) {
            ScaleBounds bounds = DeviceRanges.of(device);
            min = Math.min(min, bounds.min());
            max = Math.max(max, bounds.max());
        }
        return new ScaleBounds(min, max).contains(scale);
    }
}
