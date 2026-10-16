package com.misterblusky9.pym.internal;

import com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScaling;
import com.misterblusky9.pym.internal.compat.simulated.SimulatedCompat;
import com.misterblusky9.pym.internal.compat.simurail.SimurailCompat;
import com.misterblusky9.pym.internal.config.PymConfig;
import com.misterblusky9.pym.internal.network.ScaleNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(PymMod.MOD_ID)
public final class PymMod {
    public static final String MOD_ID = "pym";

    public PymMod(IEventBus modBus, ModContainer modContainer) {
        PymConfig.register(modBus, modContainer);
        modBus.addListener(ScaleNetwork::register);

        PehkuiEntityScaling.initialize();
        if (ModList.get().isLoaded("simulated")) SimulatedCompat.init();
        if (ModList.get().isLoaded("simurail")) SimurailCompat.init();
    }
}
