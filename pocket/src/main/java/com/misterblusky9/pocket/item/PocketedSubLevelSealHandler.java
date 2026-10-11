package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class PocketedSubLevelSealHandler {

    private PocketedSubLevelSealHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof final PackageEntity pocketed)) return;

        final ItemStack box = pocketed.getBox();
        if (box == null || !PocketCaseItem.isSealable(box)) return;

        final ItemStack held = event.getItemStack();
        final PocketSeal glue = PocketSeal.appliedBy(held);
        final boolean knife = held.is(ModItems.POCKET_KNIFE.get());
        if (glue != null && PocketCaseItem.isSealed(box)) return;
        if (glue == null && !(knife && PocketCaseItem.isSealed(box))) return;

        final Level level = event.getLevel();
        event.setCanceled(true);
        if (glue == null && !PocketCaseItem.caseSeal(box).canCut(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(glue == PocketSeal.HOT_GLUE
                ? InteractionResult.CONSUME
                : InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof final ServerLevel serverLevel) || !pocketed.isAlive()) return;

        if (glue != null) {
            seal(serverLevel, pocketed, box, glue, event.getEntity(), held, event);
        } else {
            cut(serverLevel, pocketed, box, event.getEntity(), held, event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(final AttackEntityEvent event) {
        if (!(event.getTarget() instanceof final PackageEntity pocketed)) return;

        final ItemStack held = event.getEntity().getMainHandItem();
        if (PocketSeal.appliedBy(held) == null && !held.is(ModItems.POCKET_KNIFE.get())) return;

        final ItemStack box = pocketed.getBox();
        if (box == null || !PocketCaseItem.isSealable(box)) return;

        event.setCanceled(true);
        if (!held.is(ModItems.GLUE_GUN.get()) || !PocketCaseItem.isSealed(box)
                || !PocketCaseItem.caseSeal(box).canCut(event.getEntity())) return;
        if (!(event.getEntity().level() instanceof final ServerLevel serverLevel) || !pocketed.isAlive()) return;

        cut(serverLevel, pocketed, box, event.getEntity(), held, InteractionHand.MAIN_HAND);
    }

    private static void seal(
            final ServerLevel level,
            final PackageEntity pocketed,
            final ItemStack box,
            final PocketSeal glue,
            final Player player,
            final ItemStack held,
            final PlayerInteractEvent.EntityInteract event
    ) {
        final ItemStack sealed = box.copy();
        PocketCaseItem.setSeal(sealed, CaseSeal.by(player, glue));
        replaceBox(level, pocketed, sealed);

        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
        glueEffect(level, pocketed, glue);
        switch (glue) {
            case HONEY_GLUE -> level.playSound(null, pocketed.blockPosition(),
                    SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
            default -> AllSoundEvents.SLIME_ADDED.playOnServer(level, pocketed.blockPosition());
        }
    }

    private static void cut(
            final ServerLevel level,
            final PackageEntity pocketed,
            final ItemStack box,
            final Player player,
            final ItemStack knife,
            final InteractionHand hand
    ) {
        final PocketSeal glue = PocketCaseItem.seal(box);
        final ItemStack opened = box.copy();
        PocketCaseItem.setSeal(opened, null);
        replaceBox(level, pocketed, opened);

        knife.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        if (glue != null) glueEffect(level, pocketed, glue);
        level.playSound(null, pocketed.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    static void replaceBox(final ServerLevel level, final PackageEntity pocketed, final ItemStack box) {
        pocketed.setBox(box);
        level.getChunkSource().broadcast(pocketed, new ClientboundSetEquipmentPacket(
                pocketed.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, box.copy()))));
    }

    private static void glueEffect(final ServerLevel level, final PackageEntity pocketed, final PocketSeal glue) {
        final AABB bounds = pocketed.getBoundingBox();
        final Vec3 centre = bounds.getCenter();
        final double dx = bounds.getXsize() * 0.4D;
        final double dy = bounds.getYsize() * 0.4D;
        final double dz = bounds.getZsize() * 0.4D;

        switch (glue) {
            case SUPER_GLUE -> level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)),
                    centre.x, centre.y, centre.z, 24, dx, dy, dz, 0.05D);
            case HONEY_GLUE -> {
                level.sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SimItems.HONEY_GLUE.get())),
                        centre.x, centre.y, centre.z, 16, dx, dy, dz, 0.05D);
                level.sendParticles(ParticleTypes.FALLING_HONEY,
                        centre.x, centre.y, centre.z, 8, dx, dy, dz, 0.0D);
            }
            case HOT_GLUE -> level.levelEvent(
                    LevelEvent.PARTICLES_DESTROY_BLOCK,
                    pocketed.blockPosition(),
                    Block.getId(SimBlocks.MERGING_GLUE.getDefaultState()
                            .setValue(MergingGlueBlock.FACING, Direction.UP)));
        }
    }
}
