package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.network.HatStandWrenchPayload;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class HatStandWrenchInput {
    private static final int HOLD_TICKS = 3;
    private static final float CLICK_PITCH = 1.3F;
    private static final float CLICK_VOLUME = 0.25F;
    private static @Nullable BlockPos pending;
    private static @Nullable BlockPos stand;
    private static @Nullable Vec2 aimed;
    private static boolean turning;
    private static boolean swallowing;
    private static int heldTicks;

    public static boolean isTurning() {
        return turning;
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (swallowing) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null
                || !AllItems.WRENCH.isIn(player.getItemInHand(event.getHand()))
                || !player.mayBuild()
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        int action = action(player, level, pos, hit);
        if (action == -1) return;
        swallowing = true;
        event.setCanceled(true);
        if (action == HatStandWrenchPayload.START) {
            pending = pos;
            stand = standOf(level, pos);
            aimed = null;
            heldTicks = 0;
            event.setSwingHand(false);
        } else {
            PacketDistributor.sendToServer(new HatStandWrenchPayload(pos, action));
        }
    }

    private static int action(LocalPlayer player, ClientLevel level, BlockPos pos, BlockHitResult hit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof HatStandBlock block) {
            if (!(level.getBlockEntity(block.standPos(state, pos)) instanceof HatStandBlockEntity stand)) return -1;
            if (player.isShiftKeyDown()) return stand.getActivator().isEmpty() ? -1 : HatStandWrenchPayload.TOGGLE;
            return !stand.isActive() && block.holdsHead(state) && stand.onHead(hit.getLocation())
                    ? HatStandWrenchPayload.START : -1;
        }
        if (player.isShiftKeyDown() || state.getBlock() instanceof IWrenchable) return -1;
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel != null && level.getBlockState(subLevel.getPlot().getCenterBlock()).is(ModBlocks.POCKET_DUMMY.get())
                ? HatStandWrenchPayload.START : -1;
    }

    private static @Nullable BlockPos standOf(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof HatStandBlock block) return block.standPos(state, pos);
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel == null ? null : BuildMirrors.host(subLevel);
    }

    private static void guide(LocalPlayer player, ClientLevel level) {
        if (stand == null || !(level.getBlockEntity(stand) instanceof HatStandBlockEntity entity)) return;
        Vec2 look = entity.aimedLook(player);
        if (look == null) return;
        if (aimed == null) aimed = entity.look();
        if (look.x != aimed.x || look.y != aimed.y)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    AllSoundEvents.SCROLL_VALUE.getMainEvent(), CLICK_PITCH, CLICK_VOLUME));
        aimed = look;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            pending = null;
            stand = null;
            turning = false;
            swallowing = false;
            return;
        }
        boolean held = minecraft.options.keyUse.isDown()
                && (AllItems.WRENCH.isIn(player.getMainHandItem()) || AllItems.WRENCH.isIn(player.getOffhandItem()));
        if (!minecraft.options.keyUse.isDown()) swallowing = false;
        if (!held) {
            pending = null;
            if (turning) PacketDistributor.sendToServer(new HatStandWrenchPayload(BlockPos.ZERO, HatStandWrenchPayload.STOP));
            turning = false;
            stand = null;
            return;
        }
        if (pending != null && ++heldTicks >= HOLD_TICKS) {
            PacketDistributor.sendToServer(new HatStandWrenchPayload(pending, HatStandWrenchPayload.START));
            pending = null;
            turning = true;
        }
        if (turning) guide(player, minecraft.level);
    }

    private HatStandWrenchInput() {}
}
