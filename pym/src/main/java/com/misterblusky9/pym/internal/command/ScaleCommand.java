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
import java.util.function.DoubleBinaryOperator;

@EventBusSubscriber(modid = PymMod.MOD_ID)
public final class ScaleCommand {
    private static final double PICK_RANGE = 64.0D;

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pym")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("scale")
                                .then(subLevelOperation("set", (current, factor) -> factor))
                                .then(subLevelOperation("add", (current, factor) -> current + factor))
                                .then(subLevelOperation("subtract", (current, factor) -> current - factor))
                                .then(subLevelOperation("multiply", (current, factor) -> current * factor))
                                .then(subLevelOperation("divide", (current, factor) -> current / factor))
                                .then(entityScaleBranch()))
                        .then(Commands.literal("info")
                                .executes(ScaleCommand::subLevelInfo)
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ScaleCommand::entityInfo))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on").executes(context -> debug(context, true)))
                                .then(Commands.literal("off").executes(context -> debug(context, false))))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> subLevelOperation(
            final String name,
            final DoubleBinaryOperator apply
    ) {
        return Commands.literal(name)
                .then(Commands.argument("scaleFactor", DoubleArgumentType.doubleArg())
                        .executes(context -> runSubLevel(context, apply, -1))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> runSubLevel(context, apply, ticks(context)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entityScaleBranch() {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("entity");
        final ArgumentBuilder<CommandSourceStack, ?> targets = Commands.argument("targets", EntityArgument.entities())
                .then(entityOperation("set", (current, factor) -> factor))
                .then(entityOperation("add", (current, factor) -> current + factor))
                .then(entityOperation("subtract", (current, factor) -> current - factor))
                .then(entityOperation("multiply", (current, factor) -> current * factor))
                .then(entityOperation("divide", (current, factor) -> current / factor));
        return root.then(targets);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entityOperation(
            final String name,
            final DoubleBinaryOperator apply
    ) {
        return Commands.literal(name)
                .then(Commands.argument("scaleFactor", DoubleArgumentType.doubleArg())
                        .executes(context -> runEntities(context, apply, -1))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> runEntities(context, apply, ticks(context)))));
    }

    private static int ticks(final CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "ticks");
    }

    private static int runSubLevel(
            final CommandContext<CommandSourceStack> context,
            final DoubleBinaryOperator apply,
            final int ticks
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        final double factor = DoubleArgumentType.getDouble(context, "scaleFactor");

        final ServerSubLevel subLevel = targetSubLevel(player);
        if (subLevel == null) {
            source.sendFailure(Component.literal("Look at or stand on a sublevel."));
            return 0;
        }

        final double current = Pym.scale().target(subLevel);
        final double goal = apply.applyAsDouble(current, factor);
        final ResizeRequest request = Pym.resize().request(subLevel).scaleTo(goal).ticks(ticks < 0 ? Double.NaN : ticks);
        final ResizeResult result = request.submit();
        if (!result.accepted()) {
            source.sendFailure(Component.literal("Resize refused: " + result.describe()));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Scaling sublevel " + ScaleFormat.label(current) + " -> "
                        + ScaleFormat.label(result.scale()) + (ticks < 0 ? "" : " over " + ticks + " ticks")),
                true);
        return 1;
    }

    private static int runEntities(
            final CommandContext<CommandSourceStack> context,
            final DoubleBinaryOperator apply,
            final int requestedTicks
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        final double operand = DoubleArgumentType.getDouble(context, "scaleFactor");
        final int duration = requestedTicks < 0
                ? Math.max(0, (int) Math.round(Pym.resize().defaultTransitionTicks()))
                : requestedTicks;

        int changed = 0;
        int unsupported = 0;
        int invalid = 0;
        for (final Entity entity : targets) {
            if (!Pym.entities().supports(entity)) {
                unsupported++;
                continue;
            }
            final double current = Pym.entities().target(entity);
            final double goal = apply.applyAsDouble(current, operand);
            if (!ScaleBounds.isValid(goal)) {
                invalid++;
                continue;
            }
            if (Pym.entities().set(entity, goal, duration)) changed++;
            else unsupported++;
        }

        final int successCount = changed;
        if (changed > 0) {
            source.sendSuccess(() -> Component.literal(
                    "Scaled " + successCount + " entit" + (successCount == 1 ? "y" : "ies")
                            + " through Pym" + (duration == 0 ? " immediately" : " over " + duration + " ticks")), true);
        }
        if (unsupported > 0) {
            final int count = unsupported;
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

    private static int subLevelInfo(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerSubLevel subLevel = targetSubLevel(source.getPlayerOrException());
        if (subLevel == null) {
            source.sendFailure(Component.literal("Look at or stand on a sublevel."));
            return 0;
        }
        final PlotContents contents = Pym.resize().contents(subLevel);
        final int coupled = Pym.connections().coupled(subLevel).size() - 1;
        source.sendSuccess(() -> Component.literal(
                "Scale " + ScaleFormat.label(Pym.scale().of(subLevel))
                        + ", settled " + ScaleFormat.label(Pym.scale().settled(subLevel))
                        + ", target " + ScaleFormat.label(Pym.scale().target(subLevel))
                        + "\n" + contents.blocks() + " blocks, " + contents.blockEntities() + " block entities"
                        + (contents.hasNoShrinkBlock() ? ", holds no-shrink block " + contents.noShrink() : "")
                        + "\nCoupled to " + Math.max(0, coupled) + " other sublevel(s)"
                        + (Pym.connections().isJoinedToAnother(subLevel) ? ", joined to another" : "")), false);
        return 1;
    }

    private static int entityInfo(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final Entity entity = EntityArgument.getEntity(context, "target");
        final var backend = Pym.entities().backend(entity);
        final StringBuilder text = new StringBuilder()
                .append(entity.getName().getString())
                .append("\nPym backend: ").append(backend)
                .append("\nScale: ").append(ScaleFormat.label(Pym.entities().scaleOf(entity)))
                .append(", target ").append(ScaleFormat.label(Pym.entities().target(entity)));
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int debug(final CommandContext<CommandSourceStack> context, final boolean on) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        PymTrace.setEnabled(on);
        PacketDistributor.sendToPlayer(player, new DebugPayload(on));
        context.getSource().sendSuccess(() -> Component.literal(
                on ? "Pym debug on: collider outlines for you, scale logging for the server"
                   : "Pym debug off"), true);
        return 1;
    }

    private static ServerSubLevel targetSubLevel(final ServerPlayer player) {
        final HitResult hit = player.pick(PICK_RANGE, 1.0F, false);
        if (hit.getType() != HitResult.Type.MISS) {
            final ServerSubLevel looked = live(Sable.HELPER.getContaining(player.level(), hit.getLocation()));
            if (looked != null) return looked;
        }

        return live(Sable.HELPER.getTrackingSubLevel(player));
    }

    private static ServerSubLevel live(final SubLevel subLevel) {
        return subLevel instanceof final ServerSubLevel server && !server.isRemoved() ? server : null;
    }

    private ScaleCommand() {
    }
}
