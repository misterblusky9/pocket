package com.misterblusky9.pym.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.misterblusky9.pym.api.client.Portrait;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.lang.ref.WeakReference;

public final class PortraitMirror {
    private static final double REACH = 64.0D;
    private static final float HELD_SIZE = 0.5F;

    private static WeakReference<ClientSubLevel> held = new WeakReference<>(null);

    public static void registerCommand(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pymportrait").executes(context -> {
            final ClientSubLevel aimed = aimed();
            held = new WeakReference<>(aimed);
            context.getSource().sendSystemMessage(Component.literal(aimed == null
                    ? "Portrait cleared"
                    : "Portrait holding " + aimed.getUniqueId() + " (" + Portrait.span(aimed) + " blocks across)"));
            return aimed == null ? 0 : 1;
        }));
    }

    public static void renderHand(final RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;

        final ClientSubLevel subLevel = held.get();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !Portrait.canDraw(subLevel)) return;

        final PoseStack pose = event.getPoseStack();
        final float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        final float swing = event.getSwingProgress();
        final float swingIn = Mth.sin(swing * swing * Mth.PI);
        final float swingOut = Mth.sin(Mth.sqrt(swing) * Mth.PI);

        pose.pushPose();
        pose.translate(side * 0.56F, -0.52F + event.getEquipProgress() * -0.6F, -0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45.0F + swingIn * -20.0F)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * swingOut * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(swingOut * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
        pose.translate(0.0F, HELD_SIZE * 0.35F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(20.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -30.0F));

        if (Portrait.drawFitted(subLevel, pose, HELD_SIZE)) event.setCanceled(true);
        pose.popPose();
    }

    private static ClientSubLevel aimed() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        final Vec3 eye = player.getEyePosition();
        final Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
        final BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return null;

        SubLevel found = Sable.HELPER.getContaining(player.level(), hit.getLocation());
        if (found == null) found = Sable.HELPER.getContaining(player.level(), hit.getBlockPos());
        return found instanceof final ClientSubLevel client && !client.isRemoved() ? client : null;
    }

    private PortraitMirror() {}
}
