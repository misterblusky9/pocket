package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PocketContainer {
    CARDBOARD_BOX(
            "empty_box", "item/container/cardboard_box",
            12.0D, 0.5D,
            1.5D, 12.0D, -1.0D,
            false
    ),

    DISPLAY_BOTTLE(
            "display_bottle", "item/display_bottle",
            12.0D, 0.5D,
            0.5D, 12.0D, 0.5D,
            true
    ),

    BRASS_DISPLAY_CASE(
            "brass_display_case", "item/brass_display_case",
            12.0D, 0.5D,
            1.25D, 13.0D, 0.5D,
            true
    ),

    BRASS_DISPLAY_PLATE(
            "brass_display_plate", "item/brass_display_plate",
            12.0D, 0.5D,
            1.25D, 13.0D, -1.0D,
            false
    ),

    ANDESITE_DISPLAY_CASE(
            "andesite_display_case", "item/andesite_display_case",
            12.0D, 0.5D,
            1.25D, 13.0D, 0.5D,
            true
    ),

    ANDESITE_DISPLAY_PLATE(
            "andesite_display_plate", "item/andesite_display_plate",
            12.0D, 0.5D,
            1.25D, 13.0D, -1.0D,
            false
    );

    private final String registryPath;
    private final String modelPath;
    private final double interiorWidthVoxels;
    private final double sideMarginVoxels;
    private final double floorVoxels;
    private final double ceilingVoxels;
    private final double topMarginVoxels;
    private final boolean translucent;

    PocketContainer(
            final String registryPath,
            final String modelPath,
            final double interiorWidthVoxels,
            final double sideMarginVoxels,
            final double floorVoxels,
            final double ceilingVoxels,
            final double topMarginVoxels,
            final boolean translucent
    ) {
        this.registryPath = registryPath;
        this.modelPath = modelPath;
        this.interiorWidthVoxels = interiorWidthVoxels;
        this.sideMarginVoxels = sideMarginVoxels;
        this.floorVoxels = floorVoxels;
        this.ceilingVoxels = ceilingVoxels;
        this.topMarginVoxels = topMarginVoxels;
        this.translucent = translucent;
    }

    public Item item() {
        final Item item = BuiltInRegistries.ITEM.get(id());
        return item == Items.AIR ? ModItems.EMPTY_BOX.get() : item;
    }

    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, this.registryPath);
    }

    public ResourceLocation modelId() {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, this.modelPath);
    }

    public double clearWidth() {
        return (this.interiorWidthVoxels - 2.0D * this.sideMarginVoxels) / 16.0D;
    }

    public double clearHeight() {
        if (this.topMarginVoxels < 0.0D) return Double.POSITIVE_INFINITY;
        return (this.ceilingVoxels - this.floorVoxels - this.topMarginVoxels) / 16.0D;
    }

    public double floorY() {
        return -0.5D + this.floorVoxels / 16.0D;
    }

    public boolean translucent() {
        return this.translucent;
    }

    public static PocketContainer byItem(final Item item) {
        if (item == null) return null;
        for (final PocketContainer container : values()) {
            if (container.item() == item) return container;
        }
        return null;
    }

    public static boolean isContainer(final ItemStack stack) {
        return stack != null && !stack.isEmpty() && byItem(stack.getItem()) != null;
    }

    public static PocketContainer of(final ItemStack carrier) {
        final ResourceLocation recorded = PocketCaseItem.containerId(carrier);
        if (recorded != null) {
            for (final PocketContainer container : values()) {
                if (container.id().equals(recorded)) return container;
            }
        }
        return CARDBOARD_BOX;
    }
}
