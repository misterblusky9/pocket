package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.network.HatNudgePayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class HatStandControls {
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !Screen.hasAltDown() || event.getScrollDeltaY() == 0.0
                || !(minecraft.hitResult instanceof BlockHitResult hit)) return;
        BlockPos stand = hit.getBlockPos();
        if (!(minecraft.level.getBlockEntity(stand) instanceof BuildHostBlockEntity)) {
            SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, stand);
            stand = subLevel == null ? null : BuildMirrors.standOf(subLevel);
        }
        if (stand == null || !(minecraft.level.getBlockEntity(stand) instanceof BuildHostBlockEntity host)
                || host.getHat() == null) return;
        ClientSubLevel mirror = BuildMirrors.standing(host.getHat());
        if (mirror == null) return;
        Vec3 look = minecraft.player.getViewVector(1.0F);
        Vector3d local = new Quaterniond(mirror.logicalPose().orientation()).conjugate()
                .transform(new Vector3d(look.x, look.y, look.z));
        Direction axis = Direction.getNearest(local.x, local.y, local.z);
        int step = event.getScrollDeltaY() > 0.0 ? 1 : -1;
        PacketDistributor.sendToServer(new HatNudgePayload(stand,
                axis.getStepX() * step, axis.getStepY() * step, axis.getStepZ() * step));
        event.setCanceled(true);
    }

    private HatStandControls() {}
}
