package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class CopycatFacadePartials {
    public static final PartialModel BASE =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/copycat_facade"));

    public static void init() {}

    private CopycatFacadePartials() {}
}
