package com.misterblusky9.pym.internal.client;

import com.misterblusky9.pym.api.client.DebugOverlay;
import com.misterblusky9.pym.api.client.RenderFrame;
import com.misterblusky9.pym.internal.PymMod;
import com.misterblusky9.pym.internal.compat.create.ContraptionColliderOutlines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(value = PymMod.MOD_ID, dist = Dist.CLIENT)
public final class PymClient {
    public PymClient(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ScaleHandshake.tick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> {
            RenderFrame.begin();
            SubLevelBlockEntityPass.reset();
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) RenderFrame.capture(event.getFrustum());
        });
        NeoForge.EVENT_BUS.addListener(ColliderOutlines::render);
        if (ModList.get().isLoaded("create")) ContraptionColliderOutlines.register();
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!event.getLevel().isClientSide()) return;
            ScaleHandshake.clear();
            DebugOverlay.setEnabled(false);
        });
    }
}
