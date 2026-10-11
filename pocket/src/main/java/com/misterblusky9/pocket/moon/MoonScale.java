package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "pocket")
public final class MoonScale {
    private static final String DATA_NAME = "pocket_moon_scale";
    private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load);
    private static final int TRANSITION_TICKS = 9;
    private static final float EPSILON = 1.0E-6F;

    public static float get(final MinecraftServer server) {
        return data(server).scale;
    }

    public static CompressionStage stage(final MinecraftServer server) {
        return CompressionStage.nearest(data(server).scale);
    }

    public static boolean isPresent(final MinecraftServer server) {
        return data(server).present;
    }

    public static void setPresent(final MinecraftServer server, final boolean present) {
        if (server == null) return;
        final Data data = data(server);
        if (data.present == present) return;
        data.present = present;
        data.setDirty();
        MoonScaleNetwork.broadcastPresence(present);
    }

    public static boolean isTransitioning(final MinecraftServer server) {
        return data(server).transitioning;
    }

    public static boolean set(final MinecraftServer server, final float scale) {
        if (!Float.isFinite(scale) || scale < 0.0F) return false;
        final Data data = data(server);
        data.scale = scale;
        data.transitionFrom = scale;
        data.transitionTarget = scale;
        data.transitionTicks = 0;
        data.transitioning = false;
        data.setDirty();
        MoonScaleNetwork.broadcast(scale);
        return true;
    }

    public static void transitionTo(final MinecraftServer server, final CompressionStage stage) {
        if (server == null || stage == null) return;
        final Data data = data(server);
        final float target = (float) stage.scale();
        if (data.transitioning && Math.abs(data.transitionTarget - target) <= EPSILON) return;
        if (Math.abs(data.scale - target) <= EPSILON) {
            data.scale = target;
            data.transitionFrom = target;
            data.transitionTarget = target;
            data.transitionTicks = 0;
            data.transitioning = false;
            data.setDirty();
            MoonScaleNetwork.broadcast(target);
            return;
        }
        data.transitionFrom = data.scale;
        data.transitionTarget = target;
        data.transitionTicks = 0;
        data.transitioning = true;
        data.setDirty();
    }

    static void tick(final MinecraftServer server) {
        final Data data = data(server);
        if (!data.transitioning) return;

        data.transitionTicks++;
        final float progress = Math.min(1.0F, data.transitionTicks / (float) TRANSITION_TICKS);
        final float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        data.scale = data.transitionFrom + (data.transitionTarget - data.transitionFrom) * eased;

        if (progress >= 1.0F) {
            data.scale = data.transitionTarget;
            data.transitionFrom = data.scale;
            data.transitionTicks = 0;
            data.transitioning = false;
        }

        data.setDirty();
        MoonScaleNetwork.broadcast(data.scale);
    }

    @SubscribeEvent
    public static void playerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)) return;
        final MinecraftServer server = player.serverLevel().getServer();
        MoonScaleNetwork.send(player, get(server));
        MoonScaleNetwork.sendPresence(player, isPresent(server));
    }

    private static Data data(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static final class Data extends SavedData {
        private float scale = 1.0F;
        private float transitionFrom = 1.0F;
        private float transitionTarget = 1.0F;
        private int transitionTicks;
        private boolean transitioning;
        private boolean present = true;

        private static Data load(final CompoundTag tag, final HolderLookup.Provider registries) {
            final Data data = new Data();
            if (tag.contains("Present")) data.present = tag.getBoolean("Present");
            if (!tag.contains("Scale")) return data;
            final float scale = tag.getFloat("Scale");
            if (Float.isFinite(scale) && scale >= 0.0F) {
                data.scale = scale;
                data.transitionFrom = scale;
                data.transitionTarget = scale;
            }
            return data;
        }

        @Override
        public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
            tag.putFloat("Scale", this.scale);
            tag.putBoolean("Present", this.present);
            return tag;
        }
    }

    private MoonScale() {}
}
