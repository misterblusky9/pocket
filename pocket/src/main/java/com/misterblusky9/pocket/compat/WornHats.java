package com.misterblusky9.pocket.compat;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.compat.accessories.AccessoriesCompat;
import com.misterblusky9.pocket.compat.curios.CuriosHats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class WornHats {
    public static final boolean CURIOS = ModList.get().isLoaded("curios");
    public static final boolean ACCESSORIES = ModList.get().isLoaded("accessories")
            && !(CURIOS && ModList.get().isLoaded("accessories_compat_layer"));

    public static boolean isCustomHat(ItemStack stack) {
        return HatStandBlock.isHeadwear(stack) && BuildHostBlockEntity.token(stack) != null;
    }

    public static @Nullable ItemStack find(LivingEntity entity) {
        ItemStack found = CURIOS ? CuriosHats.find(entity) : null;
        return found == null && ACCESSORIES ? AccessoriesCompat.findHat(entity) : found;
    }

    public static void forEach(LivingEntity entity, Consumer<ItemStack> action) {
        if (CURIOS) CuriosHats.forEach(entity, action);
        if (ACCESSORIES) AccessoriesCompat.forEach(entity, action);
    }

    private WornHats() {}
}
