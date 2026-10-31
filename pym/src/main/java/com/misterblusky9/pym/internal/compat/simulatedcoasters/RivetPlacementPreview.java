package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.client.LineEmitter;
import com.misterblusky9.pym.internal.PymMod;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = PymMod.MOD_ID, value = Dist.CLIENT)
public final class RivetPlacementPreview {
    private static final String KEY = "rivet_placement_preview";
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {1, 3}, {2, 3},
            {4, 5}, {4, 6}, {5, 7}, {6, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static ClientSubLevel owner;
    private static Vec3[] corners;
    private static int color;

    public static void show(
            ClientSubLevel subLevel, Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
            double halfRight, double halfUp, double halfForward, int outlineColor
    ) {
        owner = subLevel;
        color = outlineColor;
        corners = new Vec3[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = center
                    .add(right.scale((i & 1) == 0 ? -halfRight : halfRight))
                    .add(up.scale((i & 2) == 0 ? -halfUp : halfUp))
                    .add(forward.scale((i & 4) == 0 ? -halfForward : halfForward));
        }
        draw(Minecraft.getInstance());
    }

    public static void clear() {
        if (owner == null) return;
        owner = null;
        corners = null;
        Outliner outliner = Outliner.getInstance();
        for (int i = 0; i < EDGES.length; i++) outliner.remove(Pair.of(KEY, i));
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        draw(Minecraft.getInstance());
    }

    private static void draw(Minecraft minecraft) {
        if (owner == null) return;
        if (owner.isRemoved() || owner.getLevel() != minecraft.level
                || minecraft.player == null || minecraft.options.hideGui) {
            clear();
            return;
        }
        var pose = owner.renderPose(minecraft.getTimer().getGameTimeDeltaPartialTick(false));
        double scale = pose.scale().x();
        if (!ScaleBounds.isValid(scale)) {
            clear();
            return;
        }
        Vec3[] world = new Vec3[corners.length];
        for (int i = 0; i < corners.length; i++) world[i] = pose.transformPosition(corners[i]);
        Outliner outliner = Outliner.getInstance();
        for (int i = 0; i < EDGES.length; i++) {
            int[] edge = EDGES[i];
            LineEmitter.weldLine(outliner.showLine(Pair.of(KEY, i), world[edge[0]], world[edge[1]])
                    .colored(color).lineWidth((float) (0.0625D * scale)), null);
        }
    }

    private RivetPlacementPreview() {}
}
