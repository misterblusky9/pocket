package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CompressionGunTank {
    public static final int CAPACITY = 3000;

    private static final ResourceLocation LEVITITE_BLEND =
            ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite_blend");

    // Empty-tank backup wear: 59 usable durability x 7 ticks = ~20 s of firing.
    private static final int BACKUP_WEAR_INTERVAL_TICKS = 7;

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> CONTENTS =
            COMPONENTS.registerComponentType("levitite_tank", builder -> builder
                    .persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private CompressionGunTank() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new Handler(stack, CAPACITY),
                ModItems.COMPRESSION_GUN.get(),
                ModItems.PEARLESCENT_COMPRESSION_GUN.get());
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new Handler(stack, SelfResizeDeviceItem.CAPACITY),
                ModItems.SELF_RESIZE_DEVICE.get());
    }

    public static Fluid levitite() {
        return BuiltInRegistries.FLUID.get(LEVITITE_BLEND);
    }

    public static boolean isLevitite(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return false;
        Fluid levitite = levitite();
        return levitite != Fluids.EMPTY && fluid.getFluid().isSame(levitite);
    }

    public static FluidStack levititeStack(int amount) {
        return new FluidStack(levitite(), Math.max(1, amount));
    }

    public static int amount(ItemStack stack) {
        FluidStack fluid = stack.getOrDefault(CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        return isLevitite(fluid) ? fluid.getAmount() : 0;
    }

    public static ItemStack filled(ItemStack stack) {
        return filled(stack, CAPACITY);
    }

    public static ItemStack filled(ItemStack stack, int capacity) {
        stack.set(CONTENTS.get(), SimpleFluidContent.copyOf(levititeStack(capacity)));
        return stack;
    }

    public static int drain(ItemStack stack, int requested) {
        if (requested <= 0) return 0;
        int held = amount(stack);
        int drained = Math.min(held, requested);
        if (drained <= 0) return 0;

        int left = held - drained;
        if (left <= 0) stack.remove(CONTENTS.get());
        else stack.set(CONTENTS.get(), SimpleFluidContent.copyOf(levititeStack(left)));
        return drained;
    }

    /** Levitite is the primary fuel; tool durability is the fallback, never a concurrent cost. */
    public static boolean hasPower(Player player, ItemStack stack) {
        return player != null && (player.isCreative() || amount(stack) > 0 || hasDurability(stack));
    }

    public static boolean hasDurability(ItemStack stack) {
        // Preserve the gun's existing 1-durability safety margin for backup operation.
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    public static boolean canFire(Player player, ItemStack stack) {
        return hasPower(player, stack);
    }

    public static boolean runEngine(
            ServerPlayer player,
            ItemStack stack,
            InteractionHand hand,
            int elapsed
    ) {
        if (player.isCreative() || amount(stack) > 0) {
            return true;
        }

        if (!hasDurability(stack)) return false;
        if (elapsed <= 0 || elapsed % BACKUP_WEAR_INTERVAL_TICKS != 0) return true;

        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return true;
    }

    /**
     * Legacy one-shot durability backup; intentionally cannot wear a fuelled item.
     * Kept for callers that used this public helper before the levitite migration.
     */
    public static boolean runEngineOnce(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (player.isCreative() || amount(stack) > 0) return true;
        if (!hasDurability(stack)) return false;
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return true;
    }

    /** Legacy backup helper; see {@link #consumeForActivation} for levitite-first activation. */
    public static boolean runEngineOnce(ServerPlayer player, ItemStack stack, int durabilityCost) {
        if (player.isCreative() || amount(stack) > 0) return true;
        if (durabilityCost <= 0) return true;
        if (!canAffordDurability(stack, durabilityCost)) return false;
        stack.setDamageValue(stack.getDamageValue() + durabilityCost);
        return true;
    }

    /**
     * Exactly one resource pays per activation. Even the last partial dose of levitite
     * is a levitite-only use; durability is charged only when the tank began empty.
     * Rejected backup uses do not alter the item.
     */
    public static boolean consumeForActivation(
            ServerPlayer player, ItemStack stack, int levititeCost, int fullDurabilityCost
    ) {
        if (player.isCreative()) return true;
        LevititeUseCost.Charge charge = LevititeUseCost.plan(
                amount(stack), levititeCost, fullDurabilityCost);

        if (charge.durability() > 0 && !canAffordDurability(stack, charge.durability())) return false;

        if (charge.levitite() > 0) drain(stack, charge.levitite());
        else if (charge.durability() > 0) {
            stack.setDamageValue(stack.getDamageValue() + charge.durability());
        }
        return true;
    }

    private static boolean canAffordDurability(ItemStack stack, int durabilityCost) {
        return stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() >= durabilityCost;
    }

    public static void stopEngine(Player player) {
        // Retained for binary/source compatibility; no per-player engine debt remains.
    }

    private static final class Handler extends FluidHandlerItemStack {
        private Handler(ItemStack container, int capacity) {
            super(CONTENTS, container, capacity);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return isLevitite(stack);
        }

        @Override
        public boolean canFillFluidType(FluidStack fluid) {
            return isLevitite(fluid);
        }
    }
}
