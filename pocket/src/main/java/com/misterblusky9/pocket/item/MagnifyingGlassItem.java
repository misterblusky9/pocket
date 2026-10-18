package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.MagnifyingGlassArmPose;
import com.misterblusky9.pocket.client.MagnifyingGlassZoom;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.function.Consumer;

public class MagnifyingGlassItem extends Item {
    public MagnifyingGlassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) return InteractionResultHolder.consume(stack);
        MagnifyingGlassZoom.use(player);
        return InteractionResultHolder.fail(stack);
    }

    public static boolean isRaised(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MAGNIFYING_GLASS_RAISED.get(), false);
    }

    public static void setRaised(Player player, boolean raised) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        lower(main);
        lower(off);
        if (raised) {
            ItemStack held = main.getItem() instanceof MagnifyingGlassItem ? main : off;
            if (held.getItem() instanceof MagnifyingGlassItem) {
                held.set(ModDataComponents.MAGNIFYING_GLASS_RAISED.get(), true);
            }
        }
        broadcastHands(player);
    }

    private static void broadcastHands(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.getChunkSource().broadcast(entity, new ClientboundSetEquipmentPacket(entity.getId(), List.of(
                Pair.of(EquipmentSlot.MAINHAND, entity.getMainHandItem().copy()),
                Pair.of(EquipmentSlot.OFFHAND, entity.getOffhandItem().copy()))));
    }

    private static void lower(ItemStack stack) {
        if (stack.getItem() instanceof MagnifyingGlassItem) stack.remove(ModDataComponents.MAGNIFYING_GLASS_RAISED.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !isRaised(stack)) return;
        boolean inHand = selected
                || entity instanceof LivingEntity living && living.getOffhandItem() == stack;
        if (!inHand) {
            stack.remove(ModDataComponents.MAGNIFYING_GLASS_RAISED.get());
            if (entity instanceof LivingEntity living) broadcastHands(living);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack stack, LocalPlayer player,
                                                   HumanoidArm arm, ItemStack item,
                                                   float partialTick, float equip, float swing) {
                return MagnifyingGlassZoom.applyHandTransform(stack, player, arm, item, partialTick, equip, swing);
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return (isRaised(stack) || MagnifyingGlassZoom.raisedLocally(entity, stack))
                        && !(entity instanceof Player player && player.swinging)
                        ? MagnifyingGlassArmPose.POINTING.getValue()
                        : null;
            }
        });
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof MagnifyingGlassItem
                || player.getOffhandItem().getItem() instanceof MagnifyingGlassItem;
    }
}
