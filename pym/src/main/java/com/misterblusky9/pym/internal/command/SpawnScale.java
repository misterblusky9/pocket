package com.misterblusky9.pym.internal.command;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.PymMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = PymMod.MOD_ID)
public final class SpawnScale {
    private static final String DATA_NAME = "pym_spawn_scale";
    private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load);

    public static double get(MinecraftServer server) {
        return data(server).scale;
    }

    public static void set(MinecraftServer server, double scale) {
        Data data = data(server);
        data.scale = scale;
        data.setDirty();
    }

    @SubscribeEvent
    public static void firstSpawn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) != 0) return;
        apply(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        apply(player);
    }

    private static void apply(ServerPlayer player) {
        double scale = get(player.server);
        if (!(scale > 0.0D) || !Double.isFinite(scale)) return;
        if (!Pym.entities().supports(player)) return;
        Pym.entities().set(player, scale, 0);
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static final class Data extends SavedData {
        private double scale;

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            double scale = tag.getDouble("Scale");
            if (scale > 0.0D && Double.isFinite(scale)) data.scale = scale;
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (this.scale > 0.0D) tag.putDouble("Scale", this.scale);
            return tag;
        }
    }

    private SpawnScale() {}
}
