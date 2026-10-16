package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class SwitchBearingPartials {
    public static final PartialModel TOP =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/switch_bearing/top"));

    public static final PartialModel SHAFT_HALF =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/switch_bearing/shaft_half"));

    public static void init() {}

    private SwitchBearingPartials() {}
}
