package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.client.CreativeShrinkRayRenderer;
import com.misterblusky9.pocket.client.ShrinkRayControls;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CreativeShrinkRayItem extends ZapperItem {
    private static final String STAGE_KEY = "PocketStage";

    public CreativeShrinkRayItem(final Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CreativeShrinkRayRenderer()));
    }

    public static CompressionStage selectedStage(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return CompressionStage.NORMAL;
        final CompoundTag tag = custom.copyTag();
        return CompressionStage.fromDepth(tag.getInt(STAGE_KEY));
    }

    public static void setSelectedStage(final ItemStack stack, final CompressionStage stage) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putInt(STAGE_KEY, stage == null ? 0 : stage.depth());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void openHandgunGUI(final ItemStack item, final InteractionHand hand) {
        final var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) ShrinkRayControls.showLadder(player, selectedStage(item));
    }

    @Override
    public @Nullable Component validateUsage(final ItemStack item) {
        return null;
    }

    @Override
    protected int getZappingRange(final ItemStack stack) {
        return 192;
    }

    @Override
    protected int getCooldownDelay(final ItemStack stack) {
        return 2;
    }

    @Override
    public void inventoryTick(
            final ItemStack stack,
            final Level level,
            final Entity entity,
            final int slotId,
            final boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        stack.remove(AllDataComponents.SHAPER_BLOCK_USED);
        stack.remove(AllDataComponents.SHAPER_BLOCK_DATA);
    }

    @Override
    protected boolean canActivateWithoutSelectedBlock(final ItemStack stack) {
        return true;
    }

    @Override
    protected boolean activate(
            final Level level,
            final Player player,
            final ItemStack stack,
            final BlockState stateToUse,
            final BlockHitResult raytrace,
            final CompoundTag data
    ) {
        if (level.isClientSide) return true;

        SubLevel subLevel = Sable.HELPER.getContaining(level, raytrace.getLocation());
        if (subLevel == null) subLevel = Sable.HELPER.getContaining(level, raytrace.getBlockPos());

        boolean lockedOn = false;
        if (!(subLevel instanceof ServerSubLevel)
                && player instanceof final net.minecraft.server.level.ServerPlayer holder) {
            subLevel = com.misterblusky9.pocket.compression.CompressionSessions.lockedSubLevel(holder);
            lockedOn = subLevel != null;
        }

        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) return false;

        final net.minecraft.core.BlockPos contact =
                lockedOn ? centreOf(serverSubLevel) : raytrace.getBlockPos();

        final CompressionStage target = selectedStage(stack);
        if (target.isCompressed()) {
            final int blocks = PocketMetrics.measureForCompression(serverSubLevel, level.getGameTime()).blocks();
            if (blocks > PocketSized.MAX_COMPRESSED_BLOCKS) {
                player.displayClientMessage(Component.literal("Pocket Sized hard limit: " + PocketSized.MAX_COMPRESSED_BLOCKS + " blocks"), true);
                return false;
            }
        }

        if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.misterblusky9.pocket.compression.CompressionSessions.instant(
                    serverPlayer, serverSubLevel, contact, target
            );
            return true;
        }

        ScaleController.forceStage(serverSubLevel, target, level.getGameTime());
        return true;
    }

    private static net.minecraft.core.BlockPos centreOf(final ServerSubLevel subLevel) {
        final var bounds = subLevel.getPlot().getBoundingBox();
        return new net.minecraft.core.BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
    }
}
