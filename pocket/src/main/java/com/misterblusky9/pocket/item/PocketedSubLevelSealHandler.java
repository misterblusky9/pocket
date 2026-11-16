package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.HatSeals;
import com.misterblusky9.pocket.particle.ModParticles;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.jetbrains.annotations.Nullable;

import java.util.List;

@EventBusSubscriber(modid = PocketSized.MOD_ID)
public final class PocketedSubLevelSealHandler {

    private PocketedSubLevelSealHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PackageEntity pocketed)) return;

        ItemStack box = pocketed.getBox();
        if (box == null || !PocketCaseItem.isSealable(box)) return;

        ItemStack held = event.getItemStack();
        PocketSeal glue = PocketSeal.appliedBy(held);
        boolean knife = held.is(ModItems.POCKET_KNIFE.get());
        if (glue != null && PocketCaseItem.isSealed(box)) return;
        if (glue == null && !(knife && PocketCaseItem.isSealed(box))) return;

        Level level = event.getLevel();
        event.setCanceled(true);
        if (glue == null && !PocketCaseItem.caseSeal(box).canCut(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(glue == PocketSeal.HOT_GLUE
                ? InteractionResult.CONSUME
                : InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof ServerLevel serverLevel) || !pocketed.isAlive()) return;

        if (glue != null) {
            seal(serverLevel, pocketed, box, glue, event.getEntity(), held, event);
        } else {
            cut(serverLevel, pocketed, box, event.getEntity(), held, event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof PackageEntity pocketed)) return;

        ItemStack held = event.getEntity().getMainHandItem();
        if (PocketSeal.appliedBy(held) == null && !held.is(ModItems.POCKET_KNIFE.get())) return;

        ItemStack box = pocketed.getBox();
        if (box == null || !PocketCaseItem.isSealable(box)) return;

        event.setCanceled(true);
        if (!held.is(ModItems.GLUE_GUN.get()) || !PocketCaseItem.isSealed(box)
                || !PocketCaseItem.caseSeal(box).canCut(event.getEntity())) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel) || !pocketed.isAlive()) return;

        cut(serverLevel, pocketed, box, event.getEntity(), held, InteractionHand.MAIN_HAND);
    }

    private static void seal(
            ServerLevel level,
            PackageEntity pocketed,
            ItemStack box,
            PocketSeal glue,
            Player player,
            ItemStack held,
            PlayerInteractEvent.EntityInteract event
    ) {
        ItemStack sealed = box.copy();
        PocketCaseItem.setSeal(sealed, CaseSeal.by(player, glue));
        replaceBox(level, pocketed, sealed);

        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
        sealEffect(level, pocketed.getBoundingBox(), pocketed.blockPosition(), glue);
    }

    private static void sealEffect(ServerLevel level, AABB bounds, BlockPos at, PocketSeal glue) {
        glueEffect(level, bounds, glue);
        switch (glue) {
            case HONEY_GLUE -> level.playSound(null, at, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
            default -> AllSoundEvents.SLIME_ADDED.playOnServer(level, at);
        }
    }

    private static void cutEffect(ServerLevel level, AABB bounds, BlockPos at, @Nullable PocketSeal glue) {
        if (glue != null) glueEffect(level, bounds, glue);
        level.playSound(null, at, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    private static void cut(
            ServerLevel level,
            PackageEntity pocketed,
            ItemStack box,
            Player player,
            ItemStack knife,
            InteractionHand hand
    ) {
        PocketSeal glue = PocketCaseItem.seal(box);
        ItemStack opened = box.copy();
        PocketCaseItem.setSeal(opened, null);
        replaceBox(level, pocketed, opened);

        knife.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        cutEffect(level, pocketed.getBoundingBox(), pocketed.blockPosition(), glue);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickStand(PlayerInteractEvent.RightClickBlock event) {
        HatStandBlockEntity stand = HatSeals.base(event.getLevel(), event.getHitVec());
        if (stand == null) return;
        ItemStack hat = stand.getActivator();
        ItemStack held = event.getItemStack();
        PocketSeal glue = PocketSeal.appliedBy(held);
        boolean knife = held.is(ModItems.POCKET_KNIFE.get());
        if (glue != null && PocketCaseItem.isSealed(hat)) return;
        if (glue == null && !(knife && PocketCaseItem.isSealed(hat))) return;

        Player player = event.getEntity();
        event.setCanceled(true);
        if (glue == null && !PocketCaseItem.caseSeal(hat).canCut(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(glue == PocketSeal.HOT_GLUE
                ? InteractionResult.CONSUME
                : InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        PocketSeal used = glue != null ? glue : PocketCaseItem.seal(hat);
        stand.seal(glue == null ? null : CaseSeal.by(player, glue));
        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
        AABB base = baseBox(stand);
        if (glue != null) sealEffect(level, base, stand.getBlockPos(), glue);
        else cutEffect(level, base, stand.getBlockPos(), used);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickStand(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (PocketSeal.appliedBy(held) == null && !held.is(ModItems.POCKET_KNIFE.get())) return;
        if (!(player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(event.getPos())) return;
        HatStandBlockEntity stand = HatSeals.base(event.getLevel(), hit);
        if (stand == null) return;

        event.setCanceled(true);
        ItemStack hat = stand.getActivator();
        if (!held.is(ModItems.GLUE_GUN.get()) || !PocketCaseItem.isSealed(hat)
                || !PocketCaseItem.caseSeal(hat).canCut(player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        PocketSeal glue = PocketCaseItem.seal(hat);
        stand.seal(null);
        held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        cutEffect(level, baseBox(stand), stand.getBlockPos(), glue);
    }

    public static AABB baseBox(HatStandBlockEntity stand) {
        return new AABB(2.0D / 16.0D, 0.0D, 2.0D / 16.0D, 14.0D / 16.0D, 1.0D / 16.0D, 14.0D / 16.0D)
                .move(stand.getBlockPos());
    }

    static void replaceBox(ServerLevel level, PackageEntity pocketed, ItemStack box) {
        pocketed.setBox(box);
        level.getChunkSource().broadcast(pocketed, new ClientboundSetEquipmentPacket(
                pocketed.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, box.copy()))));
    }

    private static void glueEffect(ServerLevel level, AABB bounds, PocketSeal glue) {
        Vec3 centre = bounds.getCenter();
        double dx = bounds.getXsize() * 0.4D;
        double dy = bounds.getYsize() * 0.4D;
        double dz = bounds.getZsize() * 0.4D;

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
            case HOT_GLUE -> level.sendParticles(
                    ModParticles.HOT_GLUE.get(),
                    centre.x, centre.y, centre.z, 24, dx, dy, dz, 0.1D);
        }
    }
}
