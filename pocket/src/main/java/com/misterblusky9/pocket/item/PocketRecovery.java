package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.CarriedBuilds;
import com.misterblusky9.pocket.compat.WornHats;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PocketRecovery {
    public enum Kind { HATS, CASES, ALL }

    public static final Component MISSING = Component.translatable("pocket.recover.missing")
            .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY));

    public static final String LAST_SEEN = "pocket_last_seen";
    private static final String MISSING_KEY = "pocket_data_not_found";

    public static long lastSeen(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0L : data.copyTag().getLong(LAST_SEEN);
    }

    public static boolean missing(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(MISSING_KEY);
    }

    record Lost(UUID token, ServerLevel level, boolean hat, ItemStack item, boolean missing, long modified) {}

    public static int open(CommandSourceStack source, Kind kind) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new LostAndFoundMenu(id, inventory, kind),
                Component.translatable("pocket.recover.title")));
        return 1;
    }

    static List<Lost> lost(MinecraftServer server, Kind kind) {
        Set<UUID> held = held(server);
        List<Lost> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
            for (UUID token : storage.tokens()) {
                if (held.contains(token) || storage.wasDeployed(token) || BuildSites.live(token) != null
                        || CarriedBuilds.holds(token)) continue;
                CompoundTag saved = storage.peek(token);
                ItemStack item = storage.carrier(token, level.registryAccess());
                boolean missing = item.isEmpty();
                boolean hat = missing ? BuildSites.isHatPayload(saved) : !(item.getItem() instanceof PocketCaseItem);
                if (kind == Kind.HATS && !hat || kind == Kind.CASES && hat) continue;
                if (hat && (saved == null || !BuildSites.hasStoredBuild(saved))) continue;
                if (hat && missing) {
                    item = new ItemStack(Items.LEATHER_HELMET);
                    item.set(ModDataComponents.HEADWEAR_LAYER.get(), HatStandBlockEntity.LAYER_REMOVED);
                    BuildHostBlockEntity.restore(item, token, storage.epoch(token));
                } else if (hat) {
                    BuildHostBlockEntity.stampEpoch(item, storage.epoch(token));
                } else if (missing) {
                    item = PocketCaseItem.rebuild(level, token, saved);
                }
                found.add(new Lost(token, level, hat, item, missing, storage.modified(token)));
            }
        }
        found.sort(Comparator.comparing(Lost::missing).thenComparing(Lost::modified, Comparator.reverseOrder()));
        return found;
    }

    static boolean matches(Lost lost, String search) {
        if (search.isEmpty()) return true;
        String creator = BuildHostBlockEntity.creator(lost.item());
        return lost.item().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(search)
                || creator != null && creator.toLowerCase(java.util.Locale.ROOT).contains(search)
                || lost.token().toString().startsWith(search);
    }

    static boolean stillLost(Lost lost) {
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(lost.level());
        return storage.contains(lost.token()) && !storage.wasDeployed(lost.token())
                && BuildSites.live(lost.token()) == null && !CarriedBuilds.holds(lost.token())
                && !held(lost.level().getServer()).contains(lost.token());
    }

    static ItemStack shown(Lost lost) {
        ItemStack shown = lost.item().copy();
        if (lost.missing()) CustomData.update(DataComponents.CUSTOM_DATA, shown, tag -> tag.putBoolean(MISSING_KEY, true));
        else if (lost.modified() > 0L) CustomData.update(DataComponents.CUSTOM_DATA, shown,
                tag -> tag.putLong(LAST_SEEN, lost.modified()));
        return shown;
    }

    static ItemStack take(Lost lost) {
        ItemStack item = lost.item().copy();
        if (lost.hat()) BuildHostBlockEntity.stampEpoch(item, BuildSites.claim(lost.level(), lost.token()));
        return item;
    }

    static @Nullable Lost store(ServerLevel level, ItemStack stack, Kind kind) {
        boolean caseItem = stack.getItem() instanceof PocketCaseItem;
        UUID token = caseItem ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token == null) return null;
        boolean current = caseItem ? PocketCaseItem.payloadState(level, stack) == PocketCaseItem.PayloadState.AVAILABLE
                : BuildSites.live(token) == null && BuildSites.current(level, token, stack);
        ServerLevel stored = BuildSites.storedIn(level, token);
        if (!current || stored == null) return null;
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(stored);
        storage.remember(token, stack, level.registryAccess());
        if (kind == Kind.HATS && caseItem || kind == Kind.CASES && !caseItem) return null;
        return new Lost(token, stored, !caseItem, stack.copy(), false, storage.modified(token));
    }

    private static Set<UUID> held(MinecraftServer server) {
        Set<UUID> held = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            collect(held, player.getInventory());
            collect(held, player.getEnderChestInventory());
            collect(held, player.containerMenu.getCarried());
            WornHats.forEach(player, stack -> collect(held, stack));
        }
        return held;
    }

    private static void collect(Set<UUID> held, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) collect(held, container.getItem(i));
    }

    private static void collect(Set<UUID> held, ItemStack stack) {
        UUID token = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token != null) held.add(token);
    }

    private PocketRecovery() {}
}
