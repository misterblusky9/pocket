package com.misterblusky9.pocket.compat.vista;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.client.BuildMirrors;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;

public final class VistaHatFeed {
    private VistaHatFeed() {}

    public static @Nullable ViewFinderBlockEntity shownCopy(ViewFinderBlockEntity camera) {
        Level level = camera.getLevel();
        ClientLevel client = Minecraft.getInstance().level;
        if (level == null || level.isClientSide() || client == null || client.dimension() != level.dimension())
            return null;
        if (!(client.getBlockEntity(camera.getBlockPos()) instanceof ViewFinderBlockEntity shown)) return null;
        return BuildMirrors.isKnownHatPart(Sable.HELPER.getContaining(client, camera.getBlockPos())) ? shown : null;
    }

    public static ViewFinderBlockEntity shown(ViewFinderBlockEntity camera) {
        ViewFinderBlockEntity shown = shownCopy(camera);
        return shown != null ? shown : camera;
    }

    public static boolean unavailable(BlockEntity camera) {
        Level level = camera.getLevel();
        if (level == null || !level.isClientSide()) return false;

        SubLevel part = Sable.HELPER.getContaining(level, camera.getBlockPos());
        if (part == null) return false;

        boolean hat = BuildMirrors.isKnownHatPart(part)
                || part.getLevel().getBlockState(part.getPlot().getCenterBlock())
                        .is(ModBlocks.POCKET_DUMMY.get());
        return hat && !BuildMirrors.isWornHatPart(part) && !BuildMirrors.isStandingHatPart(part);
    }
}
