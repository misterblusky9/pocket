package com.misterblusky9.pocket.compat.curios;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.event.CurioCanUnequipEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.Consumer;

public final class CuriosHats {
    private static final String SLOT = "head";

    public static void register() {
        CuriosApi.registerCurioPredicate(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "custom_hat"),
                result -> isCustomHat(result.stack()));
        NeoForge.EVENT_BUS.addListener(CuriosHats::onAttributes);
        NeoForge.EVENT_BUS.addListener(CuriosHats::onUnequip);
    }

    public static boolean isCustomHat(ItemStack stack) {
        return HatStandBlock.isHeadwear(stack) && BuildHostBlockEntity.token(stack) != null;
    }

    public static @Nullable ItemStack find(LivingEntity entity) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(entity)
                .flatMap(inventory -> inventory.getStacksHandler(SLOT)).orElse(null);
        if (handler == null) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getRenders().get(i)) continue;
            ItemStack cosmetic = handler.getCosmeticStacks().getStackInSlot(i);
            if (isCustomHat(cosmetic)) return cosmetic;
            ItemStack worn = handler.getStacks().getStackInSlot(i);
            if (isCustomHat(worn)) return worn;
        }
        return null;
    }

    public static void forEach(LivingEntity entity, Consumer<ItemStack> action) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    action.accept(handler.getStacks().getStackInSlot(i));
                    action.accept(handler.getCosmeticStacks().getStackInSlot(i));
                }
            }
        });
    }

    private static void onAttributes(CurioAttributeModifierEvent event) {
        if (isCustomHat(event.getItemStack())) event.clearModifiers();
    }

    private static void onUnequip(CurioCanUnequipEvent event) {
        if (isCustomHat(event.getStack())) event.setUnequipResult(TriState.TRUE);
    }

    private CuriosHats() {}
}
