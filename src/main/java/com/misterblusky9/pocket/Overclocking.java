package com.misterblusky9.pocket;

import com.misterblusky9.pocket.network.OverclockingSyncPayload;
import com.misterblusky9.pocket.scale.ScaleLadder;
import com.misterblusky9.pocket.scale.ScaleLimits;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class Overclocking {
    private static final String KEY = "PocketOverclocking";
    private static boolean clientEnabled;

    public static boolean enabled(final Player player) {
        if (player == null) return false;
        return player.level().isClientSide ? clientEnabled : player.getPersistentData().getBoolean(KEY);
    }

    public static ScaleLimits limits(final Player player, final ScaleLimits base) {
        return enabled(player) ? ScaleLimits.EXPERIMENTAL : base;
    }

    public static double[] ladder(final Player player, final double[] base) {
        return enabled(player) ? ScaleLadder.EXPERIMENTAL : base;
    }

    public static double clamp(final Player player, final double scale, final ScaleLimits base) {
        return limits(player, base).clamp(scale);
    }

    private static void set(final ServerPlayer player, final boolean enabled) {
        player.getPersistentData().putBoolean(KEY, enabled);
        PacketDistributor.sendToPlayer(player, new OverclockingSyncPayload(enabled));
    }

    public static void acceptClient(final boolean enabled) {
        clientEnabled = enabled;
    }

    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pocket")
                        .then(Commands.literal("overclocking")
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("on")
                                        .executes(context -> {
                                            set(context.getSource().getPlayerOrException(), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("off")
                                        .executes(context -> {
                                            set(context.getSource().getPlayerOrException(), false);
                                            return 1;
                                        })))
        );
    }

    @SubscribeEvent
    public static void loggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new OverclockingSyncPayload(enabled(player)));
        }
    }

    @SubscribeEvent
    public static void cloned(final PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof final ServerPlayer original)
                || !(event.getEntity() instanceof final ServerPlayer player)) return;
        set(player, enabled(original));
    }

    private Overclocking() {}
}
