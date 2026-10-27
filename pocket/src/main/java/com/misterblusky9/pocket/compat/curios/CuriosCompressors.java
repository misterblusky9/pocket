package com.misterblusky9.pocket.compat.curios;

import com.misterblusky9.pocket.item.PersonalCompressors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

public final class CuriosCompressors {
    public static @Nullable ItemStack find(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.findFirstCurio(PersonalCompressors::is))
                .map(SlotResult::stack)
                .orElse(null);
    }

    private CuriosCompressors() {}
}
