package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.network.CannonExpansionPayload;
import com.misterblusky9.pocket.pocket.CannonExpansionMode;
import com.simibubi.create.foundation.gui.AllGuiTextures;
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

public class CannonExpansionScreen extends AbstractSimiScreen {
    private final PocketGuiTexture background = PocketGuiTexture.CANNON;
    private final ItemStack cannon;
    private final InteractionHand hand;
    private final Component title = Component.literal("Projectile Expansion");

    private static final int SLOT_ROW_LEFT = 63;
    private static final int SLOT_ROW_TOP = 28;
    private static final int SLOT_PITCH = 18;

    private final List<IconButton> modeButtons = new ArrayList<>(CannonExpansionMode.values().length);
    private CannonExpansionMode selected;

    public CannonExpansionScreen(final ItemStack cannon, final InteractionHand hand) {
        this.cannon = cannon;
        this.hand = hand;
        this.selected = CannonExpansionMode.of(cannon);
    }

    @Override
    protected void init() {
        setWindowSize(this.background.getWidth(), this.background.getHeight());
        setWindowOffset(-10, 0);
        super.init();

        final int x = this.guiLeft;
        final int y = this.guiTop;

        final IconButton confirm = new IconButton(
                x + this.background.getWidth() - 33, y + this.background.getHeight() - 24, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        this.modeButtons.clear();
        for (final CannonExpansionMode mode : CannonExpansionMode.values()) {
            final int index = this.modeButtons.size();

            final IconButton button = new IconButton(
                    x + SLOT_ROW_LEFT + index * SLOT_PITCH, y + SLOT_ROW_TOP, iconFor(mode));

            button.withCallback(() -> {
                this.modeButtons.forEach(other -> other.green = false);
                button.green = true;
                this.selected = mode;
            });
            button.setToolTip(Component.literal(mode.label()));
            this.modeButtons.add(button);
        }

        this.modeButtons.get(this.selected.ordinal()).green = true;
        addRenderableWidgets(this.modeButtons);
    }

    @Override
    protected void renderWindow(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        final int x = this.guiLeft;
        final int y = this.guiTop;

        this.background.render(graphics, x, y);
        graphics.drawString(
                this.font, this.title,
                x + (this.background.getWidth() - this.font.width(this.title)) / 2, y + 4,
                0x54_1F_4F, false);

        GuiGameElement.of(this.cannon)
                .scale(4.0D)
                .at(x + this.background.getWidth(), y + this.background.getHeight() - 48, -200.0F)
                .render(graphics);
    }

    @Override
    public void removed() {
        CannonExpansionMode.set(this.cannon, this.selected);
        PacketDistributor.sendToServer(new CannonExpansionPayload(this.hand, this.selected));
    }

    private static AllIcons iconFor(final CannonExpansionMode mode) {
        return switch (mode) {
            case NONE -> AllIcons.I_DISABLE;
            case IMMEDIATE -> AllIcons.I_ACTIVE;
            case IMPACT -> AllIcons.I_TARGET;
        };
    }
}
