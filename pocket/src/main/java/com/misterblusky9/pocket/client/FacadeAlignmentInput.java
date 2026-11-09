package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeAlignment;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.network.FacadeAlignmentPayload;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FacadeAlignmentInput {
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null || !player.isShiftKeyDown()) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())) return;
        if (!(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat) || !copycat.hasCustomMaterial()) return;
        if (!FacadeAlignment.holds(player.getItemInHand(event.getHand()), copycat.getMaterial().getBlock())) return;

        SubLevel craft = Sable.HELPER.getContaining(level, pos);
        if (craft == null || !FacadeAlignment.shrunk(Pym.scale().of(craft))) return;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new FacadeAlignmentPayload(pos));
    }

    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (event.isCanceled() || !AllKeys.altDown() || event.getScrollDeltaY() == 0.0D) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null
                || (!AllItems.WRENCH.isIn(player.getMainHandItem())
                && !AllItems.WRENCH.isIn(player.getOffhandItem()))
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())
                || !(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat)
                || !copycat.hasCustomMaterial()) return;

        SubLevel craft = Sable.HELPER.getContaining(level, pos);
        if (craft == null || !Pym.scale().isSettled(craft)
                || !FacadeAlignment.shrunk(Pym.scale().of(craft))) return;

        int step = event.getScrollDeltaY() > 0.0D ? 1 : -1;
        Vec3 eye = craft.logicalPose().transformPositionInverse(player.getEyePosition());
        if (new AABB(pos).contains(eye)) step = -step;

        PacketDistributor.sendToServer(new FacadeAlignmentPayload(
                pos, hit.getDirection().get3DDataValue(), step));
        event.setCanceled(true);
    }

    private FacadeAlignmentInput() {}
}
