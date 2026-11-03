package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

public final class HatStandBlockEntity extends BuildHostBlockEntity {
    public static final int LAYER_MODES = 3;
    public static final int LAYER_REMOVED = 2;
    private @Nullable ResolvableProfile skin;
    private int skinLayer;
    private int headwearLayer;

    public HatStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAT_STAND.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.HAT_STAND.get(),
                (stand, side) -> stand.items());
    }

    public @Nullable ResolvableProfile getSkinProfile() {
        return skin != null && skin.properties().containsKey("textures") ? skin : null;
    }

    public int getSkinLayer() {
        return skinLayer;
    }

    public int getHeadwearLayer() {
        return headwearLayer;
    }

    public void setSkin(@Nullable ResolvableProfile profile) {
        if (!(level instanceof ServerLevel server)) return;
        skin = profile;
        sync();
        if (profile == null || profile.isResolved()) return;
        profile.resolve().thenAcceptAsync(resolved -> {
            if (level != server || isRemoved() || skin != profile) return;
            skin = resolved;
            sync();
        }, server.getServer());
    }

    public void setSkinLayer(int layer) {
        skinLayer = Math.floorMod(layer, LAYER_MODES);
        sync();
    }

    public void setHeadwearLayer(int layer) {
        headwearLayer = Math.floorMod(layer, LAYER_MODES);
        sync();
    }

    @Override
    protected void stamp(ItemStack result) {
        if (skinLayer == 0) result.remove(ModDataComponents.SKIN_LAYER.get());
        else result.set(ModDataComponents.SKIN_LAYER.get(), skinLayer);
        if (headwearLayer == 0) result.remove(ModDataComponents.HEADWEAR_LAYER.get());
        else result.set(ModDataComponents.HEADWEAR_LAYER.get(), headwearLayer);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && skin != null && !skin.isResolved()) setSkin(skin);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, skin);
        if (skinLayer != 0) components.set(ModDataComponents.SKIN_LAYER.get(), skinLayer);
        if (headwearLayer != 0) components.set(ModDataComponents.HEADWEAR_LAYER.get(), headwearLayer);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (skin != null) tag.put("SkinProfile",
                ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, skin).getOrThrow());
        if (skinLayer != 0) tag.putInt("SkinLayer", skinLayer);
        if (headwearLayer != 0) tag.putInt("HeadwearLayer", headwearLayer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        skin = tag.contains("SkinProfile")
                ? ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag.get("SkinProfile")).result().orElse(null)
                : null;
        skinLayer = Math.floorMod(tag.getInt("SkinLayer"), LAYER_MODES);
        headwearLayer = Math.floorMod(tag.getInt("HeadwearLayer"), LAYER_MODES);
    }
}
