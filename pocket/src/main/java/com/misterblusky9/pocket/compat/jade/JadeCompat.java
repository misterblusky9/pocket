package com.misterblusky9.pocket.compat.jade;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.PocketDummyBlock;
import com.misterblusky9.pocket.client.BuildMirrors;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

@WailaPlugin(PocketSized.MOD_ID)
public class JadeCompat implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(CaseName.INSTANCE, PackageEntity.class);
        registration.registerBlockComponent(DummyHost.INSTANCE, PocketDummyBlock.class);
        registration.registerBlockIcon(DummyHost.INSTANCE, PocketDummyBlock.class);
    }

    enum DummyHost implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "dummy_host");

        private static @Nullable BlockState host(BlockAccessor accessor) {
            SubLevel subLevel = Sable.HELPER.getContaining(accessor.getLevel(), accessor.getPosition());
            if (subLevel == null) return null;
            BlockPos host = BuildMirrors.host(subLevel);
            if (host == null) return null;
            BlockState state = accessor.getLevel().getBlockState(host);
            return state.isAir() ? null : state;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState host = host(accessor);
            if (host == null) return;
            tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(host.getBlock().getName()));
        }

        @Override
        public @Nullable IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
            BlockState host = host(accessor);
            if (host == null) return currentIcon;
            ItemStack stack = new ItemStack(host.getBlock());
            return stack.isEmpty() ? currentIcon : IElementHelper.get().item(stack);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public int getDefaultPriority() {
            return -10000;
        }
    }

    enum CaseName implements IEntityComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "case_name");

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof PackageEntity entity) || entity.hasCustomName()) return;
            ItemStack box = entity.getBox();
            if (!(box.getItem() instanceof PocketCaseItem)) return;
            PocketContainer container = PocketContainer.of(box);
            if (container == PocketContainer.CARDBOARD_BOX) return;
            tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(container.item().getDescription()));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public int getDefaultPriority() {
            return -10000;
        }
    }
}
