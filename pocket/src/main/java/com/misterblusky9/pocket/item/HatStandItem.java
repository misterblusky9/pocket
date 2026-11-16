package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.HatStandItemRenderer;
import com.mojang.authlib.properties.PropertyMap;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.Optional;
import java.util.function.Consumer;

public class HatStandItem extends BlockItem {
    public HatStandItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean name(ItemStack stand, String name) {
        if (!StringUtil.isValidPlayerName(name)) return false;
        stand.set(DataComponents.PROFILE, new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap()));
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new HatStandItemRenderer()));
    }
}
