package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.block.StaticSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.network.PersonalLockPayload;
import com.misterblusky9.pocket.scale.ResizeActor;
import com.misterblusky9.pym.api.ScaleDriver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PersonalLock {
    private static final String KEY = "PocketPersonalUnlocked";

    private static boolean clientUnlocked;

    private PersonalLock() {}

    public static boolean unlocked(final Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) return clientUnlocked;

        final DeviceRanges.PvpScalingMode mode = DeviceRanges.pvpScalingMode();
        if (!mode.personal()) return mode.defaultAllowed();
        return player.getPersistentData().contains(KEY)
                ? player.getPersistentData().getBoolean(KEY)
                : mode.defaultAllowed();
    }

    public static boolean canToggle() {
        return DeviceRanges.pvpScalingMode().personal() && DeviceRanges.allowPlayersToTogglePvpScaling();
    }

    public static void set(final ServerPlayer player, final boolean unlocked) {
        if (player == null) return;
        if (canToggle()) player.getPersistentData().putBoolean(KEY, unlocked);
        sync(player);
    }

    public static void sync(final ServerPlayer player) {
        if (player != null) PacketDistributor.sendToPlayer(player, new PersonalLockPayload(unlocked(player)));
    }

    public static void copy(final ServerPlayer from, final ServerPlayer to) {
        if (from == null || to == null) return;
        if (from.getPersistentData().contains(KEY)) {
            to.getPersistentData().putBoolean(KEY, from.getPersistentData().getBoolean(KEY));
        } else {
            to.getPersistentData().remove(KEY);
        }
    }

    public static void setClient(final boolean unlocked) {
        clientUnlocked = unlocked;
    }

    public static boolean protects(final Player player) {
        final DeviceRanges.PvpScalingMode mode = DeviceRanges.pvpScalingMode();
        if (mode == DeviceRanges.PvpScalingMode.ON) return false;
        if (mode == DeviceRanges.PvpScalingMode.OFF) return true;
        return !unlocked(player);
    }

    public static boolean blocks(final Player actor, final Player target) {
        if (actor == null || target == null || actor == target) return false;
        if (actor.isCreative() && actor.hasPermissions(2)) return false;
        return protects(target);
    }

    public static boolean blocksResize(final ScaleDriver driver, final Player target) {
        final Player actor = ResizeActor.current();
        if (actor != null) return blocks(actor, target);
        final boolean compressor = driver instanceof StaticSubspaceCompressorBlockEntity
                || driver instanceof PortableSubspaceCompressorBlockEntity;
        return compressor && target != null && protects(target);
    }
}
