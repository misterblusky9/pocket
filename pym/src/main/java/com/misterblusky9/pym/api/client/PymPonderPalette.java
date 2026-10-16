package com.misterblusky9.pym.api.client;

import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.AABB;

public enum PymPonderPalette {
    CYAN(0x9AF0FF),
    YELLOW(0xFFD24A);

    private final Color color;

    PymPonderPalette(int rgb) {
        this.color = new Color(rgb, false).setImmutable();
    }

    public int getColor() {
        return color.getRGB();
    }

    public Color getColorObject() {
        return color;
    }

    public void chaseBoundingBoxOutline(SceneBuilder scene, Object slot, AABB bounds, int duration) {
        int rgb = getColor();
        scene.addInstruction(new TickingInstruction(false, duration) {
            @Override
            public void tick(PonderScene ponderScene) {
                super.tick(ponderScene);
                ponderScene.getOutliner().chaseAABB(slot, bounds)
                        .lineWidth(1 / 16f)
                        .colored(rgb);
            }
        });
    }
}
