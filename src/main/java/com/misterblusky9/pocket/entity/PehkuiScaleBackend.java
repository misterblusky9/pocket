package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PehkuiScaleBackend implements PehkuiScaleBridge.Backend {
    private static final float MODIFIER_PRIORITY = 1024.0F;
    private static final float PERSONAL_MODIFIER_PRIORITY = 1025.0F;

    private final Map<Entity, Factors> factors =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Entity, Boolean> personalEntities =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final ScaleModifier inheritedModifier;
    private final ScaleModifier containedModelModifier;
    private final ScaleModifier personalModifier;
    private final ScaleType personalScaleType;

    public PehkuiScaleBackend() {
        this.inheritedModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "sublevel_inherited"),
                new ScaleModifier(MODIFIER_PRIORITY) {
                    @Override
                    public float modifyScale(
                            final ScaleData scaleData,
                            final float modifiedScale,
                            final float delta
                    ) {
                        return modifiedScale * inheritedFactor(scaleData.getEntity(), delta);
                    }

                    @Override
                    public float modifyPrevScale(
                            final ScaleData scaleData,
                            final float modifiedScale
                    ) {
                        return modifiedScale * inheritedPreviousFactor(scaleData.getEntity());
                    }
                }
        );

        this.containedModelModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "sublevel_contained_model"),
                new ScaleModifier(MODIFIER_PRIORITY) {
                    @Override
                    public float modifyScale(
                            final ScaleData scaleData,
                            final float modifiedScale,
                            final float delta
                    ) {
                        return modifiedScale * containedModelFactor(scaleData.getEntity(), delta);
                    }

                    @Override
                    public float modifyPrevScale(
                            final ScaleData scaleData,
                            final float modifiedScale
                    ) {
                        return modifiedScale * containedModelPreviousFactor(scaleData.getEntity());
                    }
                }
        );

        this.personalModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "personal_scale_modifier"),
                new ScaleModifier(PERSONAL_MODIFIER_PRIORITY) {
                    @Override
                    public float modifyScale(
                            final ScaleData scaleData,
                            final float modifiedScale,
                            final float delta
                    ) {
                        return modifiedScale * personalFactor(scaleData.getEntity(), delta);
                    }

                    @Override
                    public float modifyPrevScale(
                            final ScaleData scaleData,
                            final float modifiedScale
                    ) {
                        return modifiedScale * personalPreviousFactor(scaleData.getEntity());
                    }
                }
        );

        this.personalScaleType = ScaleRegistries.register(
                ScaleRegistries.SCALE_TYPES,
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "personal_scale"),
                ScaleType.Builder.create()
                        .defaultBaseScale(1.0F)
                        .defaultPersistence(true)
                        .addDependentModifier(this.personalModifier)
                        .build()
        );
    }

    @Override
    public void apply(
            final Entity entity,
            final double inheritedBaseScale,
            final double containedModelScale
    ) {
        final float inherited = sanitize(inheritedBaseScale);
        final float model = sanitize(containedModelScale);

        final Factors previous = this.factors.get(entity);
        final float previousInherited = previous == null
                ? 1.0F
                : previous.inheritedCurrent();
        final float previousModel = previous == null
                ? 1.0F
                : previous.modelCurrent();

        final Factors next = new Factors(
                previousInherited,
                inherited,
                previousModel,
                model
        );

        if (isNeutral(inherited) && isNeutral(model)) {
            this.factors.remove(entity);
        } else {
            this.factors.put(entity, next);
        }

        final ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        final ScaleData modelWidth = ScaleTypes.MODEL_WIDTH.getScaleData(entity);
        final ScaleData modelHeight = ScaleTypes.MODEL_HEIGHT.getScaleData(entity);

        final boolean inheritedActive = !isNeutral(inherited);
        final boolean modelActive = !isNeutral(model);

        final boolean baseModifierChanged = setModifier(
                base,
                this.inheritedModifier,
                inheritedActive
        );
        final boolean widthModifierChanged = setModifier(
                modelWidth,
                this.containedModelModifier,
                modelActive
        );
        final boolean heightModifierChanged = setModifier(
                modelHeight,
                this.containedModelModifier,
                modelActive
        );

        if (!baseModifierChanged
                && Math.abs(inherited - previousInherited) > PocketSized.EPSILON) {
            base.onUpdate();
        }

        if (Math.abs(model - previousModel) > PocketSized.EPSILON) {
            if (!widthModifierChanged) modelWidth.onUpdate();
            if (!heightModifierChanged) modelHeight.onUpdate();
        }
    }

    @Override
    public void clear(final Entity entity) {
        this.factors.remove(entity);

        setModifier(
                ScaleTypes.BASE.getScaleData(entity),
                this.inheritedModifier,
                false
        );
        setModifier(
                ScaleTypes.MODEL_WIDTH.getScaleData(entity),
                this.containedModelModifier,
                false
        );
        setModifier(
                ScaleTypes.MODEL_HEIGHT.getScaleData(entity),
                this.containedModelModifier,
                false
        );
    }

    @Override
    public void setPersonalScale(final Entity entity, final double scale) {
        final float personal = sanitize(scale);
        if (isNeutral(personal)) {
            clearPersonalScale(entity);
            return;
        }

        this.personalScaleType.getScaleData(entity).setScale(personal);
        this.personalEntities.put(entity, Boolean.TRUE);
        setModifier(ScaleTypes.BASE.getScaleData(entity), this.personalModifier, true);
    }

    @Override
    public void clearPersonalScale(final Entity entity) {
        this.personalScaleType.getScaleData(entity).setScale(1.0F);
        this.personalEntities.remove(entity);
        setModifier(ScaleTypes.BASE.getScaleData(entity), this.personalModifier, false);
    }

    @Override
    public void disable() {
        final ArrayList<Entity> entities;

        synchronized (this.factors) {
            entities = new ArrayList<>(this.factors.keySet());
            this.factors.clear();
        }
        synchronized (this.personalEntities) {
            for (final Entity entity : this.personalEntities.keySet()) {
                if (!entities.contains(entity)) entities.add(entity);
            }
            this.personalEntities.clear();
        }

        for (final Entity entity : entities) {
            if (entity == null) continue;

            try {
                setModifier(
                        ScaleTypes.BASE.getScaleData(entity),
                        this.inheritedModifier,
                        false
                );
                setModifier(
                        ScaleTypes.BASE.getScaleData(entity),
                        this.personalModifier,
                        false
                );
                setModifier(
                        ScaleTypes.MODEL_WIDTH.getScaleData(entity),
                        this.containedModelModifier,
                        false
                );
                setModifier(
                        ScaleTypes.MODEL_HEIGHT.getScaleData(entity),
                        this.containedModelModifier,
                        false
                );
            } catch (final RuntimeException | LinkageError ignored) {
            }
        }
    }

    private float inheritedFactor(
            final Entity entity,
            final float delta
    ) {
        final Factors value = this.factors.get(entity);
        if (value == null) return 1.0F;
        return interpolate(value.inheritedPrevious(), value.inheritedCurrent(), delta);
    }

    private float inheritedPreviousFactor(final Entity entity) {
        final Factors value = this.factors.get(entity);
        return value == null ? 1.0F : value.inheritedPrevious();
    }

    private float containedModelFactor(
            final Entity entity,
            final float delta
    ) {
        final Factors value = this.factors.get(entity);
        if (value == null) return 1.0F;
        return interpolate(value.modelPrevious(), value.modelCurrent(), delta);
    }

    private float containedModelPreviousFactor(final Entity entity) {
        final Factors value = this.factors.get(entity);
        return value == null ? 1.0F : value.modelPrevious();
    }

    private float personalFactor(final Entity entity, final float delta) {
        if (entity == null) return 1.0F;
        return sanitize(this.personalScaleType.getScaleData(entity).getBaseScale(delta));
    }

    private float personalPreviousFactor(final Entity entity) {
        if (entity == null) return 1.0F;
        return sanitize(this.personalScaleType.getScaleData(entity).getPrevBaseScale());
    }

    private static boolean setModifier(
            final ScaleData data,
            final ScaleModifier modifier,
            final boolean present
    ) {
        if (present) {
            return data.getBaseValueModifiers().add(modifier);
        }

        return data.getBaseValueModifiers().remove(modifier);
    }

    private static float interpolate(
            final float previous,
            final float current,
            final float delta
    ) {
        if (!Float.isFinite(delta)) return current;
        return Mth.lerp(Mth.clamp(delta, 0.0F, 1.0F), previous, current);
    }

    private static float sanitize(final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0F;
        return (float) scale;
    }

    private static boolean isNeutral(final float scale) {
        return Math.abs(scale - 1.0F) <= PocketSized.EPSILON;
    }

    private record Factors(
            float inheritedPrevious,
            float inheritedCurrent,
            float modelPrevious,
            float modelCurrent
    ) {
    }
}
