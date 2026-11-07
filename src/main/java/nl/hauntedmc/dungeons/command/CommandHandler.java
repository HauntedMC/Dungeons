package nl.hauntedmc.dungeons.command;

import org.bukkit.command.CommandSender;

import java.util.List;

interface CommandHandler {
    /** Handles a branch-specific command invocation. */
    boolean handleCommand(CommandSender sender, String[] args);

    /** Produces branch-specific tab completions. */
    List<String> handleTabComplete(CommandSender sender, String[] args);
}
