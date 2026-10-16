package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.EntityScaling;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.ponder.PonderSectionScaleAccess;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PymPonder {
    public static void scaleSection(
            SceneBuilder scene,
            ElementLink<WorldSectionElement> section,
            Vec3 pivot,
            double scale,
            int ticks
    ) {
        Objects.requireNonNull(scene);
        Objects.requireNonNull(section);
        Objects.requireNonNull(pivot);
        validate(scale, ticks);
        if (scale > Float.MAX_VALUE || scale < Float.MIN_VALUE) {
            throw new IllegalArgumentException("Section scale is not representable as a float");
        }

        scene.addInstruction(new TickingInstruction(false, ticks) {
            private PonderSectionScaleAccess target;
            private double initial;

            @Override
            protected void firstTick(PonderScene context) {
                WorldSectionElement element = context.resolve(section);
                if (!(element instanceof PonderSectionScaleAccess access)) return;
                target = access;
                initial = access.pym$scale();
                access.pym$setPivot(pivot);
            }

            @Override
            public void tick(PonderScene context) {
                super.tick(context);
                if (target == null) return;
                double progress = ticks == 0 ? 1.0D : (ticks - remainingTicks) / (double) ticks;
                double current = initial + (scale - initial) * progress;
                target.pym$setScale(current, ticks == 0);
            }

            @Override
            public void reset(PonderScene context) {
                super.reset(context);
                target = null;
                initial = 1.0D;
            }
        });
    }

    public static void scaleEntity(
            SceneBuilder scene,
            ElementLink<EntityElement> entity,
            double scale,
            int ticks
    ) {
        Objects.requireNonNull(scene);
        Objects.requireNonNull(entity);
        validate(scale, ticks);
        scene.world().modifyEntity(entity, target -> Pym.entities().set(target, scale, ticks));
    }

    public static <T extends Entity> void scaleEntities(
            SceneBuilder scene,
            Class<T> type,
            double scale,
            int ticks
    ) {
        Objects.requireNonNull(scene);
        Objects.requireNonNull(type);
        validate(scale, ticks);
        scene.world().modifyEntities(type, target -> Pym.entities().set(target, scale, ticks));
    }

    public static <T extends Entity> void scaleEntitiesInside(
            SceneBuilder scene,
            Class<T> type,
            Selection area,
            double scale,
            int ticks
    ) {
        Objects.requireNonNull(scene);
        Objects.requireNonNull(type);
        Objects.requireNonNull(area);
        validate(scale, ticks);
        scene.world().modifyEntitiesInside(type, area, target -> Pym.entities().set(target, scale, ticks));
    }

    public static ScaleGroup group(SceneBuilder scene, Vec3 pivot) {
        return new ScaleGroup(scene, pivot);
    }

    public static final class ScaleGroup {
        private final SceneBuilder scene;
        private final Vec3 pivot;
        private final List<ElementLink<WorldSectionElement>> sections = new ArrayList<>();
        private final List<ElementLink<EntityElement>> entities = new ArrayList<>();
        private final List<EntityArea> areas = new ArrayList<>();
        private final Set<Entity> areaMembers = Collections.newSetFromMap(new IdentityHashMap<>());
        private double factor = 1.0D;

        private ScaleGroup(SceneBuilder scene, Vec3 pivot) {
            this.scene = Objects.requireNonNull(scene);
            this.pivot = Objects.requireNonNull(pivot);
            if (!Double.isFinite(pivot.x) || !Double.isFinite(pivot.y) || !Double.isFinite(pivot.z)) {
                throw new IllegalArgumentException("Invalid Ponder group pivot");
            }
        }

        public ScaleGroup section(ElementLink<WorldSectionElement> section) {
            sections.add(Objects.requireNonNull(section));
            return this;
        }

        public ScaleGroup entity(ElementLink<EntityElement> entity) {
            entities.add(Objects.requireNonNull(entity));
            return this;
        }

        public <T extends Entity> ScaleGroup entitiesInside(Class<T> type, Selection area) {
            areas.add(new EntityArea(Objects.requireNonNull(type), Objects.requireNonNull(area)));
            return this;
        }

        public ScaleGroup scaleTo(double target, int ticks) {
            validate(target, ticks);
            if (target > Float.MAX_VALUE || target < Float.MIN_VALUE) {
                throw new IllegalArgumentException("Ponder group scale is not representable as a float");
            }
            scene.addInstruction(new GroupScaleInstruction(
                    this, List.copyOf(sections), List.copyOf(entities), List.copyOf(areas), target, ticks));
            return this;
        }
    }

    private record EntityArea(Class<? extends Entity> type, Selection area) {}

    private record SectionOrigin(PonderSectionScaleAccess section, double scale) {}

    private record EntityOrigin(Entity entity, Vec3 position, double scale) {}

    private static final class GroupScaleInstruction extends TickingInstruction {
        private final ScaleGroup group;
        private final List<ElementLink<WorldSectionElement>> sections;
        private final List<ElementLink<EntityElement>> entities;
        private final List<EntityArea> areas;
        private final double target;
        private final List<SectionOrigin> sectionOrigins = new ArrayList<>();
        private final List<EntityOrigin> entityOrigins = new ArrayList<>();
        private double start;

        private GroupScaleInstruction(
                ScaleGroup group,
                List<ElementLink<WorldSectionElement>> sections,
                List<ElementLink<EntityElement>> entities,
                List<EntityArea> areas,
                double target,
                int ticks
        ) {
            super(false, ticks);
            this.group = group;
            this.sections = sections;
            this.entities = entities;
            this.areas = areas;
            this.target = target;
        }

        @Override
        protected void firstTick(PonderScene context) {
            start = group.factor;
            sectionOrigins.clear();
            entityOrigins.clear();

            for (ElementLink<WorldSectionElement> link : sections) {
                WorldSectionElement element = context.resolve(link);
                if (element instanceof PonderSectionScaleAccess access) {
                    double initial = access.pym$scale();
                    requireFloatScale(initial * target / start);
                    access.pym$setPivot(group.pivot);
                    sectionOrigins.add(new SectionOrigin(access, initial));
                }
            }

            Set<Entity> members = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ElementLink<EntityElement> link : entities) {
                EntityElement element = context.resolve(link);
                if (element != null) element.ifPresent(members::add);
            }
            for (EntityArea area : areas) {
                context.forEachWorldEntity(Entity.class, entity -> {
                    if (area.type().isInstance(entity) && area.area().test(entity.blockPosition())) {
                        group.areaMembers.add(entity);
                    }
                });
            }
            group.areaMembers.removeIf(Entity::isRemoved);
            members.addAll(group.areaMembers);

            for (Entity entity : members) {
                if (!entity.isAlive() || !Pym.entities().supports(entity)) continue;
                double initial = Pym.entities().scaleOf(entity);
                double goal = initial * target / start;
                if (ScaleBounds.isValid(goal)) {
                    entityOrigins.add(new EntityOrigin(entity, entity.position(), initial));
                }
            }
        }

        @Override
        public void tick(PonderScene context) {
            super.tick(context);
            double progress = totalTicks <= 1
                    ? 1.0D
                    : (totalTicks - 1 - remainingTicks) / (double) (totalTicks - 1);
            double factor = start + (target - start) * progress;
            double ratio = factor / start;
            group.factor = factor;

            for (SectionOrigin origin : sectionOrigins) {
                origin.section().pym$setScale(origin.scale() * ratio, totalTicks <= 1);
            }
            for (EntityOrigin origin : entityOrigins) {
                Entity entity = origin.entity();
                if (entity.isRemoved()) continue;
                double scale = origin.scale() * ratio;
                int ticks = totalTicks > 1 && Pym.entities().backend(entity) == EntityScaling.Backend.PEHKUI ? 1 : 0;
                if (!Pym.entities().set(entity, scale, ticks)) continue;
                Vec3 position = group.pivot.add(origin.position().subtract(group.pivot).scale(ratio));
                entity.setPos(position.x, position.y, position.z);
            }
        }

        @Override
        public void reset(PonderScene context) {
            super.reset(context);
            group.factor = 1.0D;
            group.areaMembers.clear();
            sectionOrigins.clear();
            entityOrigins.clear();
            start = 1.0D;
        }
    }

    private static void requireFloatScale(double scale) {
        if (!ScaleBounds.isValid(scale) || scale < Float.MIN_VALUE || scale > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Ponder section scale is not representable as a float");
        }
    }

    private static void validate(double scale, int ticks) {
        if (!ScaleBounds.isValid(scale) || ticks < 0) {
            throw new IllegalArgumentException("Invalid Ponder scale or duration");
        }
    }

    private PymPonder() {}
}
