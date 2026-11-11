package nl.hauntedmc.dungeons.command;

import java.util.List;
import org.bukkit.command.CommandSender;

interface CommandHandler {
    /** Handles a branch-specific command invocation. */
    boolean handleCommand(CommandSender sender, String[] args);

    /** Produces branch-specific tab completions. */
    List<String> handleTabComplete(CommandSender sender, String[] args);
}
