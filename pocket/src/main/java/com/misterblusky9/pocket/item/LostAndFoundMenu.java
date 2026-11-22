package com.misterblusky9.pocket.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LostAndFoundMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    public static final int WINDOW = COLUMNS * ROWS;

    private final SimpleContainer window = new SimpleContainer(WINDOW);
    private final ContainerData data;
    private final PocketRecovery.Kind kind;
    private final Player viewer;
    private final List<PocketRecovery.Lost> all = new ArrayList<>();
    private List<PocketRecovery.Lost> entries = List.of();
    private String search = "";

    public LostAndFoundMenu(int id, Inventory inventory) {
        this(id, inventory, PocketRecovery.Kind.ALL, new SimpleContainerData(2));
    }

    LostAndFoundMenu(int id, Inventory inventory, PocketRecovery.Kind kind) {
        this(id, inventory, kind, new SimpleContainerData(2));
        if (inventory.player instanceof ServerPlayer viewer) all.addAll(PocketRecovery.lost(viewer.server, kind));
        refresh(inventory.player);
    }

    private LostAndFoundMenu(int id, Inventory inventory, PocketRecovery.Kind kind, ContainerData data) {
        super(ModMenus.LOST_AND_FOUND.get(), id);
        this.kind = kind;
        this.data = data;
        this.viewer = inventory.player;
        for (int row = 0; row < ROWS; row++)
            for (int column = 0; column < COLUMNS; column++)
                addSlot(new Shown(window, column + row * COLUMNS, 9 + column * 18, 18 + row * 18));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, 9 + column * 18, 112 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 9 + column * 18, 170));
        addDataSlots(data);
    }

    public int count() {
        return data.get(0);
    }

    public int row() {
        return data.get(1);
    }

    public int rows() {
        return Math.max(0, (count() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    private void refresh(Player player) {
        if (!(player instanceof ServerPlayer viewer)) return;
        entries = all.stream().filter(lost -> PocketRecovery.matches(lost, search)).toList();
        data.set(0, entries.size());
        data.set(1, Math.min(row(), rows()));
        for (int i = 0; i < WINDOW; i++) {
            int index = row() * COLUMNS + i;
            window.setItem(i, index < entries.size() ? PocketRecovery.shown(entries.get(index)) : ItemStack.EMPTY);
        }
    }

    public void search(String text) {
        search = text.toLowerCase(Locale.ROOT).trim();
        data.set(1, 0);
        refresh(viewer);
    }

    @Override
    public boolean clickMenuButton(Player player, int row) {
        data.set(1, Math.max(0, row));
        refresh(player);
        return true;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot < 0 || slot >= WINDOW) {
            super.clicked(slot, button, type, player);
            return;
        }
        if (!(player instanceof ServerPlayer viewer)) return;
        if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
            if (!getCarried().isEmpty()) {
                deposit(viewer, getCarried());
                setCarried(ItemStack.EMPTY);
            } else {
                int index = row() * COLUMNS + slot;
                PocketRecovery.Lost lost = index < entries.size() ? entries.get(index) : null;
                if (lost != null) all.remove(lost);
                if (lost != null && PocketRecovery.stillLost(lost)) {
                    ItemStack item = PocketRecovery.take(lost);
                    if (type == ClickType.PICKUP) setCarried(item);
                    else if (!viewer.getInventory().add(item)) viewer.drop(item, false);
                }
            }
        }
        refresh(viewer);
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < WINDOW || !(player instanceof ServerPlayer viewer) || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (!PocketCaseItem.isUnique(stack) && com.misterblusky9.pocket.build.BuildHostBlockEntity.token(stack) == null)
            return ItemStack.EMPTY;
        deposit(viewer, stack);
        slot.set(ItemStack.EMPTY);
        refresh(viewer);
        return ItemStack.EMPTY;
    }

    private void deposit(ServerPlayer viewer, ItemStack stack) {
        PocketRecovery.Lost stored = PocketRecovery.store(viewer.serverLevel(), stack, kind);
        if (stored == null) return;
        all.removeIf(lost -> lost.token().equals(stored.token()));
        all.addFirst(stored);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static final class Shown extends Slot {
        private Shown(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
