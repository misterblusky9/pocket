package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

public final class PocketIcon extends AllIcons {
    private static final ResourceLocation ICON_ATLAS = ResourceLocation.fromNamespaceAndPath(
            PocketSized.MOD_ID,
            "textures/gui/pocket_icons.png"
    );

    private static final int ICON_SIZE = 16;
    private static final int ATLAS_WIDTH = 112;
    private static final int ATLAS_HEIGHT = 16;

    public static final PocketIcon GROW = cell(0);
    public static final PocketIcon SHRINK = cell(1);
    public static final PocketIcon SWITCH_IMPULSE = cell(2);
    public static final PocketIcon SWITCH_TOGGLE = cell(3);
    public static final PocketIcon TARGET_BOTH = cell(4);
    public static final PocketIcon TARGET_CONTRAPTIONS = cell(5);
    public static final PocketIcon TARGET_ENTITIES = cell(6);

    private final int u;
    private final int v;

    private PocketIcon(int u, int v) {
        super(0, 0);
        this.u = u;
        this.v = v;
    }

    private static PocketIcon cell(int index) {
        return new PocketIcon(index * ICON_SIZE, 0);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void bind() {
        RenderSystem.setShaderTexture(0, ICON_ATLAS);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(
                ICON_ATLAS,
                x,
                y,
                0,
                this.u,
                this.v,
                ICON_SIZE,
                ICON_SIZE,
                ATLAS_WIDTH,
                ATLAS_HEIGHT
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack pose, MultiBufferSource buffer, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.text(ICON_ATLAS));
        Matrix4f matrix = pose.last().pose();
        Color rgb = new Color(color);
        int light = LightTexture.FULL_BRIGHT;

        float u1 = this.u / (float) ATLAS_WIDTH;
        float u2 = (this.u + ICON_SIZE) / (float) ATLAS_WIDTH;
        float v1 = this.v / (float) ATLAS_HEIGHT;
        float v2 = (this.v + ICON_SIZE) / (float) ATLAS_HEIGHT;

        vertex(builder, matrix, new Vec3(0, 0, 0), rgb, u1, v1, light);
        vertex(builder, matrix, new Vec3(0, 1, 0), rgb, u1, v2, light);
        vertex(builder, matrix, new Vec3(1, 1, 0), rgb, u2, v2, light);
        vertex(builder, matrix, new Vec3(1, 0, 0), rgb, u2, v1, light);
    }

    @OnlyIn(Dist.CLIENT)
    private static void vertex(
            VertexConsumer builder,
            Matrix4f matrix,
            Vec3 position,
            Color color,
            float u,
            float v,
            int light
    ) {
        builder.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), 255)
                .setUv(u, v)
                .setLight(light);
    }
}
