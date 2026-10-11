package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class PocketedSubLevelWrenchHandler {

    private PocketedSubLevelWrenchHandler() {}

    @SubscribeEvent
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof final PackageEntity pocketed)) return;
        if (!event.getItemStack().is(Tags.Items.TOOLS_WRENCH)) return;

        final ItemStack box = pocketed.getBox();
        if (box == null || !(box.getItem() instanceof PocketCaseItem) || !PocketCaseItem.isFilled(box)) return;

        final Level level = event.getLevel();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof final ServerLevel serverLevel) || !pocketed.isAlive()) return;

        final ItemStack rotated = box.copy();
        PocketCaseItem.rotateModelQuarterTurn(rotated);
        pocketed.setBox(rotated);
        serverLevel.getChunkSource().broadcast(pocketed, new ClientboundSetEquipmentPacket(
                pocketed.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, rotated.copy()))));
        AllSoundEvents.WRENCH_ROTATE.playOnServer(
                level, pocketed.blockPosition(), 1.0F, level.random.nextFloat() + 0.5F);
    }
}
