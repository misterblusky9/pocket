package com.misterblusky9.pocket.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

public final class PocketConfigs {
    public static void register(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, PocketServerConfig.SPEC);
        modBus.addListener(PocketServerConfig::onConfigLoad);
        modBus.addListener(PocketServerConfig::onConfigReload);
    }

    private PocketConfigs() {}
}
