package com.misterblusky9.pocket.compat.accessories;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.WornHats;
import com.misterblusky9.pocket.item.PersonalCompressors;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AccessoriesCompat {
    private static final String HAT_SLOT = "hat";

    public static void register() {
        AccessoriesAPI.registerPredicate(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "custom_hat"),
                (level, slotType, slot, stack) -> WornHats.isCustomHat(stack) ? TriState.TRUE : TriState.DEFAULT);
    }

    public static @Nullable ItemStack findHat(LivingEntity entity) {
        AccessoriesCapability capability = AccessoriesCapability.get(entity);
        AccessoriesContainer container = capability == null ? null : capability.getContainers().get(HAT_SLOT);
        if (container == null) return null;
        for (int i = 0; i < container.getSize(); i++) {
            if (!container.shouldRender(i)) continue;
            ItemStack cosmetic = container.getCosmeticAccessories().getItem(i);
            if (WornHats.isCustomHat(cosmetic)) return cosmetic;
            ItemStack worn = container.getAccessories().getItem(i);
            if (WornHats.isCustomHat(worn)) return worn;
        }
        return null;
    }

    public static void forEach(LivingEntity entity, Consumer<ItemStack> action) {
        AccessoriesCapability capability = AccessoriesCapability.get(entity);
        if (capability == null) return;
        for (AccessoriesContainer container : capability.getContainers().values()) {
            for (int i = 0; i < container.getSize(); i++) {
                action.accept(container.getAccessories().getItem(i));
                action.accept(container.getCosmeticAccessories().getItem(i));
            }
        }
    }

    public static @Nullable ItemStack findCompressor(Player player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return null;
        SlotEntryReference found = capability.getFirstEquipped((Predicate<ItemStack>) PersonalCompressors::is);
        return found == null ? null : found.stack();
    }

    private AccessoriesCompat() {}
}
