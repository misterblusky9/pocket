package com.misterblusky9.pym.internal.command;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeRequest;
import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.internal.PymMod;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.network.DebugPayload;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleBinaryOperator;

@EventBusSubscriber(modid = PymMod.MOD_ID)
public final class ScaleCommand {
    private static final double PICK_RANGE = 64.0D;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pym")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("scale")
                                .then(subLevelOperation("set", (current, factor) -> factor))
                                .then(subLevelOperation("add", (current, factor) -> current + factor))
                                .then(subLevelOperation("subtract", (current, factor) -> current - factor))
                                .then(subLevelOperation("multiply", (current, factor) -> current * factor))
                                .then(subLevelOperation("divide", (current, factor) -> current / factor))
                                .then(subLevelScaleBranch())
                                .then(entityScaleBranch()))
                        .then(Commands.literal("info")
                                .executes(ScaleCommand::subLevelInfo)
                                .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                                        .executes(ScaleCommand::selectedSubLevelInfo))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(ScaleCommand::entityInfo))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on").executes(context -> debug(context, true)))
                                .then(Commands.literal("off").executes(context -> debug(context, false))))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> subLevelScaleBranch() {
        return Commands.literal("sublevel")
                .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                        .then(subLevelOperation("set", (current, factor) -> factor, true))
                        .then(subLevelOperation("add", (current, factor) -> current + factor, true))
                        .then(subLevelOperation("subtract", (current, factor) -> current - factor, true))
                        .then(subLevelOperation("multiply", (current, factor) -> current * factor, true))
                        .then(subLevelOperation("divide", (current, factor) -> current / factor, true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> subLevelOperation(
            String name,
            DoubleBinaryOperator apply
    ) {
        return subLevelOperation(name, apply, false);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> subLevelOperation(
            String name,
            DoubleBinaryOperator apply,
            boolean selected
    ) {
        return Commands.literal(name)
                .then(Commands.argument("scaleFactor", DoubleArgumentType.doubleArg())
                        .executes(context -> runSubLevels(context, apply, -1, selected))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> runSubLevels(context, apply, ticks(context), selected))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entityScaleBranch() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("entity");
        ArgumentBuilder<CommandSourceStack, ?> targets = Commands.argument("targets", EntityArgument.entities())
                .then(entityOperation("set", (current, factor) -> factor))
                .then(entityOperation("add", (current, factor) -> current + factor))
                .then(entityOperation("subtract", (current, factor) -> current - factor))
                .then(entityOperation("multiply", (current, factor) -> current * factor))
                .then(entityOperation("divide", (current, factor) -> current / factor));
        return root.then(targets);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entityOperation(
            String name,
            DoubleBinaryOperator apply
    ) {
        return Commands.literal(name)
                .then(Commands.argument("scaleFactor", DoubleArgumentType.doubleArg())
                        .executes(context -> runEntities(context, apply, -1))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> runEntities(context, apply, ticks(context)))));
    }

    private static int ticks(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "ticks");
    }

    private static int runSubLevels(
            CommandContext<CommandSourceStack> context,
            DoubleBinaryOperator apply,
            int ticks,
            boolean selected
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerSubLevel> targets;
        if (selected) {
            targets = SubLevelArgumentType.getSubLevels(context, "targets");
        } else {
            ServerSubLevel target = targetSubLevel(source.getPlayerOrException());
            targets = target == null ? List.of() : List.of(target);
        }

        List<ServerSubLevel> subLevels = targets.stream()
                .filter(subLevel -> subLevel != null && !subLevel.isRemoved())
                .sorted(Comparator.comparing(subLevel -> subLevel.getUniqueId().toString()))
                .toList();
        if (subLevels.isEmpty()) {
            source.sendFailure(Component.literal(selected ? "No sublevels found." : "Look at or stand on a sublevel."));
            return 0;
        }

        double factor = DoubleArgumentType.getDouble(context, "scaleFactor");
        Map<UUID, Double> originalTargets = new HashMap<>();
        for (ServerSubLevel subLevel : subLevels) {
            originalTargets.putIfAbsent(subLevel.getUniqueId(), Pym.scale().target(subLevel));
        }

        int accepted = 0;
        int refused = 0;
        int skipped = 0;
        String firstRefusal = null;
        for (ServerSubLevel subLevel : subLevels) {
            UUID id = subLevel.getUniqueId();
            if (!ScaleBounds.same(Pym.scale().target(subLevel), originalTargets.get(id))) {
                skipped++;
                continue;
            }
            double current = Pym.scale().target(subLevel);
            double goal = apply.applyAsDouble(current, factor);
            ResizeRequest request = Pym.resize().request(subLevel)
                    .scaleTo(goal).ticks(ticks < 0 ? Double.NaN : ticks);
            ResizeResult result = request.submit();
            if (!result.accepted()) {
                refused++;
                if (firstRefusal == null) firstRefusal = result.describe();
                continue;
            }
            accepted++;
            if (subLevels.size() == 1) {
                source.sendSuccess(() -> Component.literal("Scaling sublevel " + ScaleFormat.label(current) + " -> "
                        + ScaleFormat.label(result.scale()) + (ticks < 0 ? "" : " over " + ticks + " ticks")), true);
            }
        }

        if (subLevels.size() > 1 && accepted > 0) {
            int count = accepted;
            int alreadyAffected = skipped;
            source.sendSuccess(() -> Component.literal("Scaling " + count + " sublevel" + (count == 1 ? "" : "s")
                    + (ticks < 0 ? "" : " over " + ticks + " ticks")
                    + (alreadyAffected == 0 ? "" : "; " + alreadyAffected + " already affected")), true);
        }
        if (refused > 0) {
            source.sendFailure(Component.literal("Resize refused for " + refused + " sublevel" + (refused == 1 ? "" : "s")
                    + ": " + firstRefusal));
        }
        return accepted;
    }

    private static int runEntities(
            CommandContext<CommandSourceStack> context,
            DoubleBinaryOperator apply,
            int requestedTicks
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        double operand = DoubleArgumentType.getDouble(context, "scaleFactor");
        int duration = requestedTicks < 0
                ? Math.max(0, (int) Math.round(Pym.resize().defaultTransitionTicks()))
                : requestedTicks;

        int changed = 0;
        int unsupported = 0;
        int invalid = 0;
        for (Entity entity : targets) {
            if (!Pym.entities().supports(entity)) {
                unsupported++;
                continue;
            }
            double current = Pym.entities().target(entity);
            double goal = apply.applyAsDouble(current, operand);
            if (!ScaleBounds.isValid(goal)) {
                invalid++;
                continue;
            }
            if (Pym.entities().set(entity, goal, duration)) changed++;
            else unsupported++;
        }

        int successCount = changed;
        if (changed > 0) {
            source.sendSuccess(() -> Component.literal(
                    "Scaled " + successCount + " entit" + (successCount == 1 ? "y" : "ies")
                            + " through Pym" + (duration == 0 ? " immediately" : " over " + duration + " ticks")), true);
        }
        if (unsupported > 0) {
            int count = unsupported;
            source.sendFailure(Component.literal(count + " target(s) cannot be scaled by this Pym installation. "
                    + (Pym.entities().pehkuiPresent()
                    ? "Pehkui is present but Pym's Pehkui backend is unavailable."
                    : "Install Pehkui for general entity/player scaling; without it Pym only scales static furniture entities.")));
        }
        if (invalid > 0) {
            source.sendFailure(Component.literal(invalid + " target(s) produced a non-positive or non-finite scale."));
        }
        return changed;
    }

    private static int subLevelInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerSubLevel subLevel = targetSubLevel(source.getPlayerOrException());
        if (subLevel == null) {
            source.sendFailure(Component.literal("Look at or stand on a sublevel."));
            return 0;
        }
        return sendSubLevelInfo(source, List.of(subLevel), false);
    }

    private static int selectedSubLevelInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sendSubLevelInfo(context.getSource(), SubLevelArgumentType.getSubLevels(context, "targets"), true);
    }

    private static int sendSubLevelInfo(
            CommandSourceStack source,
            Collection<ServerSubLevel> targets,
            boolean named
    ) {
        int count = 0;
        for (ServerSubLevel subLevel : targets) {
            if (subLevel == null || subLevel.isRemoved()) continue;
            PlotContents contents = Pym.resize().contents(subLevel);
            int coupled = Pym.connections().coupled(subLevel).size() - 1;
            String prefix = named
                    ? "Sublevel " + (subLevel.getName() == null ? subLevel.getUniqueId() : subLevel.getName()) + "\n"
                    : "";
            source.sendSuccess(() -> Component.literal(
                    prefix + "Scale " + ScaleFormat.label(Pym.scale().of(subLevel))
                            + ", settled " + ScaleFormat.label(Pym.scale().settled(subLevel))
                            + ", target " + ScaleFormat.label(Pym.scale().target(subLevel))
                            + "\n" + contents.blocks() + " blocks, " + contents.blockEntities() + " block entities"
                            + (contents.hasNoShrinkBlock() ? ", holds no-shrink block " + contents.noShrink() : "")
                            + "\nCoupled to " + Math.max(0, coupled) + " other sublevel(s)"
                            + (Pym.connections().isJoinedToAnother(subLevel) ? ", joined to another" : "")), false);
            count++;
        }
        if (count == 0) source.sendFailure(Component.literal("No sublevels found."));
        return count;
    }

    private static int entityInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            var backend = Pym.entities().backend(entity);
            StringBuilder text = new StringBuilder()
                    .append(entity.getName().getString())
                    .append("\nPym backend: ").append(backend)
                    .append("\nScale: ").append(ScaleFormat.label(Pym.entities().scaleOf(entity)))
                    .append(", target ").append(ScaleFormat.label(Pym.entities().target(entity)));
            source.sendSuccess(() -> Component.literal(text.toString()), false);
            count++;
        }
        return count;
    }

    private static int debug(CommandContext<CommandSourceStack> context, boolean on) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PymTrace.setEnabled(on);
        PacketDistributor.sendToPlayer(player, new DebugPayload(on));
        context.getSource().sendSuccess(() -> Component.literal(
                on ? "Pym debug on: collider outlines for you, scale logging for the server"
                   : "Pym debug off"), true);
        return 1;
    }

    private static ServerSubLevel targetSubLevel(ServerPlayer player) {
        HitResult hit = player.pick(PICK_RANGE, 1.0F, false);
        if (hit.getType() != HitResult.Type.MISS) {
            ServerSubLevel looked = live(Sable.HELPER.getContaining(player.level(), hit.getLocation()));
            if (looked != null) return looked;
        }

        return live(Sable.HELPER.getTrackingSubLevel(player));
    }

    private static ServerSubLevel live(SubLevel subLevel) {
        return subLevel instanceof ServerSubLevel server && !server.isRemoved() ? server : null;
    }

    private ScaleCommand() {
    }
}
