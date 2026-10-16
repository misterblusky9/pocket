package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class PocketedSubLevelBucketHandler {

    private PocketedSubLevelBucketHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PackageEntity pocketed)) return;
        if (!event.getItemStack().is(Items.BUCKET)) return;

        ItemStack box = pocketed.getBox();
        if (box == null || !(box.getItem() instanceof PocketCaseItem)
                || !PocketCaseItem.isFilled(box) || PocketCaseItem.isSealed(box)) return;

        Level level = event.getLevel();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof ServerLevel serverLevel) || !pocketed.isAlive()) return;

        PocketCaseItem.Evaporated evaporated = PocketCaseItem.evaporateStoredPayload(serverLevel, box);
        if (evaporated == null) return;
        PocketedSubLevelSealHandler.replaceBox(serverLevel, pocketed, evaporated.stack());

        Player player = event.getEntity();
        Vec3 centre = pocketed.getBoundingBox().getCenter();
        if (evaporated.water()) {
            player.setItemInHand(event.getHand(),
                    ItemUtils.createFilledResult(event.getItemStack(), player, new ItemStack(Items.WATER_BUCKET)));
            serverLevel.playSound(null, centre.x, centre.y, centre.z,
                    SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.gameEvent(player, GameEvent.FLUID_PICKUP, centre);
        } else {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 8, 0.2D, 0.2D, 0.2D, 0.0D);
            serverLevel.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    0.5F, 2.6F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.8F);
        }
    }
}
