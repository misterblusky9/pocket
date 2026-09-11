package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class CrossScaleWeldSeams {
    private static final long RETRACE_INTERVAL = 5L;

    private static final Map<UUID, Cache> CELLS = new HashMap<>();
    private static long hoverStamp = Long.MIN_VALUE;
    private static UUID hovered;

    private record Cache(long stamp, Set<Long> cells, List<WeldContactPatch.Edge> outline) {}

    @SubscribeEvent
    public static void onFrame(final RenderFrameEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        final Level level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) return;

        final List<WeldRecord> welds = CrossScaleWelds.clientWelds();
        if (welds.isEmpty() || !revealing(player)) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        CELLS.keySet().removeIf(id -> welds.stream().noneMatch(w -> w.weldId().equals(id)));

        final UUID doomed = holdingKnife(player) ? hoveredWeld(minecraft, level) : null;

        for (final WeldRecord record : welds) {
            final SubLevel small = container.getSubLevel(record.smallSubLevel());
            final SubLevel big = record.worldAnchored() ? null : container.getSubLevel(record.bigSubLevel());
            if (small == null || small.isRemoved()
                    || (!record.worldAnchored() && (big == null || big.isRemoved()))) continue;
            final Cache cache = cached(level, record, small, big);
            if (cache.cells().isEmpty()) continue;

            final int color = record.weldId().equals(doomed)
                    ? SimColors.NUH_UH_RED
                    : SimColors.SUCCESS_LIME;

            WeldContactPatch.show(
                    "pocket_weld_" + record.weldId(),
                    record.smallPos(),
                    record.smallFacing(),
                    record.anchorFor(true),
                    WeldContact.identity(record.smallFacing()),
                    cache.outline(),
                    color,
                    lineWidth(small));
        }
    }

    public static void clear() {
        CELLS.clear();
        hoverStamp = Long.MIN_VALUE;
        hovered = null;
    }

    private static Cache cached(
            final Level level,
            final WeldRecord record,
            final SubLevel small,
            final SubLevel big
    ) {
        final long now = level.getGameTime();
        final Cache hit = CELLS.get(record.weldId());
        if (hit != null && now - hit.stamp() < RETRACE_INTERVAL) return hit;

        final Set<Long> cells = WeldContact.cells(
                level,
                small,
                record.smallPos(),
                record.smallFacing(),
                big,
                record.bigPos(),
                record.bigFacing(),
                record.anchorFor(false),
                WeldContact.projectionFor(record, true));
        final Cache cache = new Cache(
                now,
                cells,
                cells.isEmpty()
                        ? List.of()
                        : WeldContactPatch.silhouette(cells, WeldContactPatch.SEAM_OOZE));
        CELLS.put(record.weldId(), cache);
        return cache;
    }

    public static float lineWidth(final SubLevel small) {
        final double scale = small == null ? 1.0D : ScaleState.getScale(small);
        return (float) (PocketSized.clampScale(scale) / 16.0D);
    }

    private static UUID hoveredWeld(final Minecraft minecraft, final Level level) {
        final long now = level.getGameTime();
        if (hoverStamp == now) return hovered;
        hoverStamp = now;

        if (!(minecraft.hitResult instanceof final BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) {
            hovered = null;
            return null;
        }
        final WeldRecord record = CrossScaleWelds.weldNear(level, hit.getLocation());
        hovered = record == null ? null : record.weldId();
        return hovered;
    }

    public static boolean revealing(final LocalPlayer player) {
        if (player == null) return false;
        return reveals(player.getMainHandItem()) || reveals(player.getOffhandItem());
    }

    private static boolean holdingKnife(final LocalPlayer player) {
        return isKnife(player.getMainHandItem()) || isKnife(player.getOffhandItem());
    }

    private static boolean isKnife(final ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.POCKET_KNIFE.get());
    }

    private static boolean reveals(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(ModItems.GLUE_GUN.get()) || stack.is(ModItems.POCKET_KNIFE.get()));
    }

    private CrossScaleWeldSeams() {}
}
