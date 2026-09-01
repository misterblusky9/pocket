package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CompressionGunTargetingMode;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.network.CompressionGunSettingsPayload;
import com.misterblusky9.pocket.network.CreativeShrinkRayTargetingPayload;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class CompressionGunTargetingScreen extends AbstractSimiScreen {
    private static final int BUTTON_TOP = 28;
    private static final int BUTTON_PITCH = 18;

    private final PocketGuiTexture background;
    private final ItemStack tool;
    private final InteractionHand hand;
    private final boolean creativeRay;
    private final Component title = Component.literal("Targeting Mode");
    private final List<ModeButton> modeButtons = new ArrayList<>(3);

    private CompressionGunTargetingMode selected;

    public CompressionGunTargetingScreen(final ItemStack tool, final InteractionHand hand) {
        this.tool = tool;
        this.hand = hand;
        this.creativeRay = tool.getItem() instanceof CreativeShrinkRayItem;
        this.background = PocketGuiTexture.SHRINKRAY;
        this.selected = this.creativeRay
                ? CreativeShrinkRayItem.targetingMode(tool)
                : CompressionGunItem.targetingMode(tool);
    }

    @Override
    protected void init() {
        setWindowSize(this.background.getWidth(), this.background.getHeight());
        setWindowOffset(-10, 0);
        super.init();

        if (this.selected == CompressionGunTargetingMode.SELF && !PehkuiScaleBridge.ownsScaling()) {
            this.selected = CompressionGunTargetingMode.SUBLEVEL;
        }

        final int x = this.guiLeft;
        final int y = this.guiTop;

        final IconButton confirm = new IconButton(
                x + this.background.getWidth() - 33,
                y + this.background.getHeight() - 24,
                AllIcons.I_CONFIRM
        );
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        this.modeButtons.clear();

        final List<CompressionGunTargetingMode> availableModes = new ArrayList<>(3);
        availableModes.add(CompressionGunTargetingMode.SUBLEVEL);
        availableModes.add(CompressionGunTargetingMode.CONNECTED_SUBLEVELS);
        if (PehkuiScaleBridge.ownsScaling()) {
            availableModes.add(CompressionGunTargetingMode.SELF);
        }

        final int rowWidth = availableModes.size() * BUTTON_PITCH;
        final int rowLeft = (this.background.getWidth() - rowWidth) / 2 - 4;

        for (int i = 0; i < availableModes.size(); i++) {
            final CompressionGunTargetingMode mode = availableModes.get(i);
            addModeButton(mode, iconFor(mode), rowLeft + i * BUTTON_PITCH);
        }

        refreshSelection();
    }

    private void addModeButton(
            final CompressionGunTargetingMode mode,
            final AllIcons icon,
            final int left
    ) {
        final IconButton button = new IconButton(
                this.guiLeft + left,
                this.guiTop + BUTTON_TOP,
                icon
        );

        button.withCallback(() -> {
            this.selected = mode;
            refreshSelection();
        });
        button.setToolTip(Component.literal(mode.label()));

        this.modeButtons.add(new ModeButton(mode, button));
        addRenderableWidget(button);
    }

    private void refreshSelection() {
        for (final ModeButton entry : this.modeButtons) {
            entry.button().green = entry.mode() == this.selected;
        }
    }

    @Override
    protected void renderWindow(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTicks
    ) {
        final int x = this.guiLeft;
        final int y = this.guiTop;

        this.background.render(graphics, x, y);
        graphics.drawString(
                this.font,
                this.title,
                x + (this.background.getWidth() - this.font.width(this.title)) / 2,
                y + 4,
                0xFF_FF_FF,
                false
        );

        GuiGameElement.of(this.tool)
                .scale(3.25D)
                .at(x + this.background.getWidth() - 8, y + this.background.getHeight() - 44, -200.0F)
                .render(graphics);
    }

    @Override
    public void removed() {
        if (this.creativeRay) {
            CreativeShrinkRayItem.setTargetingMode(this.tool, this.selected);
            PacketDistributor.sendToServer(new CreativeShrinkRayTargetingPayload(this.hand, this.selected));
            return;
        }

        CompressionGunItem.setTargetingMode(this.tool, this.selected);
        PacketDistributor.sendToServer(new CompressionGunSettingsPayload(
                this.hand,
                this.selected,
                CompressionGunItem.isGrowing(this.tool)
        ));
    }

    private static AllIcons iconFor(final CompressionGunTargetingMode mode) {
        return switch (mode) {
            case SUBLEVEL -> AllIcons.I_TARGET;
            case CONNECTED_SUBLEVELS -> AllIcons.I_ACTIVE;
            case SELF -> AllIcons.I_CONFIRM;
        };
    }

    private record ModeButton(CompressionGunTargetingMode mode, IconButton button) {}
}
