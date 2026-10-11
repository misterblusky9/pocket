package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class HotGluePunchHandler {
    private static final long AIM_TICKS = 5L;

    private record Aim(BlockPos pos, Vec3 point, long tick) {}

    private static final Map<UUID, Aim> AIMED = new HashMap<>();

    public static void aim(final ServerPlayer player, final BlockPos pos, final Vec3 point) {
        AIMED.put(player.getUUID(), new Aim(pos.immutable(), point, player.serverLevel().getGameTime()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        final Player player = event.getEntity();
        final ItemStack gun = player.getMainHandItem();
        if (!gun.is(ModItems.GLUE_GUN.get())) return;

        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getLevel() instanceof final ServerLevel level)) return;

        final Aim aim = AIMED.remove(player.getUUID());
        if (aim == null || !aim.pos().equals(event.getPos())
                || level.getGameTime() - aim.tick() > AIM_TICKS
                || !player.canInteractWithBlock(event.getPos(), 1.0D)) return;

        PocketKnifeItem.cutWeld(level, aim.point(), event.getPos(), player, gun, InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        AIMED.remove(event.getEntity().getUUID());
    }

    private HotGluePunchHandler() {}
}
