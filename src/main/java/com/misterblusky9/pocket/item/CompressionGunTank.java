package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionGunTank {
    public static final int CAPACITY = 3000;

    private static final ResourceLocation LEVITITE_BLEND =
            ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite_blend");

    private static final int BACKTANK_DRAW_TICKS = 40;
    private static final int BACKTANK_AIR_PER_DRAW = 3;
    private static final int ENGINE_TICKS_PER_AIR = 4;
    private static final int AIR_PER_DURABILITY = 12;

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> CONTENTS =
            COMPONENTS.registerComponentType("levitite_tank", builder -> builder
                    .persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private static final Map<UUID, Integer> DURABILITY_DEBT = new ConcurrentHashMap<>();

    private CompressionGunTank() {}

    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new Handler(stack),
                ModItems.COMPRESSION_GUN.get(),
                ModItems.PEARLESCENT_COMPRESSION_GUN.get());
    }

    public static Fluid levitite() {
        return BuiltInRegistries.FLUID.get(LEVITITE_BLEND);
    }

    public static boolean isLevitite(final FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return false;
        final Fluid levitite = levitite();
        return levitite != Fluids.EMPTY && fluid.getFluid().isSame(levitite);
    }

    public static FluidStack levititeStack(final int amount) {
        return new FluidStack(levitite(), Math.max(1, amount));
    }

    public static int amount(final ItemStack stack) {
        final FluidStack fluid = stack.getOrDefault(CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        return isLevitite(fluid) ? fluid.getAmount() : 0;
    }

    public static ItemStack filled(final ItemStack stack) {
        stack.set(CONTENTS.get(), SimpleFluidContent.copyOf(levititeStack(CAPACITY)));
        return stack;
    }

    public static int drain(final ItemStack stack, final int requested) {
        if (requested <= 0) return 0;
        final int held = amount(stack);
        final int drained = Math.min(held, requested);
        if (drained <= 0) return 0;

        final int left = held - drained;
        if (left <= 0) stack.remove(CONTENTS.get());
        else stack.set(CONTENTS.get(), SimpleFluidContent.copyOf(levititeStack(left)));
        return drained;
    }

    public static boolean hasAirPressure(final Player player) {
        return player != null && (player.isCreative() || !BacktankUtil.getAllWithAir(player).isEmpty());
    }

    public static boolean hasPower(final Player player, final ItemStack stack) {
        if (hasAirPressure(player)) return true;
        return amount(stack) > 0
                && stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    public static boolean canFire(final Player player, final ItemStack stack) {
        return hasPower(player, stack) && amount(stack) > 0;
    }

    public static boolean runEngine(
            final ServerPlayer player,
            final ItemStack stack,
            final InteractionHand hand,
            final int elapsed
    ) {
        if (player.isCreative()) return true;

        final List<ItemStack> tanks = BacktankUtil.getAllWithAir(player);
        if (!tanks.isEmpty()) {
            if (elapsed % BACKTANK_DRAW_TICKS == 0) {
                BacktankUtil.consumeAir(player, tanks.get(0), BACKTANK_AIR_PER_DRAW);
            }
            return true;
        }

        if (elapsed % ENGINE_TICKS_PER_AIR != 0) return hasPower(player, stack);
        if (!hasPower(player, stack)) return false;
        final int debt = DURABILITY_DEBT.merge(player.getUUID(), 1, Integer::sum);
        if (debt < AIR_PER_DURABILITY) return true;

        DURABILITY_DEBT.remove(player.getUUID());
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return true;
    }

    public static void stopEngine(final Player player) {
        if (player != null) DURABILITY_DEBT.remove(player.getUUID());
    }

    private static final class Handler extends FluidHandlerItemStack {
        private Handler(final ItemStack container) {
            super(CONTENTS, container, CAPACITY);
        }

        @Override
        public boolean isFluidValid(final int tank, final FluidStack stack) {
            return isLevitite(stack);
        }

        @Override
        public boolean canFillFluidType(final FluidStack fluid) {
            return isLevitite(fluid);
        }
    }
}
