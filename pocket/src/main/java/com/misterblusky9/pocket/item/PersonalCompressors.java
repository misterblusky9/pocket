package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.compat.curios.CuriosCompressors;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class PersonalCompressors {
    private static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static @Nullable ItemStack find(Player player) {
        if (CURIOS) {
            ItemStack worn = CuriosCompressors.find(player);
            if (worn != null) return worn;
        }
        if (is(player.getMainHandItem())) return player.getMainHandItem();
        if (is(player.getOffhandItem())) return player.getOffhandItem();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (is(stack)) return stack;
        }
        return null;
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof SelfResizeDeviceItem;
    }

    private PersonalCompressors() {}
}
