package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.HotGlueGunRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class HotGlueGunItem extends Item implements PriorityInteractionItem {
    public HotGlueGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsBlock(
            Player player,
            ItemStack stack,
            Level level,
            BlockPos pos
    ) {
        return Sable.HELPER.getContaining(level, pos) != null
                || level != null && pos != null && !level.getBlockState(pos).isAir();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new HotGlueGunRenderer()));
    }
}
