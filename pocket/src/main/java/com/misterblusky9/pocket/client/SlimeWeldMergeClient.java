package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.SlimeWeldMerge;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.network.SlimeWeldMergePayload;
import dev.simulated_team.simulated.SimulatedClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class SlimeWeldMergeClient {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(Items.SLIME_BALL)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player || player.isShiftKeyDown()) return;
        BlockHitResult hit = event.getHitVec();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        WeldRecord weld = CrossScaleWelds.weldNear(event.getLevel(), hit.getLocation());
        if (!SlimeWeldMerge.eligible(event.getLevel(), weld)) return;

        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        PacketDistributor.sendToServer(new SlimeWeldMergePayload(weld.weldId(), event.getHand(),
                hit.getBlockPos(), local.x, local.y, local.z));
        SimulatedClient.MERGING_GLUE_ITEM_HANDLER.reset(false);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
        player.swing(event.getHand());
    }

    private SlimeWeldMergeClient() {}
}
