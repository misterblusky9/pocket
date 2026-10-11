package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CaseSeal;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketSeal;
import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

public record CaseSealAim(PackageEntity pocketed, @Nullable CaseSeal seal, boolean canSeal, boolean canCut) {
    public static final int NO_COLOUR = -1;
    private static final int SEALABLE_COLOUR = 0xBFBFBF;

    @Nullable
    public static CaseSealAim of(final LocalPlayer player) {
        if (!(Minecraft.getInstance().hitResult instanceof final EntityHitResult hit)) return null;
        return of(player, hit.getEntity());
    }

    @Nullable
    public static CaseSealAim of(final LocalPlayer player, final Entity entity) {
        if (!(entity instanceof final PackageEntity pocketed) || !holdingTool(player)) return null;
        final ItemStack box = pocketed.getBox();
        if (box == null || !PocketCaseItem.isSealable(box)) return null;

        final CaseSeal seal = PocketCaseItem.caseSeal(box);
        return new CaseSealAim(pocketed, seal, seal == null && holdingGlue(player), seal != null && seal.canCut(player));
    }

    public static boolean holdingTool(final LocalPlayer player) {
        return holdingGlue(player)
                || player.getMainHandItem().is(ModItems.POCKET_KNIFE.get())
                || player.getOffhandItem().is(ModItems.POCKET_KNIFE.get());
    }

    private static boolean holdingGlue(final LocalPlayer player) {
        return PocketSeal.appliedBy(player.getMainHandItem()) != null
                || PocketSeal.appliedBy(player.getOffhandItem()) != null;
    }

    public int colour() {
        if (this.seal == null) return this.canSeal ? SEALABLE_COLOUR : NO_COLOUR;
        return this.canCut ? SimColors.SUCCESS_LIME : SimColors.NUH_UH_RED;
    }

    public String label() {
        if (this.seal == null) return "Unsealed";
        return this.canCut ? "Sealed" : "Sealed by: " + this.seal.ownerName();
    }
}
