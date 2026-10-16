package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class HelmBearingPartials {
    public static final PartialModel TOP =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/helm_bearing/top"));

    public static final PartialModel SHAFT_HALF =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/helm_bearing/shaft_half"));

    public static void init() {}

    private HelmBearingPartials() {}
}
