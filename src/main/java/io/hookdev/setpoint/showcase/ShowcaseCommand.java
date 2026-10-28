package io.hookdev.setpoint.showcase;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /setpoint showcase}: builds the example plant next to you, for creative players and operators. It clears a
 * space of 34 by 34 blocks (and 45 high) east and south of where you stand, so use it on open ground, best in a
 * superflat world.
 */
public final class ShowcaseCommand {
    private ShowcaseCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("setpoint")
                .then(Commands.literal("showcase")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> warn(context.getSource()))
                        .then(Commands.literal("confirm").executes(context -> build(context.getSource())))));
    }

    /** First a warning: it clears a big space round you. */
    private static int warn(CommandSourceStack source) {
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(2, 0, 2);
        BlockPos far = origin.offset(Showcase.WIDTH, Showcase.HEIGHT - 1, Showcase.FULL_DEPTH);
        source.sendSuccess(() -> Component.literal("This builds the Setpoint showcase plant and clears everything from " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                + " to " + far.getX() + " " + far.getY() + " " + far.getZ() + " (east and south of you). Best on open ground or in a superflat world. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Type /setpoint showcase confirm to build it.").withStyle(ChatFormatting.WHITE)), false);
        return 1;
    }

    private static int build(CommandSourceStack source) {
        if (!io.hookdev.setpoint.SetpointConfig.showcaseAllowed()) {
            source.sendFailure(Component.literal("The showcase is switched off on this server (showcase.allowCommand in the Setpoint config)."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(2, 0, 2);
        Showcase.build(level, origin);
        Showcase.ignite(level, origin);
        BlockPos room = origin.offset(10, 0, 30);
        source.sendSuccess(() -> Component.literal("Setpoint showcase plant built. ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("The control room is at " + room.getX() + " " + room.getY() + " " + room.getZ()
                        + ": the console desk, the P&ID wall and the video wall. Give it a minute to come up to pressure.").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }
}
