package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeAlignment;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.network.FacadeAlignmentPayload;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FacadeAlignmentInput {
    public static void onInteraction(final InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        final ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null || !player.isShiftKeyDown()) return;
        if (!(minecraft.hitResult instanceof final BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        final BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())) return;
        if (!(level.getBlockEntity(pos) instanceof final CopycatBlockEntity copycat) || !copycat.hasCustomMaterial()) return;
        if (!FacadeAlignment.holds(player.getItemInHand(event.getHand()), copycat.getMaterial().getBlock())) return;

        final SubLevel craft = Sable.HELPER.getContaining(level, pos);
        if (craft == null || !FacadeAlignment.shrunk(Pym.scale().of(craft))) return;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new FacadeAlignmentPayload(pos));
    }

    private FacadeAlignmentInput() {}
}
